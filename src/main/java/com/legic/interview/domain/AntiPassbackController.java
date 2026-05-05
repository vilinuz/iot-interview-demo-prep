package com.legic.interview.domain;

import java.util.concurrent.ConcurrentHashMap;

public class AntiPassbackController {
    private final ConcurrentHashMap<String, Integer> userLocation = new ConcurrentHashMap<>();

    public boolean canEnter(String userId, int zoneId) {
        // Atomic update: only allow entry if the user is not already in a zone
        return userLocation.putIfAbsent(userId, zoneId) == null;
    }

    public void exit(String userId) {
        userLocation.remove(userId);
    }
}
