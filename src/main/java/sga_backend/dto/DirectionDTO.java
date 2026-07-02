package sga_backend.dto;

import lombok.Data;

@Data
public class DirectionDTO {
    private Long id;
    private String code;
    private String libelle;
    private Boolean actif;
}