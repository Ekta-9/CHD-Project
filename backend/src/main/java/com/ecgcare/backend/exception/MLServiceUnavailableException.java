package com.ecgcare.backend.exception;

public class MLServiceUnavailableException extends MLServiceException {
    public MLServiceUnavailableException(String message) {
        super(message);
    }

    public MLServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}




