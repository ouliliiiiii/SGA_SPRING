package sga_backend.dto;

import lombok.Data;

@Data
public class InscriptionRequest {

    private String nom;
    private String prenom;
    private String login;
    private String motDePasse;

}