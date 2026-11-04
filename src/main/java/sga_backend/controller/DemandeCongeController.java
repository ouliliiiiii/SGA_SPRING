package sga_backend.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import sga_backend.dto.CreerDemandeRequest;
import sga_backend.dto.DemandeCongeDTO;
import sga_backend.repository.AgentRepository;
import sga_backend.service.DemandeCongeService;
import sga_backend.service.PermissionPdfService;
import sga_backend.service.SoldeCongesService;

import sga_backend.entity.Agent;
import sga_backend.entity.DemandeConge;
import sga_backend.entity.EtapeValidation;
import sga_backend.repository.DemandeCongeRepository;
import sga_backend.repository.EtapeValidationRepository;

@RestController
@RequestMapping("/api/demandes-conge")
public class DemandeCongeController {

    @Autowired private DemandeCongeService demandeCongeService;
    @Autowired private AgentRepository agentRepository;           
    @Autowired private SoldeCongesService soldeCongesService;
    @Autowired private DemandeCongeRepository demandeCongeRepository;     
    @Autowired private PermissionPdfService permissionPdfService;
@Autowired private EtapeValidationRepository etapeValidationRepository;  

    // Dossier de stockage des justificatifs (configurable dans application.properties)
    @Value("${sga.upload.justificatifs:uploads/justificatifs}")
    private String dossierJustificatifs;

    // ============================================
    // POST /api/demandes-conge — créer une demande
    // ============================================
    @PostMapping
    public ResponseEntity<?> creerDemande(@RequestBody CreerDemandeRequest request) {
        try {
            DemandeCongeDTO dto = demandeCongeService.creerDemande(request);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ============================================
    // POST /api/demandes-conge/{id}/justificatif — uploader un fichier
    // ============================================
    @PostMapping("/{id}/justificatif")
    public ResponseEntity<?> uploaderJustificatif(
            @PathVariable Long id,
            @RequestParam("fichier") MultipartFile fichier) {
        try {
            if (fichier.isEmpty()) {
                return ResponseEntity.badRequest().body("Fichier vide");
            }

            // Créer le dossier s'il n'existe pas
            Path dossier = Paths.get(dossierJustificatifs);
            Files.createDirectories(dossier);

            // Nom unique : {idDemande}_{timestamp}_{nomOriginal}
                      // Récupérer la demande pour construire un nom parlant
            DemandeConge demande = demandeCongeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demande introuvable"));

            // Extension du fichier original
            String nomOriginal = fichier.getOriginalFilename();
            String ext = "";
            if (nomOriginal != null) {
                int pt = nomOriginal.lastIndexOf('.');
                if (pt >= 0) ext = nomOriginal.substring(pt);
            }

            // Nom parlant : {id}_{Nom}_{Prenom}_{dateDebut}.ext
            Agent agent = demande.getAgent();
            String nom = (agent != null && agent.getNom() != null) ? agent.getNom() : "";
            String prenom = (agent != null && agent.getPrenom() != null) ? agent.getPrenom() : "";
            String dateDeb = demande.getDateDebut() != null ? demande.getDateDebut().toString() : "";

            String nomFichier = (id + "_" + nom + "_" + prenom + "_" + dateDeb + ext)
                .replaceAll("[\\\\/:*?\"<>|]", "_")   // caractères interdits dans un nom de fichier
                .replaceAll("\\s+", "_");             // espaces → _

            Path cible = dossier.resolve(nomFichier);
            Files.copy(fichier.getInputStream(), cible);

            // Enregistrer le nom en base
            demandeCongeService.enregistrerJustificatif(id, nomFichier);

            return ResponseEntity.ok("Justificatif enregistré");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Erreur lors de l'enregistrement du fichier");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ============================================
    // GET /api/demandes-conge/{id}/justificatif — télécharger le fichier
    // ============================================
    @GetMapping("/{id}/justificatif")
    public ResponseEntity<?> telechargerJustificatif(@PathVariable Long id) {
        try {
            DemandeCongeDTO demande = demandeCongeService.getDemande(id);
            if (demande.getJustificatif() == null) {
                return ResponseEntity.notFound().build();
            }

            Path fichier = Paths.get(dossierJustificatifs).resolve(demande.getJustificatif());
            Resource resource = new UrlResource(fichier.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + demande.getJustificatif() + "\"")
                .body(resource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ============================================
    // GET /api/demandes-conge/agent/{idAgent} — demandes d'un agent
    // ============================================
    @GetMapping("/agent/{idAgent}")
    public List<DemandeCongeDTO> getDemandesAgent(@PathVariable Long idAgent) {
        return demandeCongeService.getDemandesAgent(idAgent);
    }

    // ============================================
    // GET /api/demandes-conge/{id} — détail d'une demande
    // ============================================
    @GetMapping("/{id}")
    public ResponseEntity<?> getDemande(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(demandeCongeService.getDemande(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
// ============================================
    // GET /api/demandes-conge/historique — toutes les demandes (admin)
    // ============================================
   @GetMapping("/historique/{idUtilisateur}")
    public List<DemandeCongeDTO> getHistorique(@PathVariable Long idUtilisateur) {
        return demandeCongeService.getHistorique(idUtilisateur);
    }

    // ============================================
    // POST /api/demandes-conge/export — Excel de l'historique
    // Corps : liste d'IDs (demandes filtrées côté frontend). Vide = tout exporter.
    // ============================================
    @PostMapping("/export")
    public ResponseEntity<byte[]> exporterHistorique(@RequestBody(required = false) List<Long> ids) {
        byte[] contenu = demandeCongeService.exportHistorique(ids);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"historique_demandes.xlsx\"")
            .body(contenu);
    }
        @PostMapping("/dashboard/export")
    public ResponseEntity<byte[]> exporterDashboard(@RequestBody(required = false) List<Long> ids) {
        byte[] fichier = demandeCongeService.exportDashboard(ids);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=export_dashboard.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(fichier);
    }


    @GetMapping("/echus-a-date")
    public ResponseEntity<?> echusADate(
            @RequestParam Long idAgent,
            @RequestParam String date) {
        Agent agent = agentRepository.findById(idAgent)
            .orElseThrow(() -> new RuntimeException("Agent introuvable"));
        LocalDate d = LocalDate.parse(date);
        BigDecimal echus = soldeCongesService.calculerEchusALaDate(agent, d);
        Map<String, Object> result = new HashMap<>();
        result.put("echus", echus);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/export-zip")
public ResponseEntity<byte[]> exporterDemandesAvecJustificatifs(@RequestBody List<Long> ids) {
    byte[] zip = demandeCongeService.exporterDemandesAvecJustificatifs(ids);
    return ResponseEntity.ok()
        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export_demandes.zip")
        .contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
        .body(zip);
}

    @GetMapping("/{id}/permission")
public ResponseEntity<byte[]> telechargerPermission(
        @PathVariable Long id,
        @RequestParam(value = "titre", required = false) String titre) {
    try {
        DemandeConge demande = demandeCongeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Demande introuvable"));

        // Étapes du circuit (pour les visas)
        List<EtapeValidation> etapes = etapeValidationRepository.findByDemandeIdOrderByOrdreAsc(id);

        // Titre : personnalisé si fourni, sinon auto selon la catégorie
        String titreFinal = (titre != null && !titre.isBlank())
            ? titre
            : titreParDefaut(demande.getTypeConge() != null ? demande.getTypeConge().getCategorie() : null);

        byte[] pdf = permissionPdfService.genererPermission(demande, etapes, titreFinal);

        String nomFichier = "permission_" + id + ".pdf";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nomFichier)
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(null);
    }
}

// Titre par défaut selon la catégorie
private String titreParDefaut(String categorie) {
    if (categorie == null) return "PERMISSION D'ABSENCE";
    switch (categorie.toUpperCase()) {
        case "DEDUCTIBLE":      return "PERMISSION D'ABSENCE DÉDUCTIBLE DES CONGÉS";
        case "NON_DEDUCTIBLE":  return "PERMISSION EXCEPTIONNELLE D'ABSENCE NON DÉDUCTIBLE DES CONGÉS";
        case "SANS_SOLDE":      return "PERMISSION D'ABSENCE SANS SOLDE";
        default:                return "PERMISSION D'ABSENCE";
    }
}
@GetMapping("/{id}/justificatif-fichier")
public ResponseEntity<byte[]> voirJustificatif(@PathVariable Long id) {
    try {
        DemandeConge demande = demandeCongeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Demande introuvable"));
        String nomJustif = demande.getJustificatif();
        if (nomJustif == null || nomJustif.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        java.nio.file.Path fichier = java.nio.file.Paths.get(dossierJustificatifs).resolve(nomJustif);
        if (!java.nio.file.Files.exists(fichier)) {
            return ResponseEntity.notFound().build();
        }
        byte[] contenu = java.nio.file.Files.readAllBytes(fichier);
        // Type MIME selon l'extension
        String type = "application/octet-stream";
        String lower = nomJustif.toLowerCase();
        if (lower.endsWith(".pdf")) type = "application/pdf";
        else if (lower.endsWith(".png")) type = "image/png";
        else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) type = "image/jpeg";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nomJustif + "\"")
            .contentType(MediaType.parseMediaType(type))
            .body(contenu);
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(null);
    }
}
    
}