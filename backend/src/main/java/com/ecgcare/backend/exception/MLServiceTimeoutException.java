package com.ecgcare.backend.exception;

public class MLServiceTimeoutException extends MLServiceException {
    public MLServiceTimeoutException(String message) {
        super(message);
    }

    public MLServiceTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}




