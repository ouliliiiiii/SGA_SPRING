package sga_backend.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "agent")
@Data
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // IDENTITÉ
    @Column(nullable = false, unique = true, length = 30)
    private String matricule;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 100)
    private String prenom;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(unique = true, length = 30)
    private String cni;

    @Column(length = 5)
    private String sexe;

    @Column(length = 30)
    private String telephone;

    @Column(unique = true, length = 150)
    private String email;

    @Column(name = "email_personnel", length = 150)
    private String emailPersonnel;


   @Column(name = "numero_de_poste")
private String numeroDePoste;

    @Column(name = "type_contrat", length = 20, nullable = false)
    private String typeContrat = "CDI";

    @Column(name = "date_embauche")
    private LocalDate dateEmbauche;

    @Column(name = "date_fin_contrat")
    private LocalDate dateFinContrat;

    // LOCALISATION
   
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_fonction")
    @JsonIgnoreProperties("agents")
    private Fonction fonction;

   @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categorie_socio_pro")
    @JsonIgnoreProperties("agents")
    private CategorieSocioPro categorieSocioPro;

    // Supérieur hiérarchique (auto-référence)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_superieur")
    @JsonIgnoreProperties({"superieur", "agent"})
    private Agent superieur;

    @Column(length = 20, nullable = false)
    private String statut = "ACTIF";

    @Column(name = "nb_enfants")
    private Integer nbEnfants = 0;

    // ADRESSE
    @Column(length = 255)
    private String adresse1;

    @Column(length = 255)
    private String adresse2;

    @Column(length = 100)
    private String ville;

    // DATES SYSTÈME
    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    @Column(name = "date_maj", nullable = false)
    private LocalDateTime dateMaj = LocalDateTime.now();
    @OneToMany(mappedBy = "agent", fetch = FetchType.LAZY)
    private List<EnfantAgent> enfants;

   @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_unite")
    @JsonIgnoreProperties("agents")
    private UniteOrganisationnelle unite;
   @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_direction")
    @JsonIgnoreProperties("agents")
    private Direction direction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_poste")
    private Poste posteOccupe;


}