package sga_backend.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import sga_backend.dto.UtilisateurRoleDTO;
import sga_backend.service.RoleService;

// Gestion des rôles — réservé au SUPERADMIN
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasRole('SUPERADMIN')")   // toutes les méthodes : SUPERADMIN uniquement
public class RoleController {

    @Autowired private RoleService roleService;

    // GET /api/roles/utilisateurs — liste des utilisateurs avec leur droit admin
    @GetMapping("/utilisateurs")
    public ResponseEntity<List<UtilisateurRoleDTO>> listerUtilisateurs() {
        return ResponseEntity.ok(roleService.listerUtilisateurs());
    }

    // PUT /api/roles/{id}/admin?valeur=true|false — donner / retirer le droit admin
    @PutMapping("/{id}/admin")
    public ResponseEntity<?> changerDroitAdmin(@PathVariable Long id, @RequestParam boolean valeur) {
        try {
            roleService.changerDroitAdmin(id, valeur);
            return ResponseEntity.ok(valeur
                ? "Droit d'administration accordé"
                : "Droit d'administration retiré");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}