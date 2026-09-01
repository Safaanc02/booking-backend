package com.example.booking.controller;

import com.example.booking.dto.AvisRequest;
import com.example.booking.dto.AvisResponse;
import com.example.booking.service.AvisService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Dépôt d'un avis par le client. La lecture publique est dans PublicController. */
@RestController
@RequestMapping("/api/avis")
public class AvisController {

    private final AvisService avisService;

    public AvisController(AvisService avisService) {
        this.avisService = avisService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT','PRO','ADMIN')")
    public ResponseEntity<AvisResponse> deposer(@Valid @RequestBody AvisRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(avisService.deposer(req));
    }
}
