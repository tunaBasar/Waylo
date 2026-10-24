package com.waylo.authservice.repository;

import com.waylo.authservice.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByVerificationToken(String token);
    Optional<User> findByResetToken(String token);
    boolean existsByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByRole(com.waylo.authservice.domain.UserRole role);

    @Query("""
            select u from User u
            where :q is null
               or lower(u.email) like concat(:q, '%')
               or lower(u.firstName) like concat(:q, '%')
               or lower(u.lastName) like concat(:q, '%')
               or lower(concat(u.firstName, ' ', u.lastName)) like concat(:q, '%')
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);
}
