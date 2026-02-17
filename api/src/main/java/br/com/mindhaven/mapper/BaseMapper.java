package br.com.mindhaven.mapper;

import java.util.List;

public interface BaseMapper<D, E> {
    E toEntity(D dto);
    D toDto(E entity);
    List<D> toDtoList(List<E> entities);
}