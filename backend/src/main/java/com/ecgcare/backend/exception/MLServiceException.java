package com.ecgcare.backend.exception;

public class MLServiceException extends RuntimeException {
    public MLServiceException(String message) {
        super(message);
    }

    public MLServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}




