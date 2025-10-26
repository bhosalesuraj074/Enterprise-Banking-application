package com.key.deposite.exception;

public class InvalidAccountBalanceException extends RuntimeException {
    public InvalidAccountBalanceException(String message) {
        super(message);
    }
}
