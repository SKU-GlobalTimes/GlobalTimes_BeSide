package com.example.globalTimes_be.global.apiPayload.code;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Spring Page 객체의 불필요한 내부 필드를 제거하고
 * 클라이언트에 필요한 페이징 정보만 담은 공용 응답 DTO
 */
public record PagedResponse<T>(
        long totalElements,
        int totalPages,
        int currentPage,
        int size,
        List<T> items
) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize(),
                page.getContent()
        );
    }
}
