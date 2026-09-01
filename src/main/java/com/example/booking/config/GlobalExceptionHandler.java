package com.example.booking.config;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Gestionnaire d'erreurs unique de l'API.
 *
 * Le projet en comportait deux (ApiExceptionHandler et celui-ci) qui traitaient
 * les mêmes exceptions avec des statuts contradictoires — 409 chez l'un, 422
 * chez l'autre pour IllegalStateException. ApiExceptionHandler a été supprimé.
 *
 * Format de réponse :
 *   { timestamp, status, code, message, details? }
 *
 * `code` est stable et destiné au frontend ; `message` est destiné à l'humain
 * et peut évoluer sans préavis.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /* ---------- Validation ---------- */

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> champs = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            champs.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }

        Map<String, Object> body = base(HttpStatus.BAD_REQUEST, "VALIDATION", "Requête invalide");
        body.put("details", champs);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraint(ConstraintViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION", ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHODE_NON_AUTORISEE", ex.getMessage());
    }

    /* ---------- Métier ---------- */

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Object> handleNotFound(NoSuchElementException ex) {
        return build(HttpStatus.NOT_FOUND, "INTROUVABLE", ex.getMessage());
    }

    /**
     * Conflit métier : créneau déjà pris, annulation hors délai…
     * 409 et non 422 : la requête est bien formée, c'est l'état du serveur qui s'y oppose.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleConflit(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, "CONFLIT", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "ARGUMENT_INVALIDE", ex.getMessage());
    }

    /* ---------- Sécurité ---------- */

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "NON_AUTHENTIFIE", "Authentification requise");
    }

    @ExceptionHandler({AccessDeniedException.class, SecurityException.class})
    public ResponseEntity<Object> handleAccessDenied(Exception ex) {
        return build(HttpStatus.FORBIDDEN, "ACCES_REFUSE", "Accès refusé");
    }

    /* ---------- Filet de sécurité ---------- */

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleOther(Exception ex) {
        // Le détail part dans les logs, jamais dans la réponse : il peut contenir
        // des noms de tables, des requêtes SQL ou des chemins de fichiers.
        logger.error("Erreur non gérée", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "ERREUR_INTERNE", "Une erreur interne est survenue");
    }

    /* ---------- Fabrique ---------- */

    private ResponseEntity<Object> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(base(status, code, message));
    }

    private Map<String, Object> base(HttpStatus status, String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", Instant.now().toString());
        m.put("status", status.value());
        m.put("code", code);
        m.put("message", message);
        return m;
    }
}
