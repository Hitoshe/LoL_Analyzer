package com.lol.analyzer.riot;

public class PlayerNotFoundException extends RuntimeException {

    public PlayerNotFoundException(String userMessage) {
        super(userMessage);
    }
}
