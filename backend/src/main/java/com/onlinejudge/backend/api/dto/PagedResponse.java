package com.onlinejudge.backend.api.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Stable pagination envelope — deliberately not Spring's {@code Page} serialization,
 * which is not a stable wire contract.
 */
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PagedResponse<T> of(Page<S> source, Function<S, T> mapper) {
        return new PagedResponse<>(source.getContent().stream().map(mapper).toList(),
                source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages());
    }
}
