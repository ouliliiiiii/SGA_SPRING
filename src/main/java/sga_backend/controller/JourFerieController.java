package sga_backend.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sga_backend.entity.JourFerie;
import sga_backend.service.JourFerieService;

@RestController
@RequestMapping("/api/jours-feries")
public class JourFerieController {

    @Autowired private JourFerieService jourFerieService;

    // GET /api/jours-feries — liste des actifs
    @GetMapping
    public List<JourFerie> getTous() {
        return jourFerieService.getTousActifs();
    }

    // GET /api/jours-feries/archives
    @GetMapping("/archives")
    public List<JourFerie> getArchives() {
        return jourFerieService.getArchives();
    }

    // POST /api/jours-feries
    @PostMapping
    public ResponseEntity<?> creer(@RequestBody JourFerie jf) {
        try {
            return ResponseEntity.ok(jourFerieService.creer(jf));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // PUT /api/jours-feries/{id}
    @PutMapping("/{id}")
    public ResponseEntity<?> modifier(@PathVariable Long id, @RequestBody JourFerie jf) {
        try {
            return ResponseEntity.ok(jourFerieService.modifier(id, jf));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // PUT /api/jours-feries/{id}/archiver
    @PutMapping("/{id}/archiver")
    public ResponseEntity<?> archiver(@PathVariable Long id) {
        try {
            jourFerieService.archiver(id);
            return ResponseEntity.ok("Jour férié archivé");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // PUT /api/jours-feries/{id}/restaurer
    @PutMapping("/{id}/restaurer")
    public ResponseEntity<?> restaurer(@PathVariable Long id) {
        try {
            jourFerieService.restaurer(id);
            return ResponseEntity.ok("Jour férié restauré");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}