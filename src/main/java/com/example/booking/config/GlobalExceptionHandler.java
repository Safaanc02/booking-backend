package com.example.booking.config;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // ✅ Gestion validation @Valid
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, Object> body = base("Validation failed", HttpStatus.BAD_REQUEST.value());
        Map<String, String> errors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    // ✅ Mauvaise méthode HTTP (ex: POST sur un GET)
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(base(ex.getMessage(), HttpStatus.METHOD_NOT_ALLOWED.value()));
    }

    // ✅ Ressource introuvable
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Object> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(base(ex.getMessage(), HttpStatus.NOT_FOUND.value()));
    }

    // ✅ Règle métier violée
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleBusiness(IllegalStateException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(base(ex.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY.value()));
    }

    // ✅ Contrainte violée
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
                .body(base(ex.getMessage(), HttpStatus.BAD_REQUEST.value()));
    }

    // ✅ Accès interdit
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccess(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(base("Accès refusé", HttpStatus.FORBIDDEN.value()));
    }

    // ✅ Fallback générique
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleOther(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(base("Erreur interne: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    // 🛠 Utilitaire pour formater la réponse JSON
    private Map<String, Object> base(String message, int status) {
        Map<String, Object> m = new HashMap<>();
        m.put("timestamp", Instant.now().toString());
        m.put("status", status);
        m.put("message", message);
        return m;
    }
}
