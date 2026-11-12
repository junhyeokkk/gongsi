package com.gongsi.collect.normalize;

import com.fasterxml.jackson.databind.JsonNode;
import com.gongsi.collect.fetch.RawSnapshotRepository;
import com.gongsi.collect.fetch.RawSnapshotRepository.RawPage;
import com.gongsi.collect.normalize.RawRecordReader.Records;
import com.gongsi.collect.normalize.RawRecordReader.Rejects;
import com.gongsi.collect.normalize.parse.CreditNormalizer;
import com.gongsi.collect.normalize.parse.NormalizeErrorReason;
import com.gongsi.collect.normalize.parse.NormalizeException;
import com.gongsi.collect.normalize.parse.Normalizer;
import com.gongsi.collect.normalize.parse.RecordParser;
import com.gongsi.collect.normalize.store.CreditUpsertService;
import com.gongsi.collect.normalize.store.NormalizeErrorRecorder;
import com.gongsi.collect.normalize.store.ProductStore;
import com.gongsi.collect.normalize.store.ProductUpsertService;
import com.gongsi.common.GongsiException;
import com.gongsi.common.ProductType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// NormalizeStep 이 호출 - 그날 raw_snapshot 을 상품군별로 정규화해 product·product_option, credit_loan_* 에 반영
@Service
public class NormalizeService {

    private static final Logger log = LoggerFactory.getLogger(NormalizeService.class);

    // CRED 는 credit_loan_* 에, 나머지는 product 에 적재
    public static final List<ProductType> TYPES = List.of(
            ProductType.DEP, ProductType.SAV, ProductType.MORT, ProductType.RENT, ProductType.CRED);

    private final RawSnapshotRepository rawRepo;
    private final RawRecordReader reader;
    private final ProductUpsertService productStore;
    private final CreditUpsertService creditStore;
    private final NormalizeErrorRecorder errorRecorder;
    private final TransactionTemplate tx;

    public NormalizeService(RawSnapshotRepository rawRepo, RawRecordReader reader, ProductUpsertService productStore,
                            CreditUpsertService creditStore, NormalizeErrorRecorder errorRecorder,
                            TransactionTemplate tx) {
        this.rawRepo = rawRepo;
        this.reader = reader;
        this.productStore = productStore;
        this.creditStore = creditStore;
        this.errorRecorder = errorRecorder;
        this.tx = tx;
    }

    // 상품군마다 별도 트랜잭션 - 한 상품군이 실패해도 앞 상품군 결과는 남는다
    public List<NormalizeSummary> normalizeAll(LocalDate fetchedOn) {
        return TYPES.stream().map(type -> tx.execute(status -> normalize(fetchedOn, type))).toList();
    }

    NormalizeSummary normalize(LocalDate fetchedOn, ProductType type) {
        return type == ProductType.CRED
                ? normalize(fetchedOn, type, CreditNormalizer.INSTANCE, creditStore)
                : normalize(fetchedOn, type, Normalizer.of(type), productStore.forType(type));
    }

    private <K, B, O> NormalizeSummary normalize(LocalDate fetchedOn, ProductType type,
                                                 RecordParser<K, B, O> parser, ProductStore<K, B, O> store) {
        List<RawPage> pages = rawRepo.findByDate(fetchedOn, type);
        if (pages.isEmpty()) {
            // 원문 없이 진행하면 모든 상품을 소멸로 오판한다
            throw new GongsiException("raw_snapshot 없음 fetchedOn=%s type=%s".formatted(fetchedOn, type));
        }
        errorRecorder.clear(fetchedOn, type);
        ErrorSink<K> errors = new ErrorSink<>(errorRecorder, fetchedOn, type.name());

        Records<K, B, O> records = reader.read(pages, parser, errors);
        Map<K, Long> ids = store.upsertProducts(List.copyOf(records.bases().values()), fetchedOn);
        store.upsertOptions(records.options(), ids, fetchedOn);
        int deactivated = store.deactivateMissing(fetchedOn);

        var summary = new NormalizeSummary(type, pages.size(), records.bases().size(), records.options().size(),
                errors.count(), deactivated);
        log.info("normalize {} fetchedOn={} pages={} products={} options={} errors={} deactivated={}",
                type, fetchedOn, summary.pages(), summary.products(), summary.options(), summary.errors(), deactivated);
        return summary;
    }

    // 걸러진 레코드를 normalize_error 에 남기면서 개수를 센다 - dataset 은 상품군 코드 또는 COMPANY
    static final class ErrorSink<K> implements Rejects<K> {
        private final NormalizeErrorRecorder recorder;
        private final LocalDate fetchedOn;
        private final String dataset;
        private int count;

        ErrorSink(NormalizeErrorRecorder recorder, LocalDate fetchedOn, String dataset) {
            this.recorder = recorder;
            this.fetchedOn = fetchedOn;
            this.dataset = dataset;
        }

        @Override
        public void failed(RawPage page, String listKind, int index, JsonNode record, NormalizeException e) {
            add(page, listKind, index, e.reason(), describe(record) + ": " + e.getMessage());
        }

        @Override
        public void orphan(RawPage page, int index, K parent) {
            add(page, "OPTION", index, NormalizeErrorReason.ORPHAN_OPTION, parent + " base 없음");
        }

        int count() {
            return count;
        }

        private void add(RawPage page, String listKind, int index, NormalizeErrorReason reason, String detail) {
            recorder.record(fetchedOn, dataset, page.id(), listKind, index, reason, detail);
            count++;
        }

        // 어느 레코드인지 찾을 수 있게 원문의 자연키 필드만
        private static String describe(JsonNode record) {
            StringBuilder key = new StringBuilder(record.path("fin_co_no").asText("?"));
            for (String field : new String[]{"fin_prdt_cd", "crdt_prdt_type"}) {
                if (record.has(field)) {
                    key.append('/').append(record.path(field).asText("?"));
                }
            }
            return key.toString();
        }
    }
}
