package sga_backend.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class EnfantAgentDTO {

    // id : null = nouvel enfant à créer ; non-null = enfant existant à mettre à jour
    private Long id;

    private String prenom;
    private LocalDate dateNaissance;
}