package com.vone.mq.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {
    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MILLIS = 15 * 60 * 1000L;
    private static final long LOCK_MILLIS = 60 * 1000L;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isAllowed(String key, long now) {
        Attempt attempt = attempts.get(key);
        if (attempt == null || now - attempt.windowStart >= WINDOW_MILLIS) {
            return true;
        }
        return attempt.lockedUntil <= now;
    }

    public void recordFailure(String key, long now) {
        attempts.compute(key, (ignored, current) -> {
            Attempt attempt = current;
            if (attempt == null || now - attempt.windowStart >= WINDOW_MILLIS) {
                attempt = new Attempt(now);
            }
            attempt.failures++;
            if (attempt.failures >= MAX_FAILURES) {
                attempt.lockedUntil = now + LOCK_MILLIS;
            }
            return attempt;
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private static final class Attempt {
        private final long windowStart;
        private int failures;
        private long lockedUntil;

        private Attempt(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
