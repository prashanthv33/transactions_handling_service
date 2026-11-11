package com.fde.txn.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;

/** Safe error handling: callers get a stable error code and a reference id, never stack traces or internals. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> malformed(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "MALFORMED_REQUEST",
                "message", "Request body must be a JSON object or array of transactions"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception e) {
        if (e instanceof ErrorResponse framework) {
            // Standard MVC errors (404 unknown path, 405 wrong method, ...) keep their status, without details.
            return ResponseEntity.status(framework.getStatusCode()).body(Map.of(
                    "error", "REQUEST_ERROR", "message", "Request could not be handled"));
        }
        String reference = UUID.randomUUID().toString();
        log.error("event=api.error reference={} error={}", reference, e.getClass().getSimpleName(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "Unexpected error",
                "reference", reference));
    }
}
