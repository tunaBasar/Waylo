package com.waylo.carrentalservice.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle extends Audited {
    @Id public UUID id;
    @Column(name = "organization_id", nullable = false) public UUID organizationId;
    @Column(name = "home_branch_id", nullable = false) public UUID homeBranchId;
    @Column(name = "plate_number", nullable = false) public String plateNumber;
    @Column(nullable = false) public String brand;
    @Column(nullable = false) public String model;
    @Column(nullable = false) public int year;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public CarCategory category;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public Transmission transmission;
    @Enumerated(EnumType.STRING) @Column(name = "fuel_type", nullable = false) public FuelType fuelType;
    @Column(name = "seat_count", nullable = false) public int seatCount;
    @Column(nullable = false) public boolean active = true;
    @Version public Long version;

    public String exampleModel() { return brand + " " + model + " veya benzeri"; }
}
