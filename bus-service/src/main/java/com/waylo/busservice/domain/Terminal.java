package com.waylo.busservice.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "terminals")
public class Terminal extends Audited {
    @Id public UUID id;
    @Column(nullable = false) public String city;
    @Column(nullable = false) public String name;
    @Column(nullable = false) public boolean active = true;
    @Version public Long version;
}
