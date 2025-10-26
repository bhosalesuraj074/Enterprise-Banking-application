package com.key.deposite.exception;

import com.key.deposite.dto.DepositResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<DepositResponse> handleAccountNotFound(AccountNotFoundException e) {
        DepositResponse response = new DepositResponse();
        response.setMessage(e.getMessage());
        response.setNewBalance(null);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(InvalidAccountBalanceException.class)
    public ResponseEntity<DepositResponse> handleInvalidBalance(InvalidAccountBalanceException e) {
        DepositResponse response = new DepositResponse();
        response.setMessage(e.getMessage());
        response.setNewBalance(null);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<DepositResponse> handleRuntime(RuntimeException e) {
        DepositResponse response = new DepositResponse();
        response.setMessage("Deposit operation failed: " + e.getMessage());
        response.setNewBalance(null);
        return ResponseEntity.internalServerError().body(response);
    }
}
