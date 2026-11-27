package com.waylo.hotelservice.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "hotel_rate_rules")
public class HotelRateRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "hotel_id", nullable = false, updatable = false)
    private UUID hotelId;

    @Column(name = "room_type_id", nullable = false, updatable = false)
    private UUID roomTypeId;

    @Column(name = "rate_plan_id", nullable = false, updatable = false)
    private UUID ratePlanId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "bpchar(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(name = "base_occupancy", nullable = false)
    private int baseOccupancy;

    @Column(name = "base_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "weekend_base_amount", precision = 12, scale = 2)
    private BigDecimal weekendBaseAmount;

    @Column(name = "extra_person_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal extraPersonAmount = BigDecimal.ZERO;

    @Column(name = "reduced_person_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal reducedPersonAmount = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public HotelRateRule() {}

    public boolean isSeason() { return validFrom != null && validTo != null; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHotelId() { return hotelId; }
    public void setHotelId(UUID hotelId) { this.hotelId = hotelId; }
    public UUID getRoomTypeId() { return roomTypeId; }
    public void setRoomTypeId(UUID roomTypeId) { this.roomTypeId = roomTypeId; }
    public UUID getRatePlanId() { return ratePlanId; }
    public void setRatePlanId(UUID ratePlanId) { this.ratePlanId = ratePlanId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public int getBaseOccupancy() { return baseOccupancy; }
    public void setBaseOccupancy(int baseOccupancy) { this.baseOccupancy = baseOccupancy; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public void setBaseAmount(BigDecimal baseAmount) { this.baseAmount = baseAmount; }
    public BigDecimal getWeekendBaseAmount() { return weekendBaseAmount; }
    public void setWeekendBaseAmount(BigDecimal weekendBaseAmount) { this.weekendBaseAmount = weekendBaseAmount; }
    public BigDecimal getExtraPersonAmount() { return extraPersonAmount; }
    public void setExtraPersonAmount(BigDecimal extraPersonAmount) { this.extraPersonAmount = extraPersonAmount; }
    public BigDecimal getReducedPersonAmount() { return reducedPersonAmount; }
    public void setReducedPersonAmount(BigDecimal reducedPersonAmount) { this.reducedPersonAmount = reducedPersonAmount; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
