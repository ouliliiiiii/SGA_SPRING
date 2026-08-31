package sga_backend.controller;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import sga_backend.dto.AgentDTO;
import sga_backend.dto.CreerEmployeRequest;
import sga_backend.dto.EnfantAgentDTO;
import sga_backend.dto.UtilisateurResponse;
import sga_backend.entity.Agent;
import sga_backend.entity.EnfantAgent;
import sga_backend.entity.Poste;
import sga_backend.entity.Utilisateur;
import sga_backend.repository.AgentRepository;
import sga_backend.repository.EnfantAgentRepository;
import sga_backend.repository.PosteRepository;
import sga_backend.repository.SoldeCongesRepository;
import sga_backend.repository.UtilisateurRepository;
import sga_backend.service.EmployeService;
import sga_backend.service.ImportEmployeService;

@RestController
@RequestMapping("/api/employes")
public class EmployeController {

    @Autowired private EmployeService employeService;
    @Autowired private AgentRepository agentRepository;
    @Autowired private EnfantAgentRepository enfantAgentRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private SoldeCongesRepository soldeCongesRepository;

    // ============================================
    // POST /api/employes — Créer un employé
    // ============================================
    @PostMapping
    public ResponseEntity<?> creerEmploye(@RequestBody CreerEmployeRequest request) {
        try {
            Utilisateur utilisateur = employeService.creerEmploye(request);
            return ResponseEntity.ok(UtilisateurResponse.depuis(utilisateur));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body("Cette donnée existe déjà (email ou matricule en double).");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ============================================
    // GET /api/employes/{id}/solde — Solde de congés
    // ============================================
    @GetMapping("/{id}/solde")
    public ResponseEntity<?> getSolde(@PathVariable Long id) {
        try {
            return soldeCongesRepository.findByAgentId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ============================================
    // GET /api/employes/{idAgent}/enfants
    // ============================================
    @GetMapping("/{idAgent}/enfants")
    public ResponseEntity<?> getEnfants(@PathVariable Long idAgent) {
        try {
            agentRepository.findById(idAgent)
                .orElseThrow(() -> new RuntimeException("Agent introuvable"));
            List<EnfantAgent> enfants = enfantAgentRepository.findByAgentId(idAgent);
            return ResponseEntity.ok(enfants);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ============================================
    // POST /api/employes/{idAgent}/enfants
    // ============================================
    @PostMapping("/{idAgent}/enfants")
    public ResponseEntity<?> ajouterEnfant(
            @PathVariable Long idAgent,
            @RequestBody EnfantAgentDTO dto) {
        try {
            Agent agent = agentRepository.findById(idAgent)
                .orElseThrow(() -> new RuntimeException("Agent introuvable"));

            EnfantAgent enfant = new EnfantAgent();
            enfant.setAgent(agent);
            enfant.setPrenom(dto.getPrenom());
            enfant.setDateNaissance(dto.getDateNaissance());
            EnfantAgent sauvegarde = enfantAgentRepository.save(enfant);

            agent.setNbEnfants(enfantAgentRepository.findByAgentId(idAgent).size());
            agentRepository.save(agent);

            return ResponseEntity.ok(sauvegarde);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ============================================
    // PUT /api/employes/{idAgent}/statut
    // ============================================
    @PutMapping("/{idAgent}/statut")
    public ResponseEntity<?> changerStatutAgent(
            @PathVariable Long idAgent,
            @RequestBody Map<String, String> body) {
        try {
            Agent agent = agentRepository.findById(idAgent)
                .orElseThrow(() -> new RuntimeException("Agent introuvable"));

            Utilisateur utilisateur = utilisateurRepository.findByAgentId(idAgent)
                .orElseThrow(() -> new RuntimeException("Compte introuvable"));

            if (utilisateur.getProfil() != null &&
                utilisateur.getProfil().getCode().equals("ADMIN")) {
                throw new RuntimeException("Impossible de modifier le statut de l'administrateur");
            }

            String nouveauStatut = body.get("statut");
            agent.setStatut(nouveauStatut);
            agentRepository.save(agent);

            utilisateur.setStatut(nouveauStatut.equals("ACTIF") ? "VALIDE" : "REFUSE");
            utilisateurRepository.save(utilisateur);

            return ResponseEntity.ok("Statut mis à jour avec succès");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ============================================
    // PUT /api/employes/{id} — Modifier (agent + enfants + solde)
    // ============================================
    @PutMapping("/{id}")
    public ResponseEntity<?> modifierEmploye(
            @PathVariable Long id,
            @RequestBody CreerEmployeRequest request) {
        try {
            AgentDTO dto = employeService.modifierEmploye(id, request);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ============================================
    // GET /api/employes/{id} — Détail d'un agent
    // ============================================
    @GetMapping("/{id}")
    public AgentDTO getEmploye(@PathVariable Long id) {
        return employeService.getEmploye(id);
    }

    // ============================================
    // GET /api/employes/agents — Liste des agents
    // ============================================
    @GetMapping("/agents")
    public List<AgentDTO> getAgents() {
        return employeService.getAllAgents();
    }
    // ============================================================
// À AJOUTER dans EmployeController.java
// GET /api/employes/export  -> télécharge un Excel avec TOUTES les données
// ============================================================

@PostMapping("/export")
public ResponseEntity<byte[]> exporterEmployes(@RequestBody(required = false) List<Long> ids){
    try {
       byte[] fichier = employeService.genererExcelEmployes(ids);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=employes.xlsx")
            .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .body(fichier);
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(null);
    }
}

@Autowired private ImportEmployeService importEmployeService;
 
@PostMapping("/import")
public ResponseEntity<?> importerEmployes(@RequestParam("fichier") MultipartFile fichier) {
    try {
        if (fichier.isEmpty()) {
            return ResponseEntity.badRequest().body("Fichier vide");
        }
        ImportEmployeService.ResultatImport resultat = importEmployeService.importer(fichier);
        return ResponseEntity.ok(resultat);
    } catch (Exception e) {
        return ResponseEntity.badRequest().body("Erreur lors de l'import : " + e.getMessage());
    }
}
 @PostMapping("/import/colonnes")
public ResponseEntity<?> lireColonnes(@RequestParam("fichier") MultipartFile fichier) {
    try {
        if (fichier.isEmpty()) {
            return ResponseEntity.badRequest().body("Fichier vide");
        }
        return ResponseEntity.ok(importEmployeService.lireColonnes(fichier));
    } catch (Exception e) {
        return ResponseEntity.badRequest().body("Impossible de lire le fichier : " + e.getMessage());
    }
}
        @PostMapping("/import/mapping")
    public ResponseEntity<?> importerAvecMapping(
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam("mapping") String mappingStr) {
        try {
            if (fichier.isEmpty()) {
                return ResponseEntity.badRequest().body("Fichier vide");
            }
            // Décoder le mapping "champ::colonne;;champ::colonne" sans Jackson
            java.util.Map<String, String> mapping = new java.util.HashMap<>();
            if (mappingStr != null && !mappingStr.isEmpty()) {
                for (String paire : mappingStr.split(";;")) {
                    String[] parts = paire.split("::", 2);
                    if (parts.length == 2) {
                        mapping.put(parts[0], parts[1]);
                    }
                }
            }

            ImportEmployeService.ResultatImport resultat =
                importEmployeService.importerAvecMapping(fichier, mapping);
            return ResponseEntity.ok(resultat);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erreur lors de l'import : " + e.getMessage());
        }
    }
        @Autowired private PosteRepository posteRepository;

    @GetMapping("/postes/par-division/{idUnite}")
    public ResponseEntity<?> getPostesParDivision(@PathVariable Long idUnite) {
        List<Poste> postes = posteRepository.findByUniteId(idUnite);
        List<Map<String, Object>> result = postes.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("libelle", p.getLibelle());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }
}