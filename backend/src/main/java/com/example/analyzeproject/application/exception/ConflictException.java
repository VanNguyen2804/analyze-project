package com.example.analyzeproject.application.exception;

/** A stale revision, changed source or closed commitment window. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
