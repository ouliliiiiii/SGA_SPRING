package sga_backend.dto;

import lombok.Data;

@Data
public class UniteDTO {
    private Long id;
    private String code;
    private String libelle;
    private String type;                 // DIVISION | SERVICE_REGIONAL

    // Rattachement (l'un ou l'autre selon le type)
    private Long idDirection;
    private String libelleDirection;
    private Long idLieuTravail;
    private String libelleLieuTravail;

    private Boolean actif;
}