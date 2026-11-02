package com.waylo.busservice.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "buses")
public class Bus extends Audited {
    @Id public UUID id;
    @Column(name = "organization_id", nullable = false) public UUID organizationId;
    @Column(name = "plate_number", nullable = false) public String plateNumber;
    @Column(nullable = false) public String brand;
    @Column(nullable = false) public String model;
    @Column(nullable = false) public int year;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public SeatLayout layout;
    @Column(name = "row_count", nullable = false) public int rowCount;
    @Column(name = "seat_count", nullable = false) public int seatCount;
    @Column(nullable = false) public boolean active = true;
    @Version public Long version;
}
