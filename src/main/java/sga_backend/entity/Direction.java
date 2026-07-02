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

    @Column(nullable = false)
    private Boolean actif = true;

    @Column(nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();
}