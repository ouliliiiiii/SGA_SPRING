package sga_backend.dto;

import lombok.Data;

// Étape 1 : l'utilisateur demande un code en donnant son email (login)
@Data
public class DemandeResetRequest {
    private String login;   // email professionnel = login
}