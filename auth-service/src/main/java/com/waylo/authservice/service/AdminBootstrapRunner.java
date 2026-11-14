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

/**
 * Platform admin bootstrap. EMAIL only: promotes that existing, email-verified user if no ADMIN exists yet.
 * EMAIL + PASSWORD (dev/seed): creates or promotes that user. Idempotent.
 */
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
        if (hasEmail && !hasPassword) {
            promoteExistingIfNoAdmin(email.trim().toLowerCase(Locale.ROOT));
            return;
        }
        if (hasEmail != hasPassword) {
            log.warn("Bootstrap admin skipped: WAYLO_BOOTSTRAP_ADMIN_PASSWORD set without WAYLO_BOOTSTRAP_ADMIN_EMAIL");
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

    /** Email-only mode: once, promote an existing email-verified user when the platform has no ADMIN. */
    private void promoteExistingIfNoAdmin(String normalized) {
        try {
            if (userRepository.countByRole(UserRole.ADMIN) > 0) {
                return;
            }
            Optional<User> existing = userRepository.findByEmailIgnoreCase(normalized);
            if (existing.isEmpty()) {
                log.warn("Bootstrap admin skipped: no user registered with the configured email");
                return;
            }
            User user = existing.get();
            if (!user.isEmailVerified()) {
                log.warn("Bootstrap admin skipped: configured user has not verified their email");
                return;
            }
            user.setRole(UserRole.ADMIN);
            userRepository.save(user);
            log.info("AUDIT bootstrap admin: promoted user {} (id={}) to ADMIN", normalized, user.getId());
        } catch (RuntimeException e) {
            log.error("Bootstrap admin promotion failed: {}", e.getClass().getSimpleName());
        }
    }
}
