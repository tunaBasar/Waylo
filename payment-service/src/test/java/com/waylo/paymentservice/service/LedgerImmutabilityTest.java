package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.LedgerEntry;
import com.waylo.paymentservice.repository.LedgerEntryRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerImmutabilityTest {

    @Test
    void entityHasNoSetters() {
        for (Method m : LedgerEntry.class.getDeclaredMethods()) {
            assertThat(m.getName()).doesNotStartWith("set");
        }
    }

    @Test
    void repositoryDeleteMethodsThrow() {
        LedgerEntryRepository repo = (LedgerEntryRepository) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{LedgerEntryRepository.class}, (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return java.lang.invoke.MethodHandles.privateLookupIn(LedgerEntryRepository.class, java.lang.invoke.MethodHandles.lookup())
                                .unreflectSpecial(method, LedgerEntryRepository.class).bindTo(proxy).invokeWithArguments(args);
                    }
                    return null;
                });
        assertThatThrownBy(() -> repo.deleteById(UUID.randomUUID())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(repo::deleteAll).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(repo::deleteAllInBatch).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void migrationInstallsDatabaseTriggerAgainstUpdateDeleteTruncate() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V2__escrow_ledger_core.sql"));
        assertThat(sql).contains("BEFORE UPDATE OR DELETE ON ledger_entries").contains("BEFORE TRUNCATE ON ledger_entries");
    }
}
