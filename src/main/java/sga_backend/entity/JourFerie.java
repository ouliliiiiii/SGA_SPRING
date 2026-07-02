package sga_backend.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "jour_ferie")
@Data
public class JourFerie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nom du jour férié (ex. "Fête de l'Indépendance", "Tabaski")
    @Column(nullable = false, length = 100)
    private String nom;

    // FIXE (même jour/mois chaque année) ou MOBILE (date précise une année donnée)
    @Column(nullable = false, length = 10)
    private String type;

    // Pour les FIXES : jour (1-31) et mois (1-12)
    private Integer jour;
    private Integer mois;

    // Pour les MOBILES : date complète
    private LocalDate date;
}