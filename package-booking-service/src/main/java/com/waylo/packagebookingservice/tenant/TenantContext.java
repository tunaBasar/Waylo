package com.waylo.packagebookingservice.tenant;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-thread database tenant context (RLS). Read by {@link TenantAwareJpaDialect} at the start of every
 * JPA transaction and applied as transaction-local settings (app.mode, app.org_ids) for PostgreSQL RLS.
 * Threads without a context (scheduler, Kafka) run in SERVICE mode, matching the pre-RLS behaviour.
 */
public final class TenantContext {

    public enum Mode { PUBLIC, PARTNER, SERVICE, ADMIN }

    public record State(Mode mode, Set<UUID> orgIds) {
        public String orgIdsCsv() {
            return String.join(",", orgIds.stream().map(UUID::toString).toList());
        }
    }

    private static final State SERVICE_STATE = new State(Mode.SERVICE, Set.of());
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static State current() {
        State state = CURRENT.get();
        return state == null ? SERVICE_STATE : state;
    }

    public static void set(Mode mode) {
        CURRENT.set(new State(mode, Set.of()));
    }

    /**
     * Records an organization the caller was just authorized for. Only narrows a PUBLIC/PARTNER request:
     * ADMIN and SERVICE contexts are already unrestricted and stay that way.
     */
    public static void authorizePartner(UUID organizationId) {
        State state = CURRENT.get();
        if (organizationId == null || state == null || state.mode() == Mode.ADMIN || state.mode() == Mode.SERVICE) {
            return;
        }
        Set<UUID> orgs = new LinkedHashSet<>(state.orgIds());
        orgs.add(organizationId);
        CURRENT.set(new State(Mode.PARTNER, Collections.unmodifiableSet(orgs)));
        reapplyToActiveTransaction();
    }

    /**
     * The access check runs inside the service's @Transactional method, i.e. after TenantAwareJpaDialect already
     * began the transaction in PUBLIC mode. Re-apply the (now narrowed) context to that same transaction so the
     * partner's reads and writes run in PARTNER mode. Still transaction local (set_config(..., true)).
     */
    private static void reapplyToActiveTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        for (Object resource : TransactionSynchronizationManager.getResourceMap().values()) {
            if (resource instanceof EntityManagerHolder holder) {
                EntityManager em = holder.getEntityManager();
                State state = CURRENT.get();
                em.unwrap(Session.class).doWork(connection -> TenantAwareJpaDialect.apply(connection, state));
            }
        }
    }

    public static void clear() {
        CURRENT.remove();
    }
}
