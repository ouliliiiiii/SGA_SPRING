package sga_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "journal_activite")
@Data
public class JournalActivite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Qui a fait l'action
    @Column(name = "id_utilisateur")
    private Long idUtilisateur;

    @Column(name = "auteur", length = 200)
    private String auteur;            // "dary adelaide"

    @Column(name = "auteur_login", length = 150)
    private String auteurLogin;       // "admin@sencsu.sn"

    // Type d'action (code) : CONNEXION, CREATION_EMPLOYE, VALIDATION_DEMANDE, ...
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    // Libelle lisible : "Validation d'une demande"
    @Column(name = "libelle_action", length = 150)
    private String libelleAction;

    // Sur quoi porte l'action : "Demande de FALL Moussa (16 au 20 oct)"
    @Column(name = "cible", length = 300)
    private String cible;

    // Details complementaires (optionnel)
    @Column(name = "details", length = 500)
    private String details;

    @Column(name = "date_heure", nullable = false)
    private LocalDateTime dateHeure = LocalDateTime.now();
}