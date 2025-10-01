package com.example.booking.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    // ✅ Public: pas besoin de token
    @GetMapping("/api/public/hello")
    public String publicHello() {
        return "👋 Hello depuis PUBLIC endpoint (accessible à tous)";
    }

    // ✅ Accessible à tous les utilisateurs authentifiés
    @GetMapping("/api/user/hello")
    public String userHello() {
        return "🙋 Hello depuis USER endpoint (authentification requise)";
    }

    // ✅ Accessible uniquement aux ADMIN
    @GetMapping("/api/admin/hello")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminHello() {
        return "🛡️ Hello depuis ADMIN endpoint (réservé aux admins)";
    }
}
