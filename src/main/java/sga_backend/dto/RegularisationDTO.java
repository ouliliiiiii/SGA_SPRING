package sga_backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RegularisationDTO {
    private Long id;
    private String matricule;
    private String nom;
    private String prenom;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String momentDebut;
    private String momentFin;
    private BigDecimal nbJours;
    private LocalDateTime dateOperation;
    private String effectuePar;
}