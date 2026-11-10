// UtilisateurResponse.java
package sga_backend.dto;

import lombok.Data;
import sga_backend.entity.Utilisateur;

@Data
public class UtilisateurResponse {
    private Long id;
    private String nom;
    private String prenom;
    private String login;
    private String statut;
    private String codeProfil;
    private String libelleProfil;

    // Droit d'administration (independant du profil metier)
    private Boolean estAdmin;

    // Infos de l'agent lié (utiles pour le filtrage par direction)
    private Long idAgent;
    private Long idDirection;
    private String libelleDirection;
    private Long idUnite;
    private String libelleUnite;   // ex. "Division Réseaux et Supports"
    private String token;   // le jeton JWT

    public UtilisateurResponse(Long id, String nom, String prenom, String login,
                               String statut, String codeProfil, String libelleProfil,
                               Boolean estAdmin,
                               Long idAgent, Long idDirection, String libelleDirection,
                               Long idUnite, String libelleUnite) {
        this.id = id;
        this.nom = nom;
        this.prenom = prenom;
        this.login = login;
        this.statut = statut;
        this.codeProfil = codeProfil;
        this.libelleProfil = libelleProfil;
        this.estAdmin = estAdmin;
        this.idAgent = idAgent;
        this.idDirection = idDirection;
        this.libelleDirection = libelleDirection;
        this.idUnite = idUnite;
        this.libelleUnite = libelleUnite;
    }

    // ============================================================
    // Méthode de fabrique : convertit un Utilisateur en UtilisateurResponse.
    // ============================================================
    public static UtilisateurResponse depuis(Utilisateur u) {
        String codeProfil = u.getProfil() != null ? u.getProfil().getCode() : null;
        String libelleProfil = u.getProfil() != null ? u.getProfil().getLibelle() : null;

        Long idAgent = null;
        Long idDirection = null;
        String libelleDirection = null;
        Long idUnite = null;
        String libelleUnite = null;

        if (u.getAgent() != null) {
            idAgent = u.getAgent().getId();
            if (u.getAgent().getDirection() != null) {
                idDirection = u.getAgent().getDirection().getId();
                libelleDirection = u.getAgent().getDirection().getLibelle();
            }
            if (u.getAgent().getUnite() != null) {
                idUnite = u.getAgent().getUnite().getId();
                libelleUnite = u.getAgent().getUnite().getLibelle();
            }
        }

        return new UtilisateurResponse(
            u.getId(),
            u.getNom(),
            u.getPrenom(),
            u.getLogin(),
            u.getStatut(),
            codeProfil,
            libelleProfil,
            Boolean.TRUE.equals(u.getEstAdmin()),   // droit admin (false si null)
            idAgent,
            idDirection,
            libelleDirection,
            idUnite,
            libelleUnite
        );
    }
}