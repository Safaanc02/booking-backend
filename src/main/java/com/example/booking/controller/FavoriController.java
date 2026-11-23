package com.example.booking.controller;

import com.example.booking.service.FavoriService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Les salons gardés par la personne connectée.
 *
 * Une liste d'identifiants, et non de fiches : le site a déjà les fiches
 * sous la main (recherche, tableau de bord) et n'a besoin que de savoir
 * lesquelles porter un cœur plein. Les fiches des favoris eux-mêmes passent
 * par la route publique, salon par salon, dans le tableau de bord.
 */
@RestController
@RequestMapping("/api/favoris")
@PreAuthorize("hasAnyRole('CLIENT','PRO','ADMIN')")
public class FavoriController {

    private final FavoriService favoris;

    public FavoriController(FavoriService favoris) {
        this.favoris = favoris;
    }

    @GetMapping
    public ResponseEntity<List<Long>> mesSalons() {
        return ResponseEntity.ok(favoris.mesSalons());
    }

    @PutMapping("/{salonId}")
    public ResponseEntity<Void> garder(@PathVariable Long salonId) {
        favoris.garder(salonId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{salonId}")
    public ResponseEntity<Void> retirer(@PathVariable Long salonId) {
        favoris.retirer(salonId);
        return ResponseEntity.noContent().build();
    }
}
