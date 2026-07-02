package sga_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "lieu_travail")
@Data
public class LieuTravail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String libelle;

    // Correspond à type_lieu_travail_enum en base (SIEGE, REGIONAL)
    @Enumerated(EnumType.STRING)
    @Column(name = "type_entite", nullable = false)
    private TypeLieuTravail typeEntite = TypeLieuTravail.REGIONAL;

    @Column(length = 100)
    private String region;

    @ManyToOne
    @JoinColumn(name = "id_entite_parent")
    private LieuTravail lieuTravailParent;

    @Column(nullable = false)
    private Boolean actif = true;

    @Column(nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();
  

}