package com.ecgcare.backend.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonMapConverterTest {

    private JsonMapConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JsonMapConverter();
    }

    @Test
    void roundTripPreservesMapContents() {
        Map<String, Object> original = new HashMap<>();
        original.put("kdf", "PBKDF2WithHmacSHA256");
        original.put("kdfIterations", 210000);

        String json = converter.convertToDatabaseColumn(original);
        Map<String, Object> restored = converter.convertToEntityAttribute(json);

        assertThat(restored).containsEntry("kdf", "PBKDF2WithHmacSHA256")
                .containsEntry("kdfIterations", 210000);
    }

    @Test
    void nullMapConvertsToNullColumn() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void nullOrEmptyColumnConvertsToEmptyMap() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
    }

    @Test
    void invalidJsonThrowsRuntimeException() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("{not valid json"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error converting JSON string to Map");
    }
}
