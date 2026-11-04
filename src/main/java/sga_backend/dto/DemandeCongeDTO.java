package sga_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DemandeCongeDTO {
  private String uniteEtapeCourante;   // ex. "Division Réseaux et Supports"
    private Long id;

    private Long idAgent;
    private String nomAgent;
    private String prenomAgent;
    private String matriculeAgent;

    // Demandeur (utilisateur connecté qui a saisi)
    private Long idDemandeur;
    private String nomDemandeur;

    private Long idTypeConge;
    private String libelleTypeConge;
    private String codeTypeConge;
    private String categorieTypeConge;

    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String momentDebut;
    private String momentFin;
    private BigDecimal nombreJours;
    private String motif;
    private String justificatif;
    private String statut;
    private LocalDateTime dateCreation;

      // ===== Étape courante (« chez qui est la demande ») — page Historique =====
    // Renseignés uniquement pour une demande encore en cours (EN_ATTENTE / EN_COURS).
    private String libelleEtapeCourante;    // ex. « Chef de Division »
    private String profilAttenduCourant;    // ex. « CHEF_DIVISION »
    private Integer ordreEtapeCourante;      // position dans le circuit
    private String nomValidateurAttendu;    // nom du validateur si déjà connu (souvent null tant qu'en attente)

    // ===== Champs agent pour le tableau de bord (filtres sexe / direction / division) =====
    private String sexeAgent;          // "M" / "F"
    private String directionAgent;     // libellé de la direction
    private String divisionAgent;      // libellé de l'unité/division
    private String profilAgent;
    private String posteAgent;
    private String typeLieuAgent;   // SIEGE ou REGIONAL


  private java.time.LocalDate dateEmbaucheAgent;
  private String typeContratAgent;
  private java.time.LocalDate dateFinContratAgent;
  private Integer nbEnfantsAgent;

}