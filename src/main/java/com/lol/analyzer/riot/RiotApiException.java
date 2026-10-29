package com.lol.analyzer.riot;

/**
 * Riot API failure with a message that can be shown to the user as is (expired key, rate limit, outage).
 */
public class RiotApiException extends RuntimeException {

    private final int status;

    public RiotApiException(int status, String userMessage) {
        super(userMessage);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
