package com.example.booking.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String keycloakId; // ID Keycloak (sub)

    @Column(nullable = false, unique = true)
    private String username;

    private String email;
    private String fullName;

    @Column(nullable = false)
    private String role; // ADMIN / PROPRIETAIRE / CLIENT

    private boolean enabled;
}
