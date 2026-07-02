package sga_backend.dto;

import lombok.Data;

// Requête envoyée quand un validateur agit sur une étape
@Data
public class ActionValidationRequest {
    private Long idEtape;          // l'étape sur laquelle on agit
    private Long idValidateur;     // l'utilisateur qui valide/refuse (depuis localStorage)
    private String commentaire;    // optionnel si validation, obligatoire si refus
}