package com.example.analyzeproject.application.exception;

/** A bounded workload rejected an overlapping request; the caller may retry later. */
public final class ServiceBusyException extends RuntimeException {
    public ServiceBusyException(String message){super(message);}
}
