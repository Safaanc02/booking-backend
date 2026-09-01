package com.example.booking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que l'application démarre sur un PostgreSQL réel.
 *
 * Ce test attrape trois familles de régressions d'un coup : une migration
 * Flyway invalide, une entité qui ne correspond plus au schéma (ddl-auto est
 * en validate), et un bean mal câblé.
 *
 * ⚠️ Ignoré silencieusement sans Docker exploitable. C'est notamment le cas
 * avec Docker Engine 29, qui refuse les clients annonçant une API antérieure
 * à 1.40 alors que le docker-java embarqué par Testcontainers 1.21 retombe sur
 * 1.32. Il tournera dès que cette incompatibilité amont sera levée, ou sur un
 * runner CI équipé d'un Docker plus ancien. Vérifier que la classe s'exécute
 * réellement avant de compter sur elle comme filet de sécurité.
 */
@SpringBootTest
// disabledWithoutDocker : la classe entière est ignorée si aucun démon Docker
// exploitable n'est joignable, plutôt que de faire échouer tout le build.
@Testcontainers(disabledWithoutDocker = true)
class BookingApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * Le resource server n'a pas besoin de joindre Keycloak au démarrage : le
     * décodeur est construit paresseusement à partir de la jwk-set-uri.
     */
    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:8081/realms/booking-realm");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:8081/realms/booking-realm/protocol/openid-connect/certs");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Le contexte démarre et les migrations Flyway sont appliquées")
    void contexteEtMigrations() {
        Integer migrations = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(migrations).isEqualTo(4);
    }

    @Test
    @DisplayName("La contrainte anti-chevauchement existe bien en base")
    void contrainteAntiChevauchement() {
        Integer trouvee = jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint
                WHERE conname = 'pas_de_chevauchement' AND contype = 'x'
                """, Integer.class);
        assertThat(trouvee)
                .as("sans cette contrainte, deux clients peuvent réserver le même créneau")
                .isEqualTo(1);
    }
}
