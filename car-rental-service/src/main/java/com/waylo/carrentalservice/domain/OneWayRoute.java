package com.waylo.carrentalservice.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "one_way_routes")
public class OneWayRoute extends Audited {
    @Id public UUID id;
    @Column(name = "organization_id", nullable = false) public UUID organizationId;
    @Column(name = "from_branch_id", nullable = false) public UUID fromBranchId;
    @Column(name = "to_branch_id", nullable = false) public UUID toBranchId;
    @Column(nullable = false) public BigDecimal fee;
    @Column(nullable = false) public String currency;
}
