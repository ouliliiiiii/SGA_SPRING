package sga_backend.dto;

import lombok.Data;

@Data
public class DirectionRequest {
    private String code;
    private String libelle;
    private String type;      // DIRECTION ou CELLULE (choisi dans le formulaire)
    private Long idParent;    // rattachement (DG ou SG)
}