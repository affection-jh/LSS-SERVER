package com.eos.lss.exception;
 
public class PlayerAlreadyJoinedException extends GameException {
    public PlayerAlreadyJoinedException(String message) {
        super(message, 409);
    }
} 