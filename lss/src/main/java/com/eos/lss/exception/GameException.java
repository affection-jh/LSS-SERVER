package com.eos.lss.exception;

public class GameException extends RuntimeException {
    private final int statusCode;
    
    public GameException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
    
    public GameException(String message) {
        this(message, 400);
    }
    
    public int getStatusCode() {
        return statusCode;
    }
} 