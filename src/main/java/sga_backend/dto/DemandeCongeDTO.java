package sga_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DemandeCongeDTO {
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
}