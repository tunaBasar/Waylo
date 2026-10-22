package com.waylo.hotelservice.service;

import com.waylo.hotelservice.dto.QuoteCreateRequest;
import com.waylo.hotelservice.dto.QuoteResponse;

import java.util.UUID;

/**
 * Quote management - create and retrieve hotel quotes.
 * Ownership enforced via principalUserId.
 */
public interface QuoteService {

    /**
     * Create a new quote snapshot (15-minute contract) for the specified stay.
     */
    QuoteResponse createQuote(QuoteCreateRequest request, UUID principalUserId);

    /**
     * Retrieve a quote by id. Foreign quotes are hidden as 404.
     */
    QuoteResponse getQuote(UUID quoteId, UUID principalUserId);
}
