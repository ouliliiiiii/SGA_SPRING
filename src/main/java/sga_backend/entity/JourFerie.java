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

    // Nom affiché (ex. "Fête de l'Indépendance", "Tabaski")
    @Column(nullable = false, length = 100)
    private String nom;

    // Type : FIXE (même jour/mois chaque année) ou MOBILE (date qui change chaque année)
    @Column(nullable = false, length = 15)
    private String type;

    // ===== Pour les jours FIXES : jour + mois (l'année n'a pas d'importance) =====
    private Integer jour;   // 1..31
    private Integer mois;   // 1..12

    // ===== Pour les jours MOBILES : date complète (spécifique à une année) =====
    private LocalDate date;

    // Actif ou archivé (même pattern que TypeConge)
    @Column(nullable = false)
    private Boolean actif = true;
}