package sga_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

// Sous-DTO pour l'ajustement manuel du solde par le RH.
// Seuls les 4 premiers champs sont modifiables ; les autres sont calculés
// (renvoyés en lecture seule pour affichage côté frontend).
@Data
public class SoldeAjustementDTO {

    // --- Champs ajustables manuellement ---
    private BigDecimal nbCongesAcquis;
    private BigDecimal nbCongesReliquat;
    private BigDecimal nbCongesSupplementaire;
    private BigDecimal nbJourSansSolde;

    // --- Champs calculés (lecture seule, renvoyés au frontend pour info) ---
    private BigDecimal nbJourPrisDeductible;
    private BigDecimal nbCongesEncours;
    private BigDecimal nbCongesEchus;
    private LocalDateTime dateDernierCalcul;
}