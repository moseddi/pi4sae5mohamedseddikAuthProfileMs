package tn.esprit.authservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String STATUS_FIELD = "status";
    private static final String ERROR_FIELD = "error";

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        String message = ex.getMessage();

        error.put(ERROR_FIELD, message);
        error.put(STATUS_FIELD, String.valueOf(HttpStatus.BAD_REQUEST.value()));

        if (message != null) {
            if (message.contains("Email already exists")) {
                error.put(STATUS_FIELD, String.valueOf(HttpStatus.CONFLICT.value()));
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
            }
            if (message.contains("User not found")) {
                error.put(STATUS_FIELD, String.valueOf(HttpStatus.NOT_FOUND.value()));
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }
            if (message.contains("blocked")) {
                error.put(STATUS_FIELD, String.valueOf(HttpStatus.FORBIDDEN.value()));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }
            if (message.contains("Invalid credentials") || message.contains("Failed to get token")) {
                error.put(STATUS_FIELD, String.valueOf(HttpStatus.UNAUTHORIZED.value()));
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }
            if (message.contains("Keycloak")) {
                error.put(STATUS_FIELD, String.valueOf(HttpStatus.SERVICE_UNAVAILABLE.value()));
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
            }
        }

        error.put(STATUS_FIELD, String.valueOf(HttpStatus.BAD_REQUEST.value()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put(ERROR_FIELD, "An error occurred: " + ex.getMessage());
        error.put(STATUS_FIELD, String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}