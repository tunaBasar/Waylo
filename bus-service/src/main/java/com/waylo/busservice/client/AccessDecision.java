package com.waylo.busservice.client;

import java.util.List;

public record AccessDecision(boolean allowed, List<String> roles) {
}
