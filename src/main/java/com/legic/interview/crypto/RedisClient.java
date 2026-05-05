package com.legic.interview.crypto;

public interface RedisClient {
    void setAsync(String key, String value, int expirationSeconds);
    boolean setIfAbsent(String key, String value, int expirationSeconds);
}
