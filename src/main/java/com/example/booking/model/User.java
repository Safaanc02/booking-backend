package com.example.booking.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

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

    /** Claim "sub" du token. Immuable, contrairement au username. */
    @Column(name = "keycloak_id", nullable = false, unique = true, length = 64)
    private String keycloakId;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(length = 180)
    private String email;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(length = 20)
    private String telephone;

    /** ADMIN, PRO ou CLIENT — voir com.example.booking.config.Roles. */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    // La valeur vient du DEFAULT now() de la base. Sans @Generated, Hibernate
    // ne la relit pas après insertion et le champ reste nul dans la réponse.
    @Generated(event = EventType.INSERT)
    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private Instant creeLe;
}
