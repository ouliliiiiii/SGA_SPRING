package sga_backend.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

// DTO complet pour la création d'un employé
@Data
public class CreerEmployeRequest {

    // IDENTITÉ
    private String matricule;
    private String nom;
    private String prenom;
    private LocalDate dateNaissance;
    private LocalDate dateEmbauche;
    private String cni;
    private String  sexe;

    // LOCALISATION PROFESSIONNELLE
    private Long idEntite;
    private Long idDirection;
    private String statut;
    private Long idFonction;
    private Long idCategorieSocioPro;
    private Long idSuperieur;

    // CONTACTS
    private String telephone;
    private String poste;
    private String emailPersonnel;
    private String email;

    // ADRESSE
    private String adresse1;
    private String adresse2;
    private String ville;

    // COMPTE
    private Long idProfil;

    // ENFANTS (liste dynamique)
    private List<EnfantAgentDTO> enfants;

    // nb_enfants calculé automatiquement depuis la liste
    private Integer nbEnfants;

    // SOLDE (utilisé uniquement en modification, pour l'ajustement RH)
private SoldeAjustementDTO solde;
private Long idUnite;
    private String numeroDePoste;
    private Long idPoste;

    public String getNumeroDePoste() { return numeroDePoste; }
    public void setNumeroDePoste(String numeroDePoste) { this.numeroDePoste = numeroDePoste; }
    public Long getIdPoste() { return idPoste; }
    public void setIdPoste(Long idPoste) { this.idPoste = idPoste; }

    private LocalDate dateFinContrat;
}