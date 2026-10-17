package com.waylo.packagebookingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Siparis kalemi. PRODUCT-ARCHITECTURE.md SS5: OrderItem(vertical, supplierOrganizationId,
 * productRef, fulfillmentRef, commercialSnapshot). commercialSnapshot burada tipli alanlara
 * acilmistir (description/unitAmount/quantity/currency), JSONB kullanilmaz (repo genelinde
 * JSONB kolon ornegi yok).
 *
 * fulfillmentRef, dikeyin fulfillment kaydina (orn. ileride hotel-service reservation) isaret
 * eder; bugun hotel-service'te reservation modeli olmadigi icin null kalir (hotel
 * hold/reservation modelleri planli degil).
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vertical", nullable = false, length = 20)
    private OrderItemVertical vertical;

    @Column(name = "supplier_organization_id")
    private UUID supplierOrganizationId;

    @Column(name = "product_ref", nullable = false)
    private UUID productRef;

    @Column(name = "fulfillment_ref")
    private UUID fulfillmentRef;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "unit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitAmount;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "bpchar(3)")
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OrderItem() {}

    public BigDecimal getSubtotal() {
        return unitAmount.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public OrderItemVertical getVertical() { return vertical; }
    public void setVertical(OrderItemVertical vertical) { this.vertical = vertical; }

    public UUID getSupplierOrganizationId() { return supplierOrganizationId; }
    public void setSupplierOrganizationId(UUID supplierOrganizationId) { this.supplierOrganizationId = supplierOrganizationId; }

    public UUID getProductRef() { return productRef; }
    public void setProductRef(UUID productRef) { this.productRef = productRef; }

    public UUID getFulfillmentRef() { return fulfillmentRef; }
    public void setFulfillmentRef(UUID fulfillmentRef) { this.fulfillmentRef = fulfillmentRef; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getUnitAmount() { return unitAmount; }
    public void setUnitAmount(BigDecimal unitAmount) { this.unitAmount = unitAmount; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getCreatedAt() { return createdAt; }
}
