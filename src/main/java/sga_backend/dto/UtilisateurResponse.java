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

    // Infos de l'agent lié (utiles pour le filtrage par direction)
    private Long idAgent;
    private Long idDirection;
    private String libelleDirection;

    public UtilisateurResponse(Long id, String nom, String prenom, String login,
                               String statut, String codeProfil, String libelleProfil,
                               Long idAgent, Long idDirection, String libelleDirection) {
        this.id = id;
        this.nom = nom;
        this.prenom = prenom;
        this.login = login;
        this.statut = statut;
        this.codeProfil = codeProfil;
        this.libelleProfil = libelleProfil;
        this.idAgent = idAgent;
        this.idDirection = idDirection;
        this.libelleDirection = libelleDirection;
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

        if (u.getAgent() != null) {
            idAgent = u.getAgent().getId();
            if (u.getAgent().getDirection() != null) {
                idDirection = u.getAgent().getDirection().getId();
                libelleDirection = u.getAgent().getDirection().getLibelle();
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
            idAgent,
            idDirection,
            libelleDirection
        );
    }
}