package com.waylo.paymentservice.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Prepaid balance at an external flight supplier. Changed only by SupplierBalanceService under a row lock. */
@Entity
@Table(name = "supplier_accounts")
public class SupplierAccount {

    @Id
    @Column(name = "supplier", length = 30, updatable = false, nullable = false)
    private String supplier;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "bpchar(3)", updatable = false)
    private String currency;

    @Column(name = "balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SupplierAccount() {}

    public SupplierAccount(String supplier, String currency) {
        this.supplier = supplier;
        this.currency = currency;
    }

    public String getSupplier() { return supplier; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void apply(BigDecimal delta) {
        this.balance = this.balance.add(delta);
        this.updatedAt = Instant.now();
    }
}
