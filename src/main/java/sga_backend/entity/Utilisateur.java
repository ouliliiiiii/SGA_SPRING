package sga_backend.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "utilisateur")
@Data
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 100)
    private String prenom;

    @Column(nullable = false)
    private LocalDateTime dateInscription = LocalDateTime.now();

    // Statut simple VARCHAR : EN_ATTENTE, VALIDE, REFUSE
    @Column(nullable = false, length = 20)
    private String statut = "EN_ATTENTE";

    @Column(nullable = false, unique = true, length = 150)
    private String login;

    @Column(nullable = false, length = 255)
    private String motDePasse;

   @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_agent")
    private Agent agent;

     @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_profil")
    @JsonIgnoreProperties("utilisateur")
    private Profil profil;

    // Code de réinitialisation du mot de passe (6 chiffres) et son expiration
        @Column(length = 6)
        private String codeReset;

        private LocalDateTime codeResetExpiration;

        @Column(name = "token_actif", length = 500)
        private String tokenActif;

        @Column(name = "est_admin", nullable = false)
        private Boolean estAdmin = false;
}