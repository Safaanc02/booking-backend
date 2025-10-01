package com.example.booking.controller;

import com.example.booking.model.Creneau;
import com.example.booking.service.CreneauService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/creneaux")
public class CreneauController {

    private final CreneauService creneauService;

    public CreneauController(CreneauService creneauService) {
        this.creneauService = creneauService;
    }

    @GetMapping
    public List<Creneau> getAllCreneaux() {
        return creneauService.getAllCreneaux();
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin') or @permission.isOwnerCreneau(#id, authentication)")
    public ResponseEntity<Creneau> updateCreneau(@PathVariable Long id, @RequestBody Creneau creneau) {
        return creneauService.updateCreneau(id, creneau)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Creneau> getCreneauById(@PathVariable Long id) {
        return creneauService.getCreneauById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/salon/{salonId}")
    @PreAuthorize("hasRole('admin') or @permission.isOwnerSalon(#salonId, authentication)")
    public ResponseEntity<Creneau> createCreneau(@RequestBody Creneau creneau) {
        return ResponseEntity.ok(creneauService.saveCreneau(creneau));
    }



    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin') or @permission.isOwnerCreneau(#id, authentication)")
    public ResponseEntity<Void> deleteCreneau(@PathVariable Long id) {
        creneauService.deleteCreneau(id);
        return ResponseEntity.noContent().build();
    }
}
