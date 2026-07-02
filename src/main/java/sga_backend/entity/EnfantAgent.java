package sga_backend.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

// Entité correspondant à la table 'enfant_agent'
// Chaque ligne représente UN enfant d'un agent
@Entity
@Table(name = "enfant_agent")
@Data
public class EnfantAgent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Lien vers l'agent parent
    // ON DELETE CASCADE : si l'agent est supprimé, ses enfants le sont aussi
    @ManyToOne
    @JoinColumn(name = "id_agent", nullable = false)
    private Agent agent;

    // Prénom optionnel (pas de NOT NULL)
    @Column(length = 100)
    private String prenom;

    // Date de naissance obligatoire (nécessaire pour le calcul des congés)
    @Column(nullable = false)
    private LocalDate dateNaissance;

}