package sga_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

// Une étape de validation pour une demande de congé.
// Une demande génère plusieurs étapes (une par validateur du circuit).
@Entity
@Table(name = "etape_validation")
@Data
public class EtapeValidation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // La demande concernée
    @ManyToOne
    @JoinColumn(name = "id_demande", nullable = false)
    private DemandeConge demande;

    // Ordre de l'étape dans le circuit (1, 2, 3, 4...)
    @Column(nullable = false)
    private Integer ordre;

    // Code du profil attendu pour valider cette étape
    // (ex. CHEF_DIVISION, DIRECTEUR, ADMIN, DAF, CSR, SG)
    @Column(name = "profil_attendu", nullable = false, length = 30)
    private String profilAttendu;

    // Libellé lisible de l'étape (ex. "Chef de division")
    @Column(length = 100)
    private String libelle;

    // Statut de l'étape : EN_ATTENTE, VALIDEE, REFUSEE, IGNOREE
    @Column(nullable = false, length = 20)
    private String statut = "EN_ATTENTE";

    // --- Traçabilité : qui a agi, quand, avec quel commentaire ---
    @ManyToOne
    @JoinColumn(name = "id_validateur")
    private Utilisateur validateur;   // l'utilisateur qui a validé/refusé

    @Column(name = "date_action")
    private LocalDateTime dateAction;

    @Column(length = 500)
    private String commentaire;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    // Ciblage géographique de l'étape
@Column(name = "id_unite_attendue")
private Long idUniteAttendue;        // rempli pour l'étape CHEF_DIVISION

@Column(name = "id_direction_attendue")
private Long idDirectionAttendue;    // rempli pour l'étape DIRECTEUR
}