package com.example.booking.comptes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Création des comptes professionnels dans Keycloak.
 *
 * C'est l'administration qui inscrit un salon, pas le salon lui-même. Le
 * gérant reçoit ensuite un lien pour choisir son mot de passe — on ne lui
 * envoie jamais un mot de passe en clair par courriel.
 *
 * L'API s'authentifie avec son propre compte de service, jamais avec les
 * identifiants d'un administrateur humain : ses droits se limitent à la
 * gestion des utilisateurs, et révoquer son secret n'affecte personne.
 *
 * Appels REST directs plutôt que keycloak-admin-client : cette bibliothèque
 * doit suivre la version du serveur, et un décalage se manifeste par des
 * erreurs de désérialisation obscures. L'API d'administration, elle, est
 * stable.
 */
@Service
public class ComptesKeycloakService {

    private static final Logger log = LoggerFactory.getLogger(ComptesKeycloakService.class);

    /** Marge de renouvellement du jeton, pour ne pas l'utiliser à la seconde où il expire. */
    private static final long MARGE_SECONDES = 30;

    private final RestClient http;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final String urlPublique;

    private String jeton;
    private Instant jetonExpireA = Instant.EPOCH;

    public ComptesKeycloakService(RestClient.Builder builder,
                                  @Value("${app.keycloak.base-url}") String baseUrl,
                                  @Value("${app.keycloak.realm}") String realm,
                                  @Value("${app.keycloak.admin.client-id}") String clientId,
                                  @Value("${app.keycloak.admin.client-secret}") String clientSecret,
                                  @Value("${app.url-publique}") String urlPublique) {
        this.http = builder.baseUrl(baseUrl).build();
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.urlPublique = urlPublique;
    }

    /* ------------------------------------------------------------------ */

    /** Compte existant pour cet e-mail, ou nouveau compte. Renvoie son identifiant Keycloak. */
    public String creerOuRetrouver(String email, String prenom, String nom, String telephone) {
        Optional<String> existant = chercherParEmail(email);
        if (existant.isPresent()) {
            log.info("Compte Keycloak déjà présent pour {}", email);
            return existant.get();
        }

        /*
         * L'attribut `locale` fixe la langue du compte.
         *
         * Sans lui, les écrans atteints depuis un lien d'e-mail suivent
         * l'en-tête Accept-Language du navigateur : le message arrivait en
         * français, et la page de définition du mot de passe s'affichait en
         * anglais. L'attribut vaut pour les deux.
         */
        Map<String, List<String>> attributs = new java.util.HashMap<>();
        attributs.put("locale", List.of("fr"));
        if (telephone != null && !telephone.isBlank()) {
            attributs.put("telephone", List.of(telephone));
        }

        Map<String, Object> corps = new java.util.HashMap<>(Map.of(
                "username", email,          // l'e-mail sert d'identifiant : un seul à retenir
                "email", email,
                "enabled", true,
                /*
                 * Adresse considérée comme vérifiée.
                 *
                 * Le référencement suit un échange avec le gérant : l'adresse
                 * a été confirmée par un humain, pas par un clic. Exiger en
                 * plus une vérification imposerait au gérant d'ouvrir un
                 * second e-mail avant de pouvoir seulement définir son mot de
                 * passe — un pas de plus, sans rien apprendre.
                 *
                 * Ne vaut que pour ce parcours : une inscription libre devrait
                 * bien vérifier l'adresse.
                 */
                "emailVerified", true,
                "firstName", prenom == null ? "" : prenom,
                "lastName", nom == null ? "" : nom,
                "attributes", attributs));

        try {
            http.post().uri("/admin/realms/{realm}/users", realm)
                    .header("Authorization", "Bearer " + jetonDeService())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corps)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 409) {
                throw new IllegalStateException(
                        "Création du compte Keycloak impossible : " + e.getResponseBodyAsString(), e);
            }
            // 409 : quelqu'un l'a créé entre-temps, ou l'e-mail est déjà pris.
        }

        return chercherParEmail(email).orElseThrow(() -> new IllegalStateException(
                "Compte introuvable après création pour " + email));
    }

    /**
     * Attribue le rôle métier `pro`.
     *
     * Le rôle est résolu via les rôles assignables de l'utilisateur, et non par
     * /roles/{nom} : cette dernière route exige `view-realm`, un droit bien
     * plus large que ce dont on a besoin.
     */
    public void attribuerRole(String keycloakId, String role) {
        List<Map<String, Object>> assignables = http.get()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm/available", realm, keycloakId)
                .header("Authorization", "Bearer " + jetonDeService())
                .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {});

        Map<String, Object> cible = (assignables == null ? List.<Map<String, Object>>of() : assignables)
                .stream()
                .filter(r -> role.equals(r.get("name")))
                .findFirst()
                .orElse(null);

        if (cible == null) {
            // Déjà attribué : la route « available » ne le liste plus.
            log.debug("Rôle {} déjà attribué, ou inexistant, pour {}", role, keycloakId);
            return;
        }

        http.post().uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, keycloakId)
                .header("Authorization", "Bearer " + jetonDeService())
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(Map.of("id", cible.get("id"), "name", role)))
                .retrieve().toBodilessEntity();
    }

    /**
     * Envoie l'invitation à définir un mot de passe.
     *
     * On n'envoie jamais de mot de passe en clair : Keycloak émet un lien à
     * usage unique, et le gérant choisit lui-même le sien. Le lien le ramène
     * ensuite sur le site, connexion amorcée.
     *
     * Un échec ici ne doit pas annuler la création du salon : le salon existe,
     * seule l'invitation reste à renvoyer.
     */
    public boolean inviter(String keycloakId, String email) {
        // Keycloak n'ouvre pas de session applicative au terme d'une action
        // envoyée par e-mail : le gérant devait chercher « Se connecter » puis
        // ressaisir son identifiant, juste après l'avoir tapé. Le paramètre
        // déclenche la connexion à l'arrivée, avec son adresse déjà renseignée.
        String retour = urlPublique + "/pro?bienvenue="
                + URLEncoder.encode(email, StandardCharsets.UTF_8);
        try {
            http.put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/admin/realms/{realm}/users/{id}/execute-actions-email")
                            .queryParam("client_id", "booking-app")
                            .queryParam("redirect_uri", retour)
                            .build(realm, keycloakId))
                    .header("Authorization", "Bearer " + jetonDeService())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of("UPDATE_PASSWORD"))
                    .retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Invitation non envoyée pour {} : {}", email, e.getMessage());
            return false;
        }
    }

    /* ------------------------------------------------------------------ */

    private Optional<String> chercherParEmail(String email) {
        List<Map<String, Object>> trouves = http.get()
                .uri(b -> b.path("/admin/realms/{realm}/users")
                        .queryParam("email", email)
                        .queryParam("exact", true)
                        .build(realm))
                .header("Authorization", "Bearer " + jetonDeService())
                .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {});

        if (trouves == null || trouves.isEmpty()) return Optional.empty();
        return Optional.ofNullable((String) trouves.get(0).get("id"));
    }

    /** Jeton du compte de service, mis en cache jusqu'à peu avant son expiration. */
    private synchronized String jetonDeService() {
        if (jeton != null && Instant.now().isBefore(jetonExpireA)) {
            return jeton;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        Map<String, Object> reponse;
        try {
            reponse = http.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Authentification du compte de service Keycloak impossible. "
                    + "Vérifier app.keycloak.admin.client-secret.", e);
        }

        jeton = (String) reponse.get("access_token");
        long duree = ((Number) reponse.getOrDefault("expires_in", 60)).longValue();
        jetonExpireA = Instant.now().plusSeconds(Math.max(1, duree - MARGE_SECONDES));
        return jeton;
    }
}
