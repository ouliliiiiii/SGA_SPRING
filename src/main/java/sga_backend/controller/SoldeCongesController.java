package sga_backend.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sga_backend.dto.SoldeAjustementDTO;
import sga_backend.dto.SoldeRegularisationDTO;
import sga_backend.service.SoldeCongesService;

@RestController
@RequestMapping("/api/soldes")
@CrossOrigin(origins = "*")
public class SoldeCongesController {

    @Autowired private SoldeCongesService soldeCongesService;

    // Recalcul de TOUS les soldes
    @PostMapping("/recalculer")
    public ResponseEntity<String> recalculerTous() {
        soldeCongesService.recalculerTousLesSoldes();
        return ResponseEntity.ok("Recalcul des soldes effectué");
    }

    // Recalcul du solde d'UN agent
    @PostMapping("/recalculer/{idAgent}")
    public ResponseEntity<?> recalculerAgent(@PathVariable Long idAgent) {
        try {
            soldeCongesService.recalculerUnAgent(idAgent);
            return ResponseEntity.ok("Solde recalculé pour l'agent " + idAgent);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Liste des agents avec leur solde (pour la page Régularisation)
    @GetMapping("/regularisation")
    public List<SoldeRegularisationDTO> listerPourRegularisation() {
        return soldeCongesService.listerPourRegularisation();
    }

    // Ajustement manuel du solde d'un agent
    @PutMapping("/{idAgent}")
    public ResponseEntity<?> ajusterSolde(@PathVariable Long idAgent, @RequestBody SoldeAjustementDTO data) {
        try {
            soldeCongesService.ajusterSolde(idAgent, data);
            return ResponseEntity.ok("Solde ajusté");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }



// POST /api/soldes/export — Excel des soldes (filtré par IDs si fournis)
@PostMapping("/export")
public ResponseEntity<byte[]> exporterSoldes(@RequestBody(required = false) List<Long> ids) {
    try {
        byte[] fichier = soldeCongesService.genererExcelSoldes(ids);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=soldes.xlsx")
            .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .body(fichier);
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(null);
    }
}
}