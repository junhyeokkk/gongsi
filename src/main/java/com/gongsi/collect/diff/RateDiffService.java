package com.gongsi.collect.diff;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongsi.collect.diff.ChangeEventPublisher.ProductEvent;
import com.gongsi.collect.diff.ProductIdRepository.Ids;
import com.gongsi.collect.fetch.RawSnapshotRepository;
import com.gongsi.collect.fetch.RawSnapshotRepository.RawPage;
import com.gongsi.collect.normalize.NormalizeService;
import com.gongsi.common.GongsiException;
import com.gongsi.common.ProductType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

// DiffStep 이 호출 - targetDate 원문을 권역별 직전 수집일 원문과 비교해 rate_history·change_event 적재 (ADR-0003)
@Service
public class RateDiffService {

    private static final Logger log = LoggerFactory.getLogger(RateDiffService.class);

    // 상품군 상품의 10% 넘게(최소 5개) 하루에 사라지거나 생기면 급변 - 실측 일 변동은 0~1개
    private static final int MASS_CHANGE_MIN = 5;
    private static final double MASS_CHANGE_RATIO = 0.1;

    private final RawSnapshotRepository rawRepo;
    private final RawRateReader reader;
    private final ProductIdRepository idRepo;
    private final RateHistoryRepository historyRepo;
    private final ChangeEventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public RateDiffService(RawSnapshotRepository rawRepo, RawRateReader reader, ProductIdRepository idRepo,
                           RateHistoryRepository historyRepo, ChangeEventPublisher publisher,
                           ObjectMapper objectMapper, TransactionTemplate tx) {
        this.rawRepo = rawRepo;
        this.reader = reader;
        this.idRepo = idRepo;
        this.historyRepo = historyRepo;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.tx = tx;
    }

    // 전 상품군을 한 트랜잭션으로 - 이력과 이벤트가 어긋나게 남지 않는다
    public List<DiffSummary> detectChanges(LocalDate targetDate) {
        return tx.execute(status -> NormalizeService.TYPES.stream().map(type -> detect(type, targetDate)).toList());
    }

    DiffSummary detect(ProductType type, LocalDate targetDate) {
        List<RawPage> todayPages = rawRepo.findByDate(targetDate, type);
        if (todayPages.isEmpty()) {
            throw new GongsiException("raw_snapshot 없음 fetchedOn=%s type=%s".formatted(targetDate, type));
        }
        // 직전 원문이 없는 권역은 처음 수집한 날이라 비교하지 않는다 - 전부 NEW 로 쏟아지지 않게
        Map<String, LocalDate> prevDates = rawRepo.latestDatesBefore(type, targetDate);
        Set<String> baselineGroups = todayPages.stream().map(RawPage::finGrpNo)
                .filter(g -> !prevDates.containsKey(g)).collect(Collectors.toCollection(TreeSet::new));
        List<RawPage> today = todayPages.stream().filter(p -> prevDates.containsKey(p.finGrpNo())).toList();
        Set<String> groups = today.stream().map(RawPage::finGrpNo).collect(Collectors.toSet());

        RawRates prev = reader.read(type, prevPages(type, prevDates, groups));
        RawRates now = reader.read(type, today);
        DiffResult result = RateDiffCalculator.compare(prev.products(), now.products(), now.quarantined());
        checkNotPartial(type, targetDate, result, prev.products().size(), now.products().size());
        Ids ids = idRepo.load(type);
        Unresolved unresolved = new Unresolved();
        List<RateHistoryRepository.Row> rows = toRows(result.rateChanges(), ids, unresolved);
        List<ProductEvent> events = toEvents(result.events(), ids, unresolved);
        int rateInserted = historyRepo.insertAll(targetDate, rows);
        int eventInserted = publisher.publishProductEvents(targetDate, events);

        Map<ChangeEventType, Long> detected = result.events().stream()
                .collect(Collectors.groupingBy(DetectedEvent::type, TreeMap::new, Collectors.counting()));
        log.info("diff {} targetDate={} prev={} baseline={} rateChanges={} events={} inserted={}/{} unresolved={}",
                type, targetDate, new TreeMap<>(prevDates), baselineGroups, result.rateChanges().size(), detected,
                rateInserted, eventInserted, unresolved.count);
        return new DiffSummary(type, detected, rateInserted, eventInserted, unresolved.count);
    }

    // 상품이 한꺼번에 사라지거나 생기면 어느 한쪽 원문이 페이지 일부만 받은 것으로 보고 멈춘다 - 거짓 알림 방지
    private static void checkNotPartial(ProductType type, LocalDate targetDate, DiffResult result,
                                        int prevProducts, int todayProducts) {
        long closed = productEvents(result, ChangeEventType.CLOSED);
        long added = productEvents(result, ChangeEventType.NEW);
        if (exceeds(closed, prevProducts) || exceeds(added, todayProducts)) {
            throw new GongsiException("상품 급변 - 원문 누락 의심 type=%s targetDate=%s closed=%d/%d new=%d/%d"
                    .formatted(type, targetDate, closed, prevProducts, added, todayProducts));
        }
    }

    private static long productEvents(DiffResult result, ChangeEventType type) {
        return result.events().stream().filter(e -> e.optionKey() == null && e.type() == type).count();
    }

    private static boolean exceeds(long changed, int total) {
        return changed > Math.max(MASS_CHANGE_MIN, total * MASS_CHANGE_RATIO);
    }

    // 권역마다 직전 날짜가 다를 수 있어 날짜별로 한 번씩 읽고 그 권역 페이지만 남긴다
    private List<RawPage> prevPages(ProductType type, Map<String, LocalDate> prevDates, Set<String> groups) {
        Map<LocalDate, List<String>> byDate = groups.stream()
                .collect(Collectors.groupingBy(prevDates::get, TreeMap::new, Collectors.toList()));
        List<RawPage> pages = new ArrayList<>();
        byDate.forEach((date, grps) -> rawRepo.findByDate(date, type).stream()
                .filter(p -> grps.contains(p.finGrpNo())).forEach(pages::add));
        return pages;
    }

    private static List<RateHistoryRepository.Row> toRows(List<RateChange> changes, Ids ids, Unresolved unresolved) {
        List<RateHistoryRepository.Row> rows = new ArrayList<>();
        for (RateChange c : changes) {
            Long optionId = ids.option(c.product(), c.optionKey());
            if (optionId == null) {
                unresolved.add(c.product() + "|" + c.optionKey());
                continue;
            }
            rows.add(new RateHistoryRepository.Row(optionId, c.field(), c.oldValue(), c.newValue()));
        }
        return rows;
    }

    private List<ProductEvent> toEvents(List<DetectedEvent> detected, Ids ids, Unresolved unresolved) {
        List<ProductEvent> events = new ArrayList<>();
        for (DetectedEvent e : detected) {
            Long productId = ids.product(e.product());
            Long optionId = e.optionKey() == null ? null : ids.option(e.product(), e.optionKey());
            if (productId == null || (e.optionKey() != null && optionId == null)) {
                unresolved.add(e.product() + "|" + e.optionKey());
                continue;
            }
            events.add(new ProductEvent(productId, optionId, e.type(), payload(e.changes())));
        }
        return events;
    }

    // RATE_UP/DOWN 은 바뀐 필드와 전후 값, 나머지는 null
    private String payload(List<RateChange> changes) {
        if (changes.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> fields = changes.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("field", c.field().column());
            m.put("old", c.oldValue());
            m.put("new", c.newValue());
            return m;
        }).toList();
        try {
            return objectMapper.writeValueAsString(Map.of("changes", fields));
        } catch (JsonProcessingException e) {
            throw new GongsiException("change_event payload 직렬화 실패", e);
        }
    }

    // normalize 를 거치지 않은 날짜의 상품·옵션은 id 가 없다 - 버리고 수만 센다
    private static final class Unresolved {
        private int count;

        void add(String ref) {
            if (count++ < 5) {
                log.warn("diff id 없음 {}", ref);
            }
        }
    }
}
