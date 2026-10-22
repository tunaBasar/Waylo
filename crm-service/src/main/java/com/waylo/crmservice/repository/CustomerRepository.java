package com.waylo.crmservice.repository;

import com.waylo.crmservice.domain.Customer;
import com.waylo.crmservice.domain.CustomerTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByEmail(String email);
    List<Customer> findByTier(CustomerTier tier);
    List<Customer> findByActiveTrue();
    boolean existsByEmail(String email);
    List<Customer> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(String firstName, String lastName);

    /**
     * Creates the CRM record for an auth user with the SAME id as the auth userId, so that
     * customerId on bookings/orders resolves directly. Idempotent: a repeated event, or an
     * email already owned by another customer, inserts nothing (returns 0).
     */
    @Modifying
    @Query(value = "INSERT INTO customers (id, first_name, last_name, email, tier, loyalty_points, active, created_at, updated_at) "
            + "VALUES (:id, :firstName, :lastName, :email, 'BRONZE', 0, TRUE, NOW(), NOW()) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("firstName") String firstName,
                       @Param("lastName") String lastName, @Param("email") String email);
}
