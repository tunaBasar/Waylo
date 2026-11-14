package com.waylo.notificationservice.service;

import java.util.List;
import java.util.Set;

/** Push provider abstraction (Expo today). */
public interface PushSender {
    /** False when the provider is switched off: nothing is sent and the notification is recorded SUPPRESSED. */
    boolean isEnabled();

    /** Sends to all tokens. Returns tokens the provider reported as no longer valid. Throws on transport failure. */
    Set<String> send(List<String> tokens, String title, String body);
}
