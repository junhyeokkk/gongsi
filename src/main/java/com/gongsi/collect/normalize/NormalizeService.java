package com.gongsi.collect.normalize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongsi.collect.fetch.RawSnapshotRepository;
import com.gongsi.collect.fetch.RawSnapshotRepository.RawPage;
import com.gongsi.collect.normalize.model.NormalizedOption;
import com.gongsi.collect.normalize.model.NormalizedProduct;
import com.gongsi.collect.normalize.model.ProductKey;
import com.gongsi.collect.normalize.parse.NormalizeErrorReason;
import com.gongsi.collect.normalize.parse.NormalizeException;
import com.gongsi.collect.normalize.parse.Normalizer;
import com.gongsi.collect.normalize.store.NormalizeErrorRecorder;
import com.gongsi.collect.normalize.store.ProductUpsertService;
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

// NormalizeStep 이 호출 - 그날 raw_snapshot 을 상품군별로 정규화해 product·product_option 에 반영
@Service
public class NormalizeService {

    private static final Logger log = LoggerFactory.getLogger(NormalizeService.class);

    // CRED 는 credit_loan_* 대상이라 아직 제외
    static final List<ProductType> TYPES = List.of(ProductType.DEP, ProductType.SAV, ProductType.MORT, ProductType.RENT);

    private final RawSnapshotRepository rawRepo;
    private final ProductUpsertService upsertService;
    private final NormalizeErrorRecorder errorRecorder;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public NormalizeService(RawSnapshotRepository rawRepo, ProductUpsertService upsertService,
                            NormalizeErrorRecorder errorRecorder, ObjectMapper objectMapper, TransactionTemplate tx) {
        this.rawRepo = rawRepo;
        this.upsertService = upsertService;
        this.errorRecorder = errorRecorder;
        this.objectMapper = objectMapper;
        this.tx = tx;
    }

    // 상품군마다 별도 트랜잭션 - 한 상품군이 실패해도 앞 상품군 결과는 남는다
    public List<NormalizeSummary> normalizeAll(LocalDate fetchedOn) {
        return TYPES.stream().map(type -> tx.execute(status -> normalize(fetchedOn, type))).toList();
    }

    NormalizeSummary normalize(LocalDate fetchedOn, ProductType type) {
        List<RawPage> pages = rawRepo.findByDate(fetchedOn, type);
        if (pages.isEmpty()) {
            // 원문 없이 진행하면 모든 상품을 소멸로 오판한다
            throw new GongsiException("raw_snapshot 없음 fetchedOn=%s type=%s".formatted(fetchedOn, type));
        }
        errorRecorder.clear(fetchedOn, type);
        Normalizer normalizer = Normalizer.of(type);
        ErrorSink errors = new ErrorSink(fetchedOn, type);

        Map<ProductKey, NormalizedProduct> products = new LinkedHashMap<>();
        List<PendingOption> pending = new ArrayList<>();
        for (RawPage page : pages) {
            JsonNode result = parse(page).path("result");
            JsonNode bases = result.path("baseList");
            for (int i = 0; i < bases.size(); i++) {
                try {
                    NormalizedProduct p = normalizer.normalizeBase(bases.get(i)).withFinGrpNo(page.finGrpNo());
                    products.put(p.key(), p);
                } catch (NormalizeException e) {
                    errors.add(page.id(), "BASE", i, e.reason(), describe(bases.get(i), e));
                }
            }
            JsonNode options = result.path("optionList");
            for (int i = 0; i < options.size(); i++) {
                try {
                    pending.add(new PendingOption(page.id(), i, normalizer.normalizeOption(options.get(i))));
                } catch (NormalizeException e) {
                    errors.add(page.id(), "OPTION", i, e.reason(), describe(options.get(i), e));
                }
            }
        }

        // 고아 판정은 전 페이지의 정상 base 를 모은 뒤에 한다
        List<NormalizedOption> options = new ArrayList<>();
        for (PendingOption o : pending) {
            if (products.containsKey(o.option().productKey())) {
                options.add(o.option());
            } else {
                errors.add(o.rawSnapshotId(), "OPTION", o.index(), NormalizeErrorReason.ORPHAN_OPTION,
                        o.option().productKey() + " base 없음");
            }
        }

        Map<ProductKey, Long> ids = upsertService.upsertProducts(type, List.copyOf(products.values()), fetchedOn);
        upsertService.upsertOptions(options, ids, fetchedOn);
        int deactivated = upsertService.deactivateMissing(type, fetchedOn);

        var summary = new NormalizeSummary(type, pages.size(), products.size(), options.size(), errors.count, deactivated);
        log.info("normalize {} fetchedOn={} pages={} products={} options={} errors={} deactivated={}",
                type, fetchedOn, summary.pages(), summary.products(), summary.options(), summary.errors(), deactivated);
        return summary;
    }

    private JsonNode parse(RawPage page) {
        try {
            return objectMapper.readTree(page.payload());
        } catch (JsonProcessingException e) {
            // fetch 가 파싱을 통과한 원문만 적재하므로 여기 오면 DB 가 오염된 것
            throw new GongsiException("raw_snapshot 파싱 실패 id=" + page.id(), e);
        }
    }

    // 어느 레코드인지 찾을 수 있게 자연키와 사유만
    private static String describe(JsonNode record, NormalizeException e) {
        return record.path("fin_co_no").asText("?") + "/" + record.path("fin_prdt_cd").asText("?") + ": " + e.getMessage();
    }

    // 고아 판정 전까지 원문 위치를 들고 있는 옵션
    private record PendingOption(long rawSnapshotId, int index, NormalizedOption option) {
    }

    // 에러를 기록하면서 개수를 센다
    private final class ErrorSink {
        private final LocalDate fetchedOn;
        private final ProductType type;
        private int count;

        ErrorSink(LocalDate fetchedOn, ProductType type) {
            this.fetchedOn = fetchedOn;
            this.type = type;
        }

        void add(long rawSnapshotId, String listKind, int index, NormalizeErrorReason reason, String detail) {
            errorRecorder.record(fetchedOn, type, rawSnapshotId, listKind, index, reason, detail);
            count++;
        }
    }
}
