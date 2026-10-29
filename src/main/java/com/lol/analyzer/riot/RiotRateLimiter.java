package com.lol.analyzer.riot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Sliding-window limiter for one Riot routing host. Riot enforces application limits per host
 * (development key: 20 requests / 1 s and 100 requests / 120 s), so every window must have room before a call.
 */
public class RiotRateLimiter {

    private final List<Window> windows;

    public RiotRateLimiter(List<Window> windows) {
        this.windows = List.copyOf(windows);
    }

    /** Spec format: {@code "20:1,100:120"} = 20 requests per 1 s and 100 requests per 120 s. */
    public static RiotRateLimiter fromSpec(String spec) {
        List<Window> parsed = new ArrayList<>();
        for (String part : spec.split(",")) {
            String[] pair = part.trim().split(":");
            if (pair.length == 2) {
                parsed.add(new Window(Integer.parseInt(pair[0].trim()), Long.parseLong(pair[1].trim()) * 1000L));
            }
        }
        return new RiotRateLimiter(parsed);
    }

    public synchronized void acquire() throws InterruptedException {
        while (true) {
            long now = System.currentTimeMillis();
            long waitMs = 0;
            for (Window w : windows) {
                waitMs = Math.max(waitMs, w.waitMs(now));
            }
            if (waitMs <= 0) {
                for (Window w : windows) {
                    w.record(now);
                }
                return;
            }
            wait(waitMs);
        }
    }

    public static final class Window {
        private final int limit;
        private final long durationMs;
        private final Deque<Long> calls = new ArrayDeque<>();

        public Window(int limit, long durationMs) {
            this.limit = limit;
            this.durationMs = durationMs;
        }

        long waitMs(long now) {
            while (!calls.isEmpty() && calls.peekFirst() + durationMs <= now) {
                calls.pollFirst();
            }
            return calls.size() < limit ? 0 : calls.peekFirst() + durationMs - now + 1;
        }

        void record(long now) {
            calls.addLast(now);
        }
    }
}
