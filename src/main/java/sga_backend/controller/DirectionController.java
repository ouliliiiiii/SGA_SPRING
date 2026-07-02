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

import sga_backend.dto.DirectionDTO;
import sga_backend.dto.DirectionRequest;
import sga_backend.service.DirectionService;

@RestController
@RequestMapping("/api/directions")
@CrossOrigin(origins = "*")
public class DirectionController {

    @Autowired private DirectionService directionService;

    @GetMapping
    public ResponseEntity<List<DirectionDTO>> lister() {
        return ResponseEntity.ok(directionService.getActives());
    }

    @GetMapping("/archivees")
    public ResponseEntity<List<DirectionDTO>> listerArchivees() {
        return ResponseEntity.ok(directionService.getArchivees());
    }

    @PostMapping
    public ResponseEntity<?> creer(@RequestBody DirectionRequest req) {
        try {
            return ResponseEntity.ok(directionService.creer(req));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> modifier(@PathVariable Long id, @RequestBody DirectionRequest req) {
        try {
            return ResponseEntity.ok(directionService.modifier(id, req));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/archiver")
    public ResponseEntity<?> archiver(@PathVariable Long id) {
        try {
            directionService.archiver(id);
            return ResponseEntity.ok("Direction archivée");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/restaurer")
    public ResponseEntity<?> restaurer(@PathVariable Long id) {
        try {
            directionService.restaurer(id);
            return ResponseEntity.ok("Direction restaurée");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}