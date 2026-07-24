package com.ecgcare.backend.dto.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void successWithDataOnly() {
        ApiResponse<String> response = ApiResponse.success("payload");

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData()).isEqualTo("payload");
        assertThat(response.getMessage()).isNull();
        assertThat(response.getError()).isNull();
    }

    @Test
    void successWithMessageAndData() {
        ApiResponse<Integer> response = ApiResponse.success("done", 42);

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("done");
        assertThat(response.getData()).isEqualTo(42);
    }

    @Test
    void errorCarriesCodeAndMessage() {
        ApiResponse<Object> response = ApiResponse.error("NOT_FOUND", "missing thing");

        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getData()).isNull();
        assertThat(response.getError().getCode()).isEqualTo("NOT_FOUND");
        assertThat(response.getError().getMessage()).isEqualTo("missing thing");
    }

    @Test
    void toBuilderPreservesExistingFields() {
        ApiResponse<Object> response = ApiResponse.error("FORBIDDEN", "nope")
                .toBuilder()
                .timestamp("2026-01-01T00:00:00Z")
                .path("/api/x")
                .build();

        assertThat(response.getError().getCode()).isEqualTo("FORBIDDEN");
        assertThat(response.getTimestamp()).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(response.getPath()).isEqualTo("/api/x");
    }
}
