package com.example.booking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Documentation d'API, générée depuis le code.
 *
 * Interface : /swagger-ui.html — schéma brut : /v3/api-docs
 *
 * Le bouton « Authorize » attend un jeton d'accès Keycloak, obtenu par exemple
 * avec le flot mot de passe sur le realm booking-realm (voir le README).
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEMA_JWT = "bearer-jwt";

    @Bean
    public OpenAPI openApi(@Value("${app.keycloak.issuer-uri}") String issuer,
                           @Value("${server.port:8080}") String port) {
        return new OpenAPI()
                .info(new Info()
                        .title("Booking.ma — API")
                        .version("v1")
                        .description("""
                                Marketplace de réservation beauté au Maroc.

                                **Conventions**

                                - `/api/public/**` — sans authentification : recherche, fiches, disponibilités.
                                  Un visiteur doit pouvoir tout consulter avant de créer un compte.
                                - `/api/**` — jeton requis.
                                - `/api/pro/**` — back-office professionnel, rôle PRO. Chaque route vérifie
                                  en plus la propriété de la ressource : le rôle seul ne dit pas de quel salon
                                  on parle.
                                - `/api/admin/**` — administration de la plateforme, rôle ADMIN.

                                **Erreurs** — format unique `{ timestamp, status, code, message, details? }`.
                                Le champ `code` est stable et destiné au client ; `message` est destiné à
                                l'humain et peut évoluer.

                                **Montants** en dirhams (MAD). **Instants** en UTC, à présenter dans le fuseau
                                `Africa/Casablanca` — qui passe de UTC+1 à UTC+0 pendant le Ramadan.
                                """)
                        .contact(new Contact().name("Équipe Booking.ma"))
                        .license(new License().name("Propriétaire")))
                .servers(List.of(
                        new Server().url("http://localhost:" + port).description("Développement local")))
                .components(new Components().addSecuritySchemes(SCHEMA_JWT,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Jeton d'accès délivré par Keycloak — " + issuer)))
                // Appliqué globalement : les routes publiques restent joignables
                // sans jeton, l'en-tête est simplement ignoré.
                .addSecurityItem(new SecurityRequirement().addList(SCHEMA_JWT));
    }
}
