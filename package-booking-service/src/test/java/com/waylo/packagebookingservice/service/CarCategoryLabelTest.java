package com.waylo.packagebookingservice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CarCategoryLabelTest {

    @Test
    void mapsEveryCarRentalCategoryToTurkish() {
        assertThat(OrderServiceImpl.carCategoryLabel("ECONOMY")).isEqualTo("Ekonomi");
        assertThat(OrderServiceImpl.carCategoryLabel("COMPACT")).isEqualTo("Kompakt");
        assertThat(OrderServiceImpl.carCategoryLabel("MIDSIZE")).isEqualTo("Orta");
        assertThat(OrderServiceImpl.carCategoryLabel("FULLSIZE")).isEqualTo("Buyuk");
        assertThat(OrderServiceImpl.carCategoryLabel("SUV")).isEqualTo("SUV");
        assertThat(OrderServiceImpl.carCategoryLabel("MINIVAN")).isEqualTo("Minivan");
        assertThat(OrderServiceImpl.carCategoryLabel("VAN")).isEqualTo("Van");
        assertThat(OrderServiceImpl.carCategoryLabel("LUXURY")).isEqualTo("Luks");
    }

    @Test
    void unknownCategoryFallsBackToRawValueAndNullToEmpty() {
        assertThat(OrderServiceImpl.carCategoryLabel("HOVERCRAFT")).isEqualTo("HOVERCRAFT");
        assertThat(OrderServiceImpl.carCategoryLabel(null)).isEmpty();
    }
}
