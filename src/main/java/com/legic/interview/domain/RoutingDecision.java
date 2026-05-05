package com.legic.interview.domain;

import java.time.Instant;

public record RoutingDecision(String exchange, String routingKey, int priority, boolean alert) {}

// ── Event processor ───────────────────────────────────────────────────────────
