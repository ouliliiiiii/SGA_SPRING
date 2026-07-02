package sga_backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sga_backend.dto.ActionValidationRequest;
import sga_backend.service.CircuitValidationService;

@RestController
@RequestMapping("/api/validation")
@CrossOrigin(origins = "*")
public class ValidationController {

    @Autowired private CircuitValidationService circuitValidationService;

    // COUCHE 3 : les demandes qu'un utilisateur doit valider (filtrées par son profil)
    @GetMapping("/a-valider/{idUtilisateur}")
    public ResponseEntity<?> getDemandesAValider(@PathVariable Long idUtilisateur) {
        return ResponseEntity.ok(circuitValidationService.getDemandesAValider(idUtilisateur));
    }

    // Les étapes d'une demande (pour la modale détail : suivi du circuit)
    @GetMapping("/demande/{idDemande}/etapes")
    public ResponseEntity<?> getEtapes(@PathVariable Long idDemande) {
        return ResponseEntity.ok(circuitValidationService.getEtapes(idDemande));
    }

    // Valider une étape
    @PostMapping("/valider")
    public ResponseEntity<String> valider(@RequestBody ActionValidationRequest request) {
        try {
            circuitValidationService.valider(request.getIdEtape(), request.getIdValidateur(), request.getCommentaire());
            return ResponseEntity.ok("Étape validée");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Refuser une étape (commentaire obligatoire)
    @PostMapping("/refuser")
    public ResponseEntity<String> refuser(@RequestBody ActionValidationRequest request) {
        try {
            circuitValidationService.refuser(request.getIdEtape(), request.getIdValidateur(), request.getCommentaire());
            return ResponseEntity.ok("Demande refusée");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}