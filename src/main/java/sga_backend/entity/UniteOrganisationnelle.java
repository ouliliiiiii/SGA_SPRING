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

// Unité organisationnelle : unifie les divisions (siège) et services régionaux.
//   type = DIVISION          -> rattachée à une Direction
//   type = SERVICE_REGIONAL  -> rattachée à un LieuTravail (agence régionale)
@Entity
@Table(name = "unite_organisationnelle")
@Data
public class UniteOrganisationnelle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String libelle;

    // DIVISION | SERVICE_REGIONAL
    @Column(nullable = false, length = 20)
    private String type;

    // Rempli si type = DIVISION
    @ManyToOne
    @JoinColumn(name = "id_direction")
    private Direction direction;

    // Rempli si type = SERVICE_REGIONAL
    @ManyToOne
    @JoinColumn(name = "id_lieu_travail")
    private LieuTravail lieuTravail;

    @Column(nullable = false)
    private Boolean actif = true;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();
}