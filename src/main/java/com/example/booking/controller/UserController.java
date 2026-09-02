package com.example.booking.controller;

import com.example.booking.dto.UserResponse;
import com.example.booking.dto.UserRequest;
import com.example.booking.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 🔹 Liste des utilisateurs (Admin, Pro)
     */
    /**
     * Liste des utilisateurs — administration uniquement.
     *
     * Elle était ouverte aux professionnels : n'importe lequel pouvait
     * récupérer nom, email et rôle de tous les comptes de la plateforme.
     * Ce sont des données à caractère personnel au sens de la loi 09-08.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * Fiche d'un utilisateur — administration uniquement.
     *
     * Elle était ouverte à tout compte authentifié, y compris client : chacun
     * pouvait lire l'email de n'importe qui, et énumérer les comptes par
     * identifiant. Un utilisateur qui veut ses propres informations les a déjà
     * dans son jeton.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }


    /**
     * 🔹 Créer un utilisateur (Admin uniquement)
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        UserResponse created = userService.createUser(request);
        return ResponseEntity.created(URI.create("/api/users/" + created.getId())).body(created);
    }

    /**
     * 🔹 Mettre à jour un utilisateur (Admin uniquement)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserRequest request
    ) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    /**
     * 🔹 Supprimer un utilisateur (Admin uniquement)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
