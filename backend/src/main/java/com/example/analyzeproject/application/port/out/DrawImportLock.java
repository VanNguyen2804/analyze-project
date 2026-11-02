package com.example.analyzeproject.application.port.out;

/** Acquires a writer lock for the enclosing import transaction. */
@FunctionalInterface
public interface DrawImportLock {
    void acquire();
}
