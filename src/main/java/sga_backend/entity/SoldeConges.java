package sga_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;

// Entité correspondant à la table 'solde_conges'
// Un solde par agent (relation 1-à-1)
@Entity
@Table(name = "solde_conges")
@Data
public class SoldeConges {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relation 1-à-1 avec Agent
    @OneToOne
    @JoinColumn(name = "id_agent", nullable = false)
    private Agent agent;

    // Nombre de jours de congés acquis
    @Column(nullable = false)
    private BigDecimal nbCongesAcquis = BigDecimal.ZERO;

    // Nombre de jours pris (déductibles)
    @Column(nullable = false)
    private BigDecimal nbJourPrisDeductible = BigDecimal.ZERO;

    // Reliquat de congés
    @Column(nullable = false)
    private BigDecimal nbCongesReliquat = BigDecimal.ZERO;

    // Congés supplémentaires (enfants, ancienneté...)
    @Column(nullable = false)
    private BigDecimal nbCongesSupplementaire = BigDecimal.ZERO;

    // Congés échus (expirés)
    @Column(nullable = false)
    private BigDecimal nbCongesEchus = BigDecimal.ZERO;

    // Congés en cours (demandes validées en attente)
    @Column(nullable = false)
    private BigDecimal nbCongesEncours = BigDecimal.ZERO;

    // Cumul des jours sans solde
    @Column(nullable = false)
    private BigDecimal nbJourSansSolde = BigDecimal.ZERO;

    private LocalDateTime dateDernierCalcul;

    @Column(nullable = false)
    private LocalDateTime dateMaj = LocalDateTime.now();

}