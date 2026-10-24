package com.waylo.packagebookingservice.dto;

import java.util.List;

public record PartnerOrderItemPage(List<PartnerOrderItemResponse> items, int page, int size, long total) {}
