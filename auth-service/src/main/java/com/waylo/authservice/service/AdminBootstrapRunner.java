package com.waylo.authservice.service;

import com.waylo.authservice.domain.User;
import com.waylo.authservice.domain.UserRole;
import com.waylo.authservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import com.waylo.authservice.validation.PasswordPolicy;
import java.util.Optional;

/** Ensures a platform admin exists from WAYLO_BOOTSTRAP_ADMIN_EMAIL / _PASSWORD. Idempotent. */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrapRunner(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                @Value("${WAYLO_BOOTSTRAP_ADMIN_EMAIL:}") String email,
                                @Value("${WAYLO_BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPassword = password != null && !password.isBlank();
        if (!hasEmail && !hasPassword) {
            return;
        }
        if (hasEmail != hasPassword) {
            log.warn("Bootstrap admin skipped: both WAYLO_BOOTSTRAP_ADMIN_EMAIL and WAYLO_BOOTSTRAP_ADMIN_PASSWORD must be set");
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!PasswordPolicy.isValid(password, normalized)) {
            log.error("Bootstrap admin skipped for {}: {}", normalized, PasswordPolicy.MESSAGE);
            return;
        }
        try {
            Optional<User> existing = userRepository.findByEmailIgnoreCase(normalized);
            String outcome;
            if (existing.isEmpty()) {
                User user = new User();
                user.setFirstName("Platform");
                user.setLastName("Admin");
                user.setEmail(normalized);
                user.setPassword(passwordEncoder.encode(password));
                user.setRole(UserRole.ADMIN);
                user.setEmailVerified(true);
                userRepository.save(user);
                outcome = "created";
            } else if (existing.get().getRole() != UserRole.ADMIN) {
                User user = existing.get();
                user.setRole(UserRole.ADMIN);
                userRepository.save(user);
                outcome = "promoted";
            } else {
                outcome = "unchanged";
            }
            log.info("Bootstrap admin ensured: {} {}", outcome, normalized);
        } catch (RuntimeException e) {
            log.error("Bootstrap admin failed for {}: {}", normalized, e.getClass().getSimpleName());
        }
    }
}
