package com.example.booking.config;

import com.example.booking.service.CurrentUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomPermissionEvaluator customPermissionEvaluator;

    public SecurityConfig(CustomPermissionEvaluator customPermissionEvaluator) {
        this.customPermissionEvaluator = customPermissionEvaluator;
    }

    @Bean
    public UserSyncFilter userSyncFilter(CurrentUserService currentUserService) {
        return new UserSyncFilter(currentUserService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserSyncFilter userSyncFilter) throws Exception {
        http
                // API sans cookie de session : le CSRF ne s'applique pas.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Le préflight CORS ne porte pas de token.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Parcours de découverte : recherche, fiches, disponibilités.
                        // Un visiteur doit pouvoir tout consulter avant de se connecter ;
                        // c'est l'obligation de créer un compte trop tôt qui fait fuir.
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/admin/**").hasRole(Roles.ADMIN)
                        .requestMatchers("/api/pro/**").hasAnyRole(Roles.PRO, Roles.ADMIN)
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                ))
                // Après l'authentification du bearer token, sans quoi le contexte
                // de sécurité est encore vide et le filtre ne voit aucun utilisateur.
                .addFilterAfter(userSyncFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setPermissionEvaluator(customPermissionEvaluator);
        return expressionHandler;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthConverter = new JwtAuthenticationConverter();
        jwtAuthConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return jwtAuthConverter;
    }
}
