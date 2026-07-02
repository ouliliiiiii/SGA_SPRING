package sga_backend.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sga_backend.entity.Agent;
import sga_backend.entity.CategorieSocioPro;
import sga_backend.entity.Direction;
import sga_backend.entity.Fonction;
import sga_backend.entity.LieuTravail;
import sga_backend.entity.Profil;
import sga_backend.repository.AgentRepository;
import sga_backend.repository.CategorieSocioProRepository;
import sga_backend.repository.DirectionRepository;
import sga_backend.repository.FonctionRepository;
import sga_backend.repository.LieuTravailRepository;
import sga_backend.repository.ProfilRepository;

// Controller dédié aux données de référence (listes déroulantes)
@RestController
@RequestMapping("/api/referentiel")
public class ReferentielController {

    @Autowired
    private LieuTravailRepository lieuTravailRepository;

    @Autowired
    private DirectionRepository directionRepository;

    @Autowired
    private FonctionRepository fonctionRepository;

    @Autowired
    private CategorieSocioProRepository categorieSocioProRepository;

    @Autowired
    private ProfilRepository profilRepository;

    @Autowired
    private AgentRepository agentRepository;

    // GET /api/referentiel/lieux-travail
    @GetMapping("/lieux-travail")
    public ResponseEntity<List<LieuTravail>> getLieuxTravail() {
        return ResponseEntity.ok(lieuTravailRepository.findAll());
    }

    // GET /api/referentiel/directions
    @GetMapping("/directions")
    public ResponseEntity<List<Direction>> getDirections() {
        return ResponseEntity.ok(directionRepository.findAll());
    }

    // GET /api/referentiel/fonctions
    @GetMapping("/fonctions")
    public ResponseEntity<List<Fonction>> getFonctions() {
        return ResponseEntity.ok(fonctionRepository.findAll());
    }

    // GET /api/referentiel/categories-socio-pro
    @GetMapping("/categories-socio-pro")
    public ResponseEntity<List<CategorieSocioPro>> getCategoriesSocioPro() {
        return ResponseEntity.ok(categorieSocioProRepository.findAll());
    }

    // GET /api/referentiel/profils
    @GetMapping("/profils")
    public ResponseEntity<List<Profil>> getProfils() {
        return ResponseEntity.ok(profilRepository.findAll());
    }

    // GET /api/referentiel/agents
    // Pour le supérieur hiérarchique
    @GetMapping("/agents")
    public ResponseEntity<List<Agent>> getAgents() {
        return ResponseEntity.ok(agentRepository.findAll());
    }
}