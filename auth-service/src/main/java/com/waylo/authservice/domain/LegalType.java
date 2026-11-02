package com.waylo.authservice.domain;

/**
 * Legal nature of the organization.
 * Existing (pre-migration) rows default to COMPANY.
 */
public enum LegalType {
    COMPANY,
    INDIVIDUAL
}
