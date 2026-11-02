package com.example.analyzeproject.architecture;

import org.junit.jupiter.api.Test;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Checks compiled references, including annotations and method signatures, without a new dependency. */
class OnionArchitectureTest {
    private static final String BASE = "com/example/analyzeproject/";

    @Test void dependenciesPointInward() throws Exception {
        Path classes = Path.of(OnionArchitectureTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolveSibling("classes").resolve(BASE);
        check(classes.resolve("domain"), List.of(BASE + "application", BASE + "infrastructure", BASE + "presentation",
                "org/springframework", "jakarta/", "com/fasterxml", "org/hibernate", "java/sql", "javax/sql"));
        check(classes.resolve("application"), List.of(BASE + "infrastructure", BASE + "presentation",
                "org/springframework", "jakarta/persistence", "java/sql", "javax/sql"));
        check(classes.resolve("presentation"), List.of(BASE + "infrastructure"));
        check(classes.resolve("infrastructure"), List.of(BASE + "presentation"));
    }

    private void check(Path layer, List<String> forbidden) throws Exception {
        try (var files = Files.walk(layer)) {
            var classes = files.filter(p -> p.toString().endsWith(".class")).toList();
            assertFalse(classes.isEmpty(), "Missing compiled layer: " + layer);
            for (var file : classes) for (var reference : constants(file)) for (var dependency : forbidden)
                assertFalse(reference.replace('.', '/').contains(dependency), file + " depends on " + reference);
        }
    }

    private List<String> constants(Path file) throws IOException {
        try (var input = new DataInputStream(Files.newInputStream(file))) {
            assertEquals(0xCAFEBABE, input.readInt());
            input.skipNBytes(4);
            int count = input.readUnsignedShort();
            List<String> values = new ArrayList<>();
            for (int i = 1; i < count; i++) {
                switch (input.readUnsignedByte()) {
                    case 1 -> values.add(input.readUTF());
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
                    case 5, 6 -> { input.skipNBytes(8); i++; }
                    case 7, 8, 16, 19, 20 -> input.skipNBytes(2);
                    case 15 -> input.skipNBytes(3);
                    default -> throw new IOException("Unknown class constant in " + file);
                }
            }
            return values;
        }
    }
}
