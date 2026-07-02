package sga_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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

@Entity
@Table(name = "demande_conge")
@Data
public class DemandeConge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Agent concerné par le congé
    @ManyToOne
    @JoinColumn(name = "id_agent", nullable = false)
    private Agent agent;

    // Utilisateur qui a saisi/soumis la demande (le demandeur connecté)
    @ManyToOne
    @JoinColumn(name = "id_demandeur")
    private Utilisateur utilisateurDemandeur;

    @ManyToOne
    @JoinColumn(name = "id_type_conge", nullable = false)
    private TypeConge typeConge;

    @Column(nullable = false)
    private LocalDate dateDebut;

    @Column(nullable = false)
    private LocalDate dateFin;

    @Column(nullable = false, length = 15)
    private String momentDebut = "JOURNEE";

    @Column(nullable = false, length = 15)
    private String momentFin = "JOURNEE";

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal nombreJours;

    @Column(length = 500)
    private String motif;

    @Column(length = 255)
    private String justificatif;

    @Column(nullable = false, length = 30)
    private String statut = "EN_ATTENTE";

    @Column(nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();
}