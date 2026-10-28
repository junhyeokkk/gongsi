package com.gongsi.serve.product;

import com.gongsi.common.ApiResponse;
import com.gongsi.common.ProductType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공시 상품 조회 API. 읽기 전용 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductQueryService queryService;

    public ProductController(ProductQueryService queryService) {
        this.queryService = queryService;
    }

    // TODO 5주차: GET /api/products?type=DEP&saveTerm=12&sort=maxRate,desc&page=0&size=20
    //   - 페이지네이션 (Pageable or page/size 쿼리)
    //   - 정렬 — 최대금리 내림차순 기본
    //   - 응답 — ApiResponse<Page<ProductSummaryDto>>
    @GetMapping
    public ApiResponse<Object> list(@RequestParam(required = false) ProductType type,
                                    @RequestParam(required = false) Integer saveTerm) {
        throw new UnsupportedOperationException("5주차 구현 예정");
    }

    // TODO 5주차: GET /api/products/{id} — 단건 상세 (option 전체 + 최근 rate_history N 건)
    @GetMapping("/{id}")
    public ApiResponse<Object> detail(@PathVariable Long id) {
        throw new UnsupportedOperationException("5주차 구현 예정");
    }
}
