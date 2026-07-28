package com.ecgcare.backend.exception;

import com.ecgcare.backend.dto.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("GET", "/api/test");
    }

    @Test
    void badRequestMapsTo400() {
        ResponseEntity<ApiResponse<?>> response = handler.handleBadRequest(
                new BadRequestException("bad input"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError().getCode()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("bad input");
        assertThat(response.getBody().getPath()).isEqualTo("/api/test");
        assertThat(response.getBody().getTimestamp()).isNotBlank();
    }

    @Test
    void unauthorizedMapsTo401() {
        ResponseEntity<ApiResponse<?>> response = handler.handleUnauthorized(
                new UnauthorizedException("no token"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getError().getCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void forbiddenMapsTo403() {
        ResponseEntity<ApiResponse<?>> response = handler.handleForbidden(
                new ForbiddenException("not yours"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getError().getCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void notFoundMapsTo404() {
        ResponseEntity<ApiResponse<?>> response = handler.handleNotFound(
                new NotFoundException("missing"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getError().getCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void mlTimeoutMapsTo504() {
        ResponseEntity<ApiResponse<?>> response = handler.handleMLServiceTimeout(
                new MLServiceTimeoutException("slow"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody().getError().getCode()).isEqualTo("ML_SERVICE_TIMEOUT");
    }

    @Test
    void mlUnavailableMapsTo503() {
        ResponseEntity<ApiResponse<?>> response = handler.handleMLServiceUnavailable(
                new MLServiceUnavailableException("down"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getError().getCode()).isEqualTo("ML_SERVICE_UNAVAILABLE");
    }

    @Test
    void mlErrorMapsTo502() {
        ResponseEntity<ApiResponse<?>> response = handler.handleMLServiceException(
                new MLServiceException("weird reply"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getError().getCode()).isEqualTo("ML_SERVICE_ERROR");
    }

    @Test
    void runtimeExceptionMapsTo500WithMessage() {
        ResponseEntity<ApiResponse<?>> response = handler.handleRuntimeException(
                new RuntimeException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("boom");
    }

    @Test
    void runtimeExceptionWithoutMessageGetsDefaultText() {
        ResponseEntity<ApiResponse<?>> response = handler.handleRuntimeException(
                new RuntimeException(), request);

        assertThat(response.getBody().getError().getMessage())
                .isEqualTo("An unexpected error occurred");
    }

    @Test
    void checkedExceptionMapsTo500() {
        ResponseEntity<ApiResponse<?>> response = handler.handleGenericException(
                new Exception("checked failure"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError().getMessage()).isEqualTo("checked failure");
    }

    @Test
    void checkedExceptionWithoutMessageGetsDefaultText() {
        ResponseEntity<ApiResponse<?>> response = handler.handleGenericException(
                new Exception(), request);

        assertThat(response.getBody().getError().getMessage())
                .isEqualTo("An unexpected error occurred");
    }
}
