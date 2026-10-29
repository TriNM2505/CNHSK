package com.cnhsk.learning.common;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

    Optional<ServiceRequest> findByIdAndKind(Long id, ServiceRequestKind kind);

    boolean existsByKindAndStatusAndFileHash(
            ServiceRequestKind kind, ServiceRequestStatus status, String fileHash);

    List<ServiceRequest> findByKindAndStatus(ServiceRequestKind kind, ServiceRequestStatus status);

    Page<ServiceRequest> findByKind(ServiceRequestKind kind, Pageable pageable);
}
