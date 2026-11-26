package mz.netlineserv.chuangastudio.shared.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** Página de resultados (mesmo formato do SPO-BACK). */
public record PageResponse<T>(
        boolean isFirst,
        long totalItems,
        int size,
        boolean isLast,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext,
        int currentPage,
        List<T> content
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.isFirst(),
                page.getTotalElements(),
                page.getSize(),
                page.isLast(),
                page.getTotalPages(),
                page.hasPrevious(),
                page.hasNext(),
                page.getNumber(),
                page.getContent()
        );
    }
}
