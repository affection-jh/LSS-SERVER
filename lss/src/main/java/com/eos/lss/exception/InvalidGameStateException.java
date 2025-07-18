package com.eos.lss.exception;
 
public class InvalidGameStateException extends GameException {
    public InvalidGameStateException(String message) {
        super(message, 400);
    }
} 