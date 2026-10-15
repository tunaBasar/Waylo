package com.waylo.authservice.dto;

import java.util.List;

/** Read-only access-decision response returned by the internal access-check endpoint. */
public record AccessDecision(boolean allowed, List<String> roles) {}
