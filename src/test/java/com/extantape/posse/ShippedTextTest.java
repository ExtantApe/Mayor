package com.extantape.posse;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** No old plugin name in anything a player or admin can read (D295, D292). */
class ShippedTextTest {
    @Test
    void shippedFilesNameNoOldPlugin() throws Exception {
        for (String file : new String[] {"config.yml", "filter.yml", "quips.yml", "paper-plugin.yml"}) {
            try (var in = ShippedTextTest.class.getResourceAsStream("/" + file)) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
                assertFalse(text.contains("houserules"), file);
                assertFalse(text.contains("mayor"), file);
            }
        }
    }
}
