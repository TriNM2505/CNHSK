package com.cnhsk.auth.repository;

import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    long countByRoleCode(RoleCode roleCode);

    boolean existsByUserIdAndRoleCode(Long userId, RoleCode roleCode);

    List<UserRole> findAllByUserIdOrderByRoleCodeAsc(Long userId);

    List<UserRole> findAllByUserIdInOrderByUserIdAscRoleCodeAsc(Collection<Long> userIds);
}
