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
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Column(name = "check_in")
    private LocalDate checkIn;

    @Column(name = "check_out")
    private LocalDate checkOut;

    @Column(name = "pickup_at") private LocalDateTime pickupAt;
    @Column(name = "return_at") private LocalDateTime returnAt;

    @Column(name = "flight_number", length = 10) private String flightNumber;
    @Column(name = "departure_at") private Instant departureAt;
    @Column(name = "seat_number", length = 5) private String seatNumber;
    @Column(name = "passenger_first_name", length = 80) private String passengerFirstName;
    @Column(name = "passenger_last_name", length = 80) private String passengerLastName;
    @Column(name = "pnr", length = 8) private String pnr;
    @Column(name = "ticket_number", length = 20) private String ticketNumber;
    @Column(name = "supplier", length = 20) private String supplier;
    @Column(name = "supplier_order_ref", length = 100) private String supplierOrderRef;

    @Column(name = "gross_amount", precision = 12, scale = 2) private BigDecimal grossAmount;
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2) private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(name = "discount_funded_by", length = 20) private String discountFundedBy;
    @Column(name = "net_amount", precision = 12, scale = 2) private BigDecimal netAmount;
    @Column(name = "tax_amount", precision = 12, scale = 2) private BigDecimal taxAmount;
    @Column(name = "promo_code", length = 50) private String promoCode;

    /** Policy snapshot taken at order creation, never re-read (CANCELLATION-CONTRACT.md). */
    @Column(name = "cancellation_policy", nullable = false, length = 20) private String cancellationPolicy = "FREE_CANCELLATION";
    @Column(name = "cancel_free_hours_before") private Integer cancelFreeHoursBefore;
    /** REFUND | NO_REFUND | NO_SHOW once the item left escrow through cancellation or the no-show job. */
    @Column(name = "cancel_outcome", length = 20) private String cancelOutcome;
    @Column(name = "refund_amount", precision = 12, scale = 2) private BigDecimal refundAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OrderItem() {}

    public BigDecimal getSubtotal() {
        return unitAmount.multiply(BigDecimal.valueOf(quantity));
    }

    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal v) { this.grossAmount = v; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal v) { this.discountAmount = v; }
    public String getDiscountFundedBy() { return discountFundedBy; }
    public void setDiscountFundedBy(String v) { this.discountFundedBy = v; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal v) { this.netAmount = v; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal v) { this.taxAmount = v; }
    public String getPromoCode() { return promoCode; }
    public void setPromoCode(String v) { this.promoCode = v; }

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

    public LocalDate getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDate checkIn) { this.checkIn = checkIn; }

    public LocalDate getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDate checkOut) { this.checkOut = checkOut; }
    public LocalDateTime getPickupAt() { return pickupAt; }
    public void setPickupAt(LocalDateTime pickupAt) { this.pickupAt = pickupAt; }
    public LocalDateTime getReturnAt() { return returnAt; }
    public void setReturnAt(LocalDateTime returnAt) { this.returnAt = returnAt; }

    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String v) { this.flightNumber = v; }
    public Instant getDepartureAt() { return departureAt; }
    public void setDepartureAt(Instant v) { this.departureAt = v; }
    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String v) { this.seatNumber = v; }
    public String getPassengerFirstName() { return passengerFirstName; }
    public void setPassengerFirstName(String v) { this.passengerFirstName = v; }
    public String getPassengerLastName() { return passengerLastName; }
    public void setPassengerLastName(String v) { this.passengerLastName = v; }
    public String getPnr() { return pnr; }
    public void setPnr(String v) { this.pnr = v; }
    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String v) { this.ticketNumber = v; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String v) { this.supplier = v; }
    public String getSupplierOrderRef() { return supplierOrderRef; }
    public void setSupplierOrderRef(String v) { this.supplierOrderRef = v; }

    public String getCancellationPolicy() { return cancellationPolicy; }
    public void setCancellationPolicy(String v) { this.cancellationPolicy = v; }
    public Integer getCancelFreeHoursBefore() { return cancelFreeHoursBefore; }
    public void setCancelFreeHoursBefore(Integer v) { this.cancelFreeHoursBefore = v; }
    public String getCancelOutcome() { return cancelOutcome; }
    public void setCancelOutcome(String v) { this.cancelOutcome = v; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal v) { this.refundAmount = v; }

    public Instant getCreatedAt() { return createdAt; }
}
