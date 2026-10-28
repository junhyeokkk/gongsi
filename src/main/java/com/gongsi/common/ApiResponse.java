package com.gongsi.common;

/** REST 응답 공통 봉투. 모든 serve 레이어 Controller 가 이걸로 감싸서 반환 */
public record ApiResponse<T>(boolean success, T data, String error) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
