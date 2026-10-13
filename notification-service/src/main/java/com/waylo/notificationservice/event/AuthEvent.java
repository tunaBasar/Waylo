package com.waylo.notificationservice.event;

import java.util.UUID;

public class AuthEvent extends BaseEvent {
    private String eventType; // VERIFICATION, PASSWORD_RESET, WELCOME
    private UUID userId;
    private String email;
    private String name;
    private String token;

    public AuthEvent() {}

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
