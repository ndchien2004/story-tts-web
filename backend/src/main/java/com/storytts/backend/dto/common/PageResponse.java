package com.storytts.backend.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Bọc kết quả phân trang thành JSON gọn gàng cho React (thay vì trả thẳng Page của Spring). */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return from(page, Function.identity());
    }
}
