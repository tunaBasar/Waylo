package com.waylo.busservice.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.Session;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.TransactionDefinition;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Applies the RLS tenant context at the start of every JPA transaction (RM-14).
 *
 * Why here: every repository/service DB access goes through JpaTransactionManager (Spring Data repositories
 * are transactional themselves), and this hook runs once per physical transaction on the connection that
 * transaction uses. A StatementInspector only sees SQL strings, and a DataSource connection preparer runs at
 * checkout, which does not line up with transaction boundaries.
 *
 * Why it cannot leak: SET LOCAL ROLE and set_config(..., true) are transaction scoped. PostgreSQL discards
 * them on COMMIT and on ROLLBACK, so a pooled connection always returns to the login role with no app.*
 * settings, and the next transaction sets its own context from scratch.
 */
public class TenantAwareJpaDialect extends HibernateJpaDialect {

    static final String APPLY_SQL =
            "SELECT set_config('role', 'waylo_app', true), set_config('app.mode', ?, true), set_config('app.org_ids', ?, true)";

    @Override
    public Object beginTransaction(EntityManager entityManager, TransactionDefinition definition)
            throws PersistenceException, SQLException {
        Object data = super.beginTransaction(entityManager, definition);
        TenantContext.State state = TenantContext.current();
        entityManager.unwrap(Session.class).doWork(connection -> apply(connection, state));
        return data;
    }

    static void apply(java.sql.Connection connection, TenantContext.State state) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(APPLY_SQL)) {
            ps.setString(1, state.mode().name().toLowerCase());
            ps.setString(2, state.orgIdsCsv());
            ps.execute();
        }
    }
}
