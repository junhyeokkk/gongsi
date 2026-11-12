package com.gongsi.collect.normalize;

import com.gongsi.collect.fetch.RawSnapshotRepository;
import com.gongsi.collect.fetch.RawSnapshotRepository.RawPage;
import com.gongsi.collect.normalize.NormalizeService.ErrorSink;
import com.gongsi.collect.normalize.RawRecordReader.Records;
import com.gongsi.collect.normalize.model.NormalizedCompany;
import com.gongsi.collect.normalize.model.NormalizedCompanyArea;
import com.gongsi.collect.normalize.parse.CompanyNormalizer;
import com.gongsi.collect.normalize.store.CompanyUpsertService;
import com.gongsi.collect.normalize.store.NormalizeErrorRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.gongsi.collect.fetch.RawSnapshotRepository.COMPANY;

// NormalizeStep 이 호출 - 그날 companySearch 원문을 company·company_area 에 반영
@Service
public class CompanyNormalizeService {

    private static final Logger log = LoggerFactory.getLogger(CompanyNormalizeService.class);

    private final RawSnapshotRepository rawRepo;
    private final RawRecordReader reader;
    private final CompanyUpsertService upsertService;
    private final NormalizeErrorRecorder errorRecorder;
    private final TransactionTemplate tx;

    public CompanyNormalizeService(RawSnapshotRepository rawRepo, RawRecordReader reader,
                                   CompanyUpsertService upsertService, NormalizeErrorRecorder errorRecorder,
                                   TransactionTemplate tx) {
        this.rawRepo = rawRepo;
        this.reader = reader;
        this.upsertService = upsertService;
        this.errorRecorder = errorRecorder;
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
        ErrorSink<String> errors = new ErrorSink<>(errorRecorder, fetchedOn, COMPANY);

        Records<String, NormalizedCompany, NormalizedCompanyArea> records =
                reader.read(pages, CompanyNormalizer.INSTANCE, errors);
        Map<String, Long> ids = upsertService.upsertCompanies(List.copyOf(records.bases().values()), fetchedOn);
        upsertService.upsertAreas(records.options(), ids, fetchedOn);
        int deactivated = upsertService.deactivateMissing(fetchedOn);

        var summary = new Summary(pages.size(), records.bases().size(), records.options().size(), errors.count(),
                deactivated);
        log.info("normalize COMPANY fetchedOn={} pages={} companies={} areas={} errors={} deactivated={}",
                fetchedOn, summary.pages(), summary.companies(), summary.areas(), summary.errors(), deactivated);
        return summary;
    }

    // 회사 정규화 결과 - 적재한 회사·시도 행 수, 격리한 레코드 수, 비활성으로 바뀐 회사 수
    public record Summary(int pages, int companies, int areas, int errors, int deactivated) {
    }
}
