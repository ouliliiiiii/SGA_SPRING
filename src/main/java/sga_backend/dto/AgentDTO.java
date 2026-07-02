package sga_backend.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class AgentDTO {

    // IDENTITÉ
    private Long id;
    private String matricule;
    private String nom;
    private String prenom;
    private LocalDate dateNaissance;
    private LocalDate dateEmbauche;
    private String cni;
    private String sexe;

    // CONTACTS
    private String telephone;
    private String noPoste;
    private String emailPersonnel;
    private String email;            // email professionnel (login)

    // ADRESSE
    private String adresse1;
    private String adresse2;
    private String ville;

    // STATUT
    private String statut;

    // LOCALISATION / RÉFÉRENTIELS — exposés en IDs plats (évite les boucles de sérialisation JPA)
    private Long idLieuTravail;
    private String libelleLieuTravail;
    private Long idDirection;
    private String libelleDirection;
    private Long idFonction;
    private String libelleFonction;
    private Long idCategorieSocioPro;
    private String libelleCategorieSocioPro;
    private Long idSuperieur;
    private String nomSuperieur;
    private Long idProfil;
    private String libelleProfil;

    // ENFANTS
    private List<EnfantAgentDTO> enfants;
    private Integer nbEnfants;

    // SOLDE (peut être null si pas encore initialisé)
    private SoldeAjustementDTO solde;
}