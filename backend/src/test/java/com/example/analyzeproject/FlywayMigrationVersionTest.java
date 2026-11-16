package com.example.analyzeproject;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FlywayMigrationVersionTest {
    @Test void migrationVersionsAreUnique() throws Exception {
        Map<String,String> versions = new HashMap<>();
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith("V")).toList()) {
                String name = file.getFileName().toString();
                assertTrue(name.matches("V[0-9]+__[A-Za-z0-9_]+\\.sql"), name);
                String version = name.substring(1, name.indexOf("__"));
                assertNull(versions.putIfAbsent(version, name), "Duplicate Flyway version: " + name);
            }
        }
    }
}
