package sga_backend.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
import sga_backend.service.DemandeCongeService;

@RestController
@RequestMapping("/api/demandes-conge")
public class DemandeCongeController {

    @Autowired private DemandeCongeService demandeCongeService;

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
            String nomOriginal = fichier.getOriginalFilename();
            String nomFichier = id + "_" + System.currentTimeMillis() + "_" + nomOriginal;

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
    
}