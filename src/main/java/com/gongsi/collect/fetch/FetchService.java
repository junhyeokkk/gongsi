package com.gongsi.collect.fetch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongsi.common.ProductType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// FetchStep 이 호출하는 서비스 - (상품군·금융회사)×금융권×페이지 반복해서 raw_snapshot 적재
@Service
public class FetchService {

    private static final Logger log = LoggerFactory.getLogger(FetchService.class);

    // 은행, 저축은행, 여신전문/보험/금융투자는 포함 여부 미정
    static final List<String> FIN_GRP_NOS = List.of("020000", "030300");

    private static final String OK_CODE = "000";

    private final FssOpenFinanceClient client;
    private final RawSnapshotRepository rawRepo;
    private final ObjectMapper objectMapper;

    public FetchService(FssOpenFinanceClient client, RawSnapshotRepository rawRepo, ObjectMapper objectMapper) {
        this.client = client;
        this.rawRepo = rawRepo;
        this.objectMapper = objectMapper;
    }

    // 상품군 5개와 금융회사를 돌리고 전체 요약 로그를 찍은 뒤 FetchSummary 반환
    public FetchSummary fetchAll(LocalDate targetDate) {
        List<FetchSummary.DatasetResult> results = new ArrayList<>(Arrays.stream(ProductType.values())
                .map(type -> fetchOne(targetDate, type))
                .toList());
        results.add(fetchCompanies(targetDate));
        var summary = new FetchSummary(targetDate, results);
        log.info("fetch 완료 targetDate={} pages={} totalCount={} hasFailures={}", targetDate,
                results.stream().mapToInt(FetchSummary.DatasetResult::pages).sum(),
                results.stream().mapToInt(FetchSummary.DatasetResult::totalCount).sum(),
                summary.hasFailures());
        return summary;
    }

    // 한 상품군의 권역 2개를 수집
    public FetchSummary.DatasetResult fetchOne(LocalDate targetDate, ProductType type) {
        return fetchDataset(targetDate, type.name(), (finGrpNo, pageNo) -> client.fetchPage(type, finGrpNo, pageNo));
    }

    // 금융회사 목록을 상품과 같은 권역으로 수집 - 원문은 product_type=COMPANY 로 raw_snapshot 에 둔다
    public FetchSummary.DatasetResult fetchCompanies(LocalDate targetDate) {
        return fetchDataset(targetDate, RawSnapshotRepository.COMPANY, client::fetchCompanyPage);
    }

    // 실패한 권역은 failedGroups 에 담고 계속 진행
    private FetchSummary.DatasetResult fetchDataset(LocalDate targetDate, String dataset, PageFetcher fetcher) {
        int pages = 0;
        int totalCount = 0;
        List<String> failedGroups = new ArrayList<>();
        for (String finGrpNo : FIN_GRP_NOS) {
            try {
                GroupResult group = fetchGroup(targetDate, dataset, finGrpNo, fetcher);
                pages += group.pages();
                totalCount += group.totalCount();
            } catch (FssApiException e) {
                log.error("fetch 실패 {} {}: {}", dataset, finGrpNo, e.getMessage());
                failedGroups.add(finGrpNo);
            }
        }
        log.info("fetch {} pages={} totalCount={} failedGroups={}", dataset, pages, totalCount, failedGroups);
        return new FetchSummary.DatasetResult(dataset, pages, totalCount, failedGroups);
    }

    // 한 금융권을 max_page_no 까지 순회 - 페이지마다 바로 적재해서 중간에 실패해도 앞 페이지는 남음
    private GroupResult fetchGroup(LocalDate targetDate, String dataset, String finGrpNo, PageFetcher fetcher) {
        int pageNo = 1;
        int maxPage = 1;
        int totalCount = 0;
        while (pageNo <= maxPage) {
            String body = fetcher.fetch(finGrpNo, pageNo);
            JsonNode result = parseResult(body, dataset, finGrpNo, pageNo);
            rawRepo.insertOrUpdate(targetDate, dataset, finGrpNo, pageNo, body);
            maxPage = result.path("max_page_no").asInt(1);
            totalCount = result.path("total_count").asInt(0);
            pageNo++;
        }
        return new GroupResult(pageNo - 1, totalCount);
    }

    // err_cd 가 000 이 아니면 HTTP 200 이어도 실패로 본다
    private JsonNode parseResult(String body, String dataset, String finGrpNo, int pageNo) {
        JsonNode result;
        try {
            result = objectMapper.readTree(body).path("result");
        } catch (JsonProcessingException e) {
            throw new FssApiException("FSS 응답 JSON 파싱 실패 type=%s finGrpNo=%s page=%d".formatted(
                    dataset, finGrpNo, pageNo));
        }
        String errCd = result.path("err_cd").asText();
        if (!OK_CODE.equals(errCd)) {
            throw new FssApiException("FSS 응답 오류 type=%s finGrpNo=%s page=%d err_cd=%s err_msg=%s".formatted(
                    dataset, finGrpNo, pageNo, errCd, result.path("err_msg").asText()));
        }
        return result;
    }

    // 권역·페이지 하나를 받아오는 호출
    @FunctionalInterface
    private interface PageFetcher {
        String fetch(String finGrpNo, int pageNo);
    }

    // 금융권 하나의 적재 페이지 수와 total_count
    private record GroupResult(int pages, int totalCount) {
    }
}
