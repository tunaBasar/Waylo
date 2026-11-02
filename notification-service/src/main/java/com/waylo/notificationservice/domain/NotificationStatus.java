package com.waylo.notificationservice.domain;

public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    RETRYING,
    /** Not delivered on purpose: reserved/test domain or invalid address (dev and test users). */
    SUPPRESSED
}
