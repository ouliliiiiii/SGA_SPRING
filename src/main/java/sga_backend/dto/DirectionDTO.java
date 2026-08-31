package sga_backend.dto;

import lombok.Data;

@Data
public class DirectionDTO {
    private Long id;
    private String code;
    private String libelle;
    private String type;         // DIRECTION_GENERALE, SECRETARIAT_GENERAL, DIRECTION, CELLULE
    private Long idParent;       // structure parente
    private String libelleParent; // libellé du parent (pour l'affichage)
    private Boolean actif;
}