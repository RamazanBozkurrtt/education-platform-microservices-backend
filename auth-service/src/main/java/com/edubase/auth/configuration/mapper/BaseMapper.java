package com.edubase.auth.configuration.mapper;

import java.util.List;

public interface BaseMapper<E,D,U> {

    E toEntityFromResponse(D Response);
    D toResponseFromEntity(E entity);
    D toResponseFromRequest(U dto);
    E toEntityFromRequest(U dto);
    List<D> toResponseListFromEntityList(List<E> dtos);

}
