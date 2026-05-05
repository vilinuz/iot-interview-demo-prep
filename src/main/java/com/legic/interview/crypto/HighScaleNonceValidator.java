package com.legic.interview.crypto;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import java.nio.charset.Charset;

public class HighScaleNonceValidator {
    private final BloomFilter<String> localNonceFilter = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), 10_000_000, 0.01);
    private final RedisClient redisClient;

    public HighScaleNonceValidator(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    public boolean isNonceValid(String credentialId, String nonce) {
        String cacheKey = credentialId + ":" + nonce;

        // 1. FAST PATH: Check the local Bloom Filter (Zero network I/O)
        if (!localNonceFilter.mightContain(cacheKey)) {
            localNonceFilter.put(cacheKey);
            // Asynchronously record it in Redis so other pods know about it.
            redisClient.setAsync(cacheKey, "used", 30);
            return true; // Valid!
        }

        // 2. SLOW PATH: The Bloom Filter returned TRUE (possible replay or false positive)
        return redisClient.setIfAbsent(cacheKey, "used", 30);
    }
}
