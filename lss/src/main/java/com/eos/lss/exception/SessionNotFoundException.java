package com.eos.lss.exception;
 
public class SessionNotFoundException extends GameException {
    public SessionNotFoundException(String message) {
        super(message, 404);
    }
} 