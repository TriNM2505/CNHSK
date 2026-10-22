package com.cnhsk.auth.repository;

import com.cnhsk.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    @Query(value = "SELECT COUNT(DISTINCT u.id) FROM auth.users u "
            + "JOIN auth.user_roles ur ON ur.user_id = u.id "
            + "WHERE ur.role_code = 'ADMIN' AND u.banned_at IS NULL", nativeQuery = true)
    long countActiveAdmins();

    Page<User> findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
            String emailQuery, String displayNameQuery, Pageable pageable);

    @Query(value = "SELECT u.* FROM auth.users u "
            + "WHERE (CAST(:query AS text) IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR LOWER(u.display_name) LIKE LOWER(CONCAT('%', :query, '%'))) "
            + "AND (CAST(:role AS text) IS NULL OR EXISTS (SELECT 1 FROM auth.user_roles user_role "
            + "WHERE user_role.user_id = u.id AND user_role.role_code = :role)) "
            + "AND (CAST(:status AS text) IS NULL "
            + "OR (:status = 'BANNED' AND u.banned_at IS NOT NULL) "
            + "OR (:status = 'UNVERIFIED' AND u.banned_at IS NULL AND u.email_verified_at IS NULL) "
            + "OR (:status = 'ACTIVE' AND u.banned_at IS NULL AND u.email_verified_at IS NOT NULL)) "
            + "ORDER BY u.created_at DESC, u.id DESC",
            countQuery = "SELECT COUNT(*) FROM auth.users u "
                    + "WHERE (CAST(:query AS text) IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) "
                    + "OR LOWER(u.display_name) LIKE LOWER(CONCAT('%', :query, '%'))) "
                    + "AND (CAST(:role AS text) IS NULL OR EXISTS (SELECT 1 FROM auth.user_roles user_role "
                    + "WHERE user_role.user_id = u.id AND user_role.role_code = :role)) "
                    + "AND (CAST(:status AS text) IS NULL "
                    + "OR (:status = 'BANNED' AND u.banned_at IS NOT NULL) "
                    + "OR (:status = 'UNVERIFIED' AND u.banned_at IS NULL AND u.email_verified_at IS NULL) "
                    + "OR (:status = 'ACTIVE' AND u.banned_at IS NULL AND u.email_verified_at IS NOT NULL))",
            nativeQuery = true)
    Page<User> searchAdminUsers(@Param("query") String query,
            @Param("role") String role,
            @Param("status") String status,
            Pageable pageable);
}
