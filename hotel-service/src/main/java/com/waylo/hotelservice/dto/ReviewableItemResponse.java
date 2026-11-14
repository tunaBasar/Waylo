package com.waylo.hotelservice.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ReviewableItemResponse(UUID orderItemId, UUID hotelId, String hotelName, String description,
                                     LocalDate checkIn, LocalDate checkOut) {}
