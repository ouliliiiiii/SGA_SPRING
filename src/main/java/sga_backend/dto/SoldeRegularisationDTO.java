package sga_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

// DTO pour la page Régularisation : agent + solde à plat
@Data
public class SoldeRegularisationDTO {
    private Long idAgent;
    private String matricule;
    private String nom;
    private String prenom;
    private Long idDirection;
    private String libelleDirection;
    private Long idUniteo;
    private String libelleUniteo;
    private String statut;

    // Solde
    private BigDecimal nbCongesAcquis;
    private BigDecimal nbCongesReliquat;
    private BigDecimal nbCongesSupplementaire;
    private BigDecimal nbJourPrisDeductible;
    private BigDecimal nbCongesEncours;
    private BigDecimal nbCongesEchus;
    private BigDecimal nbJourSansSolde;
    private LocalDateTime dateDernierCalcul;
}