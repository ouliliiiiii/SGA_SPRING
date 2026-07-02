package sga_backend.dto;

import lombok.Data;

@Data
public class ChangerMotDePasseRequest {
    private Long userId;            // id de l'utilisateur (table Utilisateur)
    private String ancienMotDePasse;
    private String nouveauMotDePasse;
}