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
    @GetMapping
    @PreAuthorize("hasAnyRole('admin','pro')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * 🔹 Récupérer un utilisateur par ID (Admin, Pro, Client)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('admin','pro','client')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }


    /**
     * 🔹 Créer un utilisateur (Admin uniquement)
     */
    @PostMapping
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        UserResponse created = userService.createUser(request);
        return ResponseEntity.created(URI.create("/api/users/" + created.getId())).body(created);
    }

    /**
     * 🔹 Mettre à jour un utilisateur (Admin uniquement)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
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
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
