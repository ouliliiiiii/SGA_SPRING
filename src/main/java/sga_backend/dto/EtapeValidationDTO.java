package sga_backend.dto;

import java.time.LocalDateTime;

import lombok.Data;

// DTO léger pour afficher une étape de validation (évite la sérialisation
// en cascade de toute la demande / agent / enfants...)
@Data
public class EtapeValidationDTO {
    private Long id;
    private Long idDemande;
    private Integer ordre;
    private String profilAttendu;
    private String libelle;
    private String statut;

    // Traçabilité
    private Long idValidateur;
    private String nomValidateur;
    private LocalDateTime dateAction;
    private String commentaire;
    private String libelleStructure;   // ex. "Réseaux" (unité ou direction ciblée)
}