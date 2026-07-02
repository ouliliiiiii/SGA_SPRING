package sga_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

// DTO léger pour la liste des demandes à valider (page liste)
@Data
public class DemandeAValiderDTO {
    private Long idDemande;
    private Long idEtape;            // l'étape que CE validateur doit traiter
    private String libelleEtape;    // ex. "Chef de Division"

    // Infos agent
    private String nomAgent;
    private String prenomAgent;
    private String matriculeAgent;
    private String fonctionAgent;
    private Long idAgent;
    private String categorieConge;
    private String profilAgent;

    // Infos demande
    private String libelleTypeConge;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private BigDecimal nombreJours;
    private String motif;
    private String statut;
    private LocalDateTime dateCreation;
    private Boolean etapeFinale;
}