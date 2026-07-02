package sga_backend.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class JourFerieDTO {
    private Long id;
    private String nom;
    private String type;        // FIXE ou MOBILE
    private Integer jour;
    private Integer mois;
    private LocalDate date;
}