package sga_backend.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import sga_backend.dto.JourFerieDTO;
import sga_backend.service.JourFerieService;

@RestController
@RequestMapping("/api/jours-feries")
public class JourFerieController {

    @Autowired private JourFerieService jourFerieService;

    // GET /api/jours-feries  (tous, ou filtré par ?type=FIXE / MOBILE)
    @GetMapping
    public List<JourFerieDTO> getJoursFeries(@RequestParam(required = false) String type) {
        if (type != null && !type.isBlank()) {
            return jourFerieService.getParType(type);
        }
        return jourFerieService.getTous();
    }

    // POST /api/jours-feries  (ajouter un férié, surtout les mobiles)
    @PostMapping
    public ResponseEntity<?> ajouter(@RequestBody JourFerieDTO dto) {
        try {
            return ResponseEntity.ok(jourFerieService.ajouter(dto));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // DELETE /api/jours-feries/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<?> supprimer(@PathVariable Long id) {
        try {
            jourFerieService.supprimer(id);
            return ResponseEntity.ok("Jour férié supprimé");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}