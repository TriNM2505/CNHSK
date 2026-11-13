package com.cnhsk.learning.reference;

import java.util.Optional;

import org.springframework.data.repository.Repository;

public interface ReferenceItemRepository extends Repository<ReferenceItem, Long> {
    <S extends ReferenceItem> S save(S item);

    Optional<ReferenceItem> findById(Long id);
}