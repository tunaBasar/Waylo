package com.waylo.busservice.repository;

import com.waylo.busservice.domain.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TerminalRepository extends JpaRepository<Terminal, UUID> {

    List<Terminal> findAllByOrderByCityAscNameAsc();

    List<Terminal> findByActiveTrueOrderByCityAscNameAsc();

    Optional<Terminal> findByCityIgnoreCaseAndNameIgnoreCase(String city, String name);
}
