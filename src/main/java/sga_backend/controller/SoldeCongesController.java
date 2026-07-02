package sga_backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sga_backend.service.SoldeCongesService;

@RestController
@RequestMapping("/api/soldes")
@CrossOrigin(origins = "*")
public class SoldeCongesController {

    @Autowired private SoldeCongesService soldeCongesService;

    // Déclenche manuellement le recalcul de TOUS les soldes.
    // Pratique pour tester le calcul sans attendre le job de 2h du matin.
    @PostMapping("/recalculer")
    public ResponseEntity<String> recalculerTous() {
        soldeCongesService.recalculerTousLesSoldes();
        return ResponseEntity.ok("Recalcul des soldes effectué");
    }
}