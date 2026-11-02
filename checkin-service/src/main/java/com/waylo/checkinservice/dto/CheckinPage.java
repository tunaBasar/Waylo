package com.waylo.checkinservice.dto;

import java.util.List;

public record CheckinPage(List<OrderCheckinResponse> items, int page, int size, long total) {}
