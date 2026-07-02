package sga_backend.dto;

import lombok.Data;

@Data
public class UniteRequest {
    private String code;
    private String libelle;
    private String type;             // DIVISION | SERVICE_REGIONAL
    private Long idDirection;         // requis si type = DIVISION
    private Long idLieuTravail;       // requis si type = SERVICE_REGIONAL
}