package sga_backend.dto;

import lombok.Data;

@Data
public class UtilisateurRoleDTO {
    private Long id;              // id de l'utilisateur
    private String nom;
    private String prenom;
    private String login;
    private String codeProfil;
    private String libelleProfil;
    private Boolean estAdmin;
    private String statut;

    // Infos agent (pour affichage)
    private String matricule;
    private String libelleDirection;
    private String libelleUnite;
}