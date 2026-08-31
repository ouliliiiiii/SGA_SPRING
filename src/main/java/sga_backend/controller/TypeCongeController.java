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

import sga_backend.entity.TypeConge;
import sga_backend.service.TypeCongeService;

@RestController
@RequestMapping("/api/types-conge")
public class TypeCongeController {

    @Autowired private TypeCongeService typeCongeService;

    // GET /api/types-conge — types actifs (pour le formulaire de demande)
    @GetMapping
    public List<TypeConge> getTypesActifs() {
        return typeCongeService.getTypesActifs();
    }

    // GET /api/types-conge/tous — tous les types (admin, actifs + inactifs)
    @GetMapping("/tous")
    public List<TypeConge> getTous() {
        return typeCongeService.getTous();
    }

    // POST /api/types-conge — créer un type
    @PostMapping
    public ResponseEntity<?> creer(@RequestBody TypeConge type) {
        try {
            return ResponseEntity.ok(typeCongeService.creer(type));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // PUT /api/types-conge/{id} — modifier un type
    @PutMapping("/{id}")
    public ResponseEntity<?> modifier(@PathVariable Long id, @RequestBody TypeConge type) {
        try {
            return ResponseEntity.ok(typeCongeService.modifier(id, type));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // DELETE /api/types-conge/{id} — supprimer un type

@PutMapping("/{id}/archiver")
public ResponseEntity<?> archiver(@PathVariable Long id) {
    try {
        return ResponseEntity.ok(typeCongeService.archiver(id));
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
 
// PUT /api/types-conge/{id}/desarchiver
@PutMapping("/{id}/desarchiver")
public ResponseEntity<?> desarchiver(@PathVariable Long id) {
    try {
        return ResponseEntity.ok(typeCongeService.desarchiver(id));
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
 
}