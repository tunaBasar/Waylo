package com.waylo.packagebookingservice.domain;

/**
 * Siparis kaleminin ait oldugu dikey. v1 yalniz HOTEL fonksiyonel olarak desteklenir;
 * FLIGHT ve CAR modelde yer tutar, PRODUCT-ARCHITECTURE.md SS1 "dikeyler ayri" ilkesiyle uyumlu.
 */
public enum OrderItemVertical {
    HOTEL,
    FLIGHT,
    CAR
}
