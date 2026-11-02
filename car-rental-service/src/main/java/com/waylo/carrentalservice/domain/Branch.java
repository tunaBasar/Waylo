package com.waylo.carrentalservice.domain;

import jakarta.persistence.*;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "branches")
public class Branch extends Audited {
    @Id public UUID id;
    @Column(name = "organization_id", nullable = false) public UUID organizationId;
    @Column(nullable = false) public String name;
    @Column(nullable = false) public String city;
    @Column(nullable = false) public String address;
    public String phone;
    @Column(name = "open_time", nullable = false) public LocalTime openTime;
    @Column(name = "close_time", nullable = false) public LocalTime closeTime;
    @Column(nullable = false) public boolean active = true;
    @Version public Long version;
}
