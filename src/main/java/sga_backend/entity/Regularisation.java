package sga_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "regularisation")
@Data
public class Regularisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_agent")
    @JsonIgnoreProperties({"enfants", "superieur"})
    private Agent agent;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(name = "moment_debut", length = 20)
    private String momentDebut;

    @Column(name = "moment_fin", length = 20)
    private String momentFin;

    @Column(name = "nb_jours", nullable = false)
    private BigDecimal nbJours;

    @Column(name = "date_operation", nullable = false)
    private LocalDateTime dateOperation = LocalDateTime.now();

    @Column(name = "effectue_par", length = 150)
    private String effectuePar;
}