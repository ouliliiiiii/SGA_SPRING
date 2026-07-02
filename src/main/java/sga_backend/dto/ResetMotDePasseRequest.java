package sga_backend.dto;

import lombok.Data;

// Étape 2 : l'utilisateur valide le code et définit son nouveau mot de passe
@Data
public class ResetMotDePasseRequest {
    private String login;
    private String code;
    private String nouveauMotDePasse;
}