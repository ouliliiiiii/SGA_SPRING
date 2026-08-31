package sga_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

// Cette entité correspond à la table 'direction'
// (anciennement 'service', renommée)
@Entity
@Table(name = "direction")
@Data
public class Direction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String libelle;

    // Niveau hiérarchique de la structure :
    // DIRECTION_GENERALE, SECRETARIAT_GENERAL, DIRECTION, CELLULE
    @Column(nullable = false, length = 30)
    private String type = "DIRECTION";

    // Rattachement hiérarchique : id de la structure parente
    // (ex. une cellule rattachée à la DG ou au SG). NULL pour la DG (sommet).
    @Column(name = "id_parent")
    private Long idParent;

    @Column(nullable = false)
    private Boolean actif = true;

    @Column(nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();
}