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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import sga_backend.dto.UniteDTO;
import sga_backend.dto.UniteRequest;
import sga_backend.service.UniteService;

@RestController
@RequestMapping("/api/unites")
@CrossOrigin(origins = "*")
public class UniteController {

    @Autowired private UniteService uniteService;

    // Lister les unités actives (optionnel : ?type=DIVISION ou ?type=SERVICE_REGIONAL)
    @GetMapping
    public ResponseEntity<List<UniteDTO>> lister(@RequestParam(required = false) String type) {
        if (type != null && !type.isEmpty()) {
            return ResponseEntity.ok(uniteService.getParType(type));
        }
        return ResponseEntity.ok(uniteService.getActives());
    }

    @GetMapping("/archivees")
    public ResponseEntity<List<UniteDTO>> listerArchivees() {
        return ResponseEntity.ok(uniteService.getArchivees());
    }

    @PostMapping
    public ResponseEntity<?> creer(@RequestBody UniteRequest req) {
        try {
            return ResponseEntity.ok(uniteService.creer(req));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> modifier(@PathVariable Long id, @RequestBody UniteRequest req) {
        try {
            return ResponseEntity.ok(uniteService.modifier(id, req));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/archiver")
    public ResponseEntity<?> archiver(@PathVariable Long id) {
        try {
            uniteService.archiver(id);
            return ResponseEntity.ok("Unité archivée");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/restaurer")
    public ResponseEntity<?> restaurer(@PathVariable Long id) {
        try {
            uniteService.restaurer(id);
            return ResponseEntity.ok("Unité restaurée");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}