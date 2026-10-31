package com.gongsi.collect.normalize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongsi.collect.fetch.RawSnapshotRepository;
import com.gongsi.collect.fetch.RawSnapshotRepository.RawPage;
import com.gongsi.collect.normalize.model.NormalizedCompany;
import com.gongsi.collect.normalize.model.NormalizedCompanyArea;
import com.gongsi.collect.normalize.parse.CompanyNormalizer;
import com.gongsi.collect.normalize.parse.NormalizeErrorReason;
import com.gongsi.collect.normalize.parse.NormalizeException;
import com.gongsi.collect.normalize.store.CompanyUpsertService;
import com.gongsi.collect.normalize.store.NormalizeErrorRecorder;
import com.gongsi.common.GongsiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gongsi.collect.fetch.RawSnapshotRepository.COMPANY;

// NormalizeStep 이 호출 - 그날 companySearch 원문을 company·company_area 에 반영
@Service
public class CompanyNormalizeService {

    private static final Logger log = LoggerFactory.getLogger(CompanyNormalizeService.class);

    private final RawSnapshotRepository rawRepo;
    private final CompanyUpsertService upsertService;
    private final NormalizeErrorRecorder errorRecorder;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public CompanyNormalizeService(RawSnapshotRepository rawRepo, CompanyUpsertService upsertService,
                                   NormalizeErrorRecorder errorRecorder, ObjectMapper objectMapper,
                                   TransactionTemplate tx) {
        this.rawRepo = rawRepo;
        this.upsertService = upsertService;
        this.errorRecorder = errorRecorder;
        this.objectMapper = objectMapper;
        this.tx = tx;
    }

    // 한 트랜잭션으로 반영 - 원문이 없으면 건너뛴다(도입 전 날짜 재처리에서 회사를 모두 소멸로 오판하지 않게)
    public Summary normalize(LocalDate fetchedOn) {
        return tx.execute(status -> doNormalize(fetchedOn));
    }

    private Summary doNormalize(LocalDate fetchedOn) {
        List<RawPage> pages = rawRepo.findByDate(fetchedOn, COMPANY);
        if (pages.isEmpty()) {
            log.warn("normalize COMPANY 건너뜀 - raw_snapshot 없음 fetchedOn={}", fetchedOn);
            return new Summary(0, 0, 0, 0, 0);
        }
        errorRecorder.clear(fetchedOn, COMPANY);
        ErrorSink errors = new ErrorSink(fetchedOn);

        Map<String, NormalizedCompany> companies = new LinkedHashMap<>();
        List<PendingArea> pending = new ArrayList<>();
        for (RawPage page : pages) {
            JsonNode result = parse(page).path("result");
            JsonNode bases = result.path("baseList");
            for (int i = 0; i < bases.size(); i++) {
                try {
                    NormalizedCompany c = CompanyNormalizer.normalizeBase(bases.get(i)).withFinGrpNo(page.finGrpNo());
                    companies.put(c.finCoNo(), c);
                } catch (NormalizeException e) {
                    errors.add(page.id(), "BASE", i, e.reason(), describe(bases.get(i), e));
                }
            }
            JsonNode options = result.path("optionList");
            for (int i = 0; i < options.size(); i++) {
                try {
                    pending.add(new PendingArea(page.id(), i, CompanyNormalizer.normalizeOption(options.get(i))));
                } catch (NormalizeException e) {
                    errors.add(page.id(), "OPTION", i, e.reason(), describe(options.get(i), e));
                }
            }
        }

        // 고아 판정은 전 페이지의 정상 base 를 모은 뒤에 한다
        List<NormalizedCompanyArea> areas = new ArrayList<>();
        for (PendingArea p : pending) {
            if (companies.containsKey(p.area().finCoNo())) {
                areas.add(p.area());
            } else {
                errors.add(p.rawSnapshotId(), "OPTION", p.index(), NormalizeErrorReason.ORPHAN_OPTION,
                        p.area().finCoNo() + " base 없음");
            }
        }

        Map<String, Long> ids = upsertService.upsertCompanies(List.copyOf(companies.values()), fetchedOn);
        upsertService.upsertAreas(areas, ids, fetchedOn);
        int deactivated = upsertService.deactivateMissing(fetchedOn);

        var summary = new Summary(pages.size(), companies.size(), areas.size(), errors.count, deactivated);
        log.info("normalize COMPANY fetchedOn={} pages={} companies={} areas={} errors={} deactivated={}",
                fetchedOn, summary.pages(), summary.companies(), summary.areas(), summary.errors(), deactivated);
        return summary;
    }

    private JsonNode parse(RawPage page) {
        try {
            return objectMapper.readTree(page.payload());
        } catch (JsonProcessingException e) {
            throw new GongsiException("raw_snapshot 파싱 실패 id=" + page.id(), e);
        }
    }

    private static String describe(JsonNode record, NormalizeException e) {
        return record.path("fin_co_no").asText("?") + ": " + e.getMessage();
    }

    // 회사 정규화 결과 - 적재한 회사·시도 행 수, 격리한 레코드 수, 비활성으로 바뀐 회사 수
    public record Summary(int pages, int companies, int areas, int errors, int deactivated) {
    }

    // 고아 판정 전까지 원문 위치를 들고 있는 시·도 행
    private record PendingArea(long rawSnapshotId, int index, NormalizedCompanyArea area) {
    }

    // 에러를 기록하면서 개수를 센다
    private final class ErrorSink {
        private final LocalDate fetchedOn;
        private int count;

        ErrorSink(LocalDate fetchedOn) {
            this.fetchedOn = fetchedOn;
        }

        void add(long rawSnapshotId, String listKind, int index, NormalizeErrorReason reason, String detail) {
            errorRecorder.record(fetchedOn, COMPANY, rawSnapshotId, listKind, index, reason, detail);
            count++;
        }
    }
}
