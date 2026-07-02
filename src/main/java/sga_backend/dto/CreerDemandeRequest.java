package sga_backend.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class CreerDemandeRequest {
    private Long idAgent;        // agent concerné par le congé
    private Long idDemandeur;    // utilisateur connecté qui saisit la demande
    private Long idTypeConge;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String momentDebut;
    private String momentFin;
    private String motif;
}