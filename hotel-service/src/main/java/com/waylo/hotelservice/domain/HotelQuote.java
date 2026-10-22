package com.waylo.hotelservice.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "hotel_quotes")
public class HotelQuote {

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

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "rooms", nullable = false)
    private int rooms;

    @Column(name = "adults", nullable = false)
    private int adults;

    @Column(name = "children_ages", nullable = false, length = 100)
    private String childrenAges;

    @Column(name = "occupancy", nullable = false)
    private int occupancy;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "bpchar(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_plan", nullable = false, length = 20)
    private MealPlan mealPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_policy", nullable = false, length = 30)
    private CancellationPolicy cancellationPolicy;

    @Column(name = "free_cancellation_days_before")
    private Integer freeCancellationDaysBefore;

    @Column(name = "room_type_code", nullable = false, length = 30)
    private String roomTypeCode;

    @Column(name = "room_type_name", nullable = false, length = 120)
    private String roomTypeName;

    @Column(name = "rate_plan_code", nullable = false, length = 30)
    private String ratePlanCode;

    @Column(name = "rate_plan_name", nullable = false, length = 120)
    private String ratePlanName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "principal_user_id", nullable = false, updatable = false)
    private UUID principalUserId;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stayDate ASC")
    private List<HotelQuoteNight> nights = new ArrayList<>();

    public HotelQuote() {}

    // --- Getters & Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getHotelId() { return hotelId; }
    public void setHotelId(UUID hotelId) { this.hotelId = hotelId; }

    public UUID getRoomTypeId() { return roomTypeId; }
    public void setRoomTypeId(UUID roomTypeId) { this.roomTypeId = roomTypeId; }

    public UUID getRatePlanId() { return ratePlanId; }
    public void setRatePlanId(UUID ratePlanId) { this.ratePlanId = ratePlanId; }

    public LocalDate getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDate checkIn) { this.checkIn = checkIn; }

    public LocalDate getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDate checkOut) { this.checkOut = checkOut; }

    public int getRooms() { return rooms; }
    public void setRooms(int rooms) { this.rooms = rooms; }

    public int getAdults() { return adults; }
    public void setAdults(int adults) { this.adults = adults; }

    public String getChildrenAges() { return childrenAges; }
    public void setChildrenAges(String childrenAges) { this.childrenAges = childrenAges; }

    public int getOccupancy() { return occupancy; }
    public void setOccupancy(int occupancy) { this.occupancy = occupancy; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public MealPlan getMealPlan() { return mealPlan; }
    public void setMealPlan(MealPlan mealPlan) { this.mealPlan = mealPlan; }

    public CancellationPolicy getCancellationPolicy() { return cancellationPolicy; }
    public void setCancellationPolicy(CancellationPolicy cancellationPolicy) { this.cancellationPolicy = cancellationPolicy; }

    public Integer getFreeCancellationDaysBefore() { return freeCancellationDaysBefore; }
    public void setFreeCancellationDaysBefore(Integer freeCancellationDaysBefore) { this.freeCancellationDaysBefore = freeCancellationDaysBefore; }

    public String getRoomTypeCode() { return roomTypeCode; }
    public void setRoomTypeCode(String roomTypeCode) { this.roomTypeCode = roomTypeCode; }

    public String getRoomTypeName() { return roomTypeName; }
    public void setRoomTypeName(String roomTypeName) { this.roomTypeName = roomTypeName; }

    public String getRatePlanCode() { return ratePlanCode; }
    public void setRatePlanCode(String ratePlanCode) { this.ratePlanCode = ratePlanCode; }

    public String getRatePlanName() { return ratePlanName; }
    public void setRatePlanName(String ratePlanName) { this.ratePlanName = ratePlanName; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public UUID getPrincipalUserId() { return principalUserId; }
    public void setPrincipalUserId(UUID principalUserId) { this.principalUserId = principalUserId; }

    public List<HotelQuoteNight> getNights() { return nights; }
    public void setNights(List<HotelQuoteNight> nights) { this.nights = nights; }

    /**
     * Number of nights in the stay: checkOut - checkIn (days). Checkout night excluded.
     */
    public int getNightCount() {
        return (int) ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    /**
     * Expiry is inclusive: expiresAt == now means expired.
     */
    public boolean isExpired(Clock clock) {
        return !Instant.now(clock).isBefore(expiresAt);
    }

    /**
     * Adds a night to this quote, setting the back-reference on the night.
     */
    public void addNight(HotelQuoteNight night) {
        night.setQuote(this);
        this.nights.add(night);
    }

    /**
     * Encodes a list of children ages into a comma-joined CSV string.
     * Empty/null list -> "".
     */
    public static String encodeChildrenAges(List<Integer> ages) {
        if (ages == null || ages.isEmpty()) {
            return "";
        }
        return ages.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * Decodes a CSV string of children ages into a list of ints.
     * null/blank -> empty immutable list.
     */
    public static List<Integer> decodeChildrenAges(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(
                java.util.Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Integer::parseInt)
                        .collect(Collectors.toList()));
    }
}
