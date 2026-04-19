package com.smartgrocery.backend.exception;

public class OrderAssignmentConflictException extends RuntimeException {
    public OrderAssignmentConflictException(String message) {
        super(message);
    }
}

