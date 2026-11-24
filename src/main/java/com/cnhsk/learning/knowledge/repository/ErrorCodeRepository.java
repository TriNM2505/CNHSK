package com.cnhsk.learning.knowledge.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cnhsk.learning.knowledge.entity.ErrorCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ErrorCodeRepository extends JpaRepository<ErrorCode, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ErrorCode e WHERE e.code = :code")
    Optional<ErrorCode> findByCodeForUpdate(@Param("code") String code);

    @Query("SELECT e FROM ErrorCode e WHERE e.kid.code IN :kids ORDER BY e.code")
    List<ErrorCode> findByKidCodes(@Param("kids") Collection<String> kids);
}
