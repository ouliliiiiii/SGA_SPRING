package sga_backend.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sga_backend.dto.SoldeAjustementDTO;
import sga_backend.dto.SoldeRegularisationDTO;
import sga_backend.entity.Agent;
import sga_backend.entity.SoldeConges;
import sga_backend.repository.AgentRepository;
import sga_backend.repository.SoldeCongesRepository;

@Service
public class SoldeCongesService {

    private static final Logger log = LoggerFactory.getLogger(SoldeCongesService.class);

    @Autowired private AgentRepository agentRepository;
    @Autowired private SoldeCongesRepository soldeCongesRepository;

    // 2 jours ouvrables par mois de service 
    private static final BigDecimal JOURS_PAR_MOIS = BigDecimal.valueOf(2);

    // Droit annuel complet 
    private static final BigDecimal JOURS_PAR_AN = BigDecimal.valueOf(24);

    // Période de référence : 12 mois
    private static final int MOIS_PERIODE_REFERENCE = 12;

    // ============================================================
    // JOB AUTOMATIQUE
    // PROD : "0 0 2 * * *" (tous les jours à 2h)
    // TEST : "0 0 2 * * *" (toutes les minutes)
    // ============================================================
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void recalculerTousLesSoldes() {
        log.info(">>> [JOB SOLDE] Démarrage du recalcul à {}", LocalDateTime.now());
        List<Agent> agents = agentRepository.findAll();
        int compteur = 0;
        for (Agent agent : agents) {
            if (recalculerSoldeAgent(agent)) compteur++;
        }
        log.info(">>> [JOB SOLDE] Recalcul terminé : {} solde(s) mis à jour", compteur);
    }

    @Transactional
    public boolean recalculerSoldeAgent(Agent agentParam) {
         // Recharger l'agent AVEC ses enfants (pour le calcul des congés supplémentaires)
        Agent agent = agentRepository.findByIdWithEnfants(agentParam.getId()).orElse(agentParam);

        if (agent.getDateEmbauche() == null) return false;
        if (!"ACTIF".equalsIgnoreCase(agent.getStatut())) return false;

        SoldeConges solde = soldeCongesRepository.findByAgentId(agent.getId())
            .orElseGet(() -> {
                SoldeConges s = new SoldeConges();
                s.setAgent(agent);
                return s;
            });

        LocalDate aujourdhui = LocalDate.now();

        // --- Mois de service écoulés ---
        long moisDeService = ChronoUnit.MONTHS.between(agent.getDateEmbauche(), aujourdhui);
        if (moisDeService < 0) moisDeService = 0;

        // --- Années complètes d'ancienneté (paliers annuels) ---
        long anneesCompletes = ChronoUnit.YEARS.between(agent.getDateEmbauche(), aujourdhui);
        if (anneesCompletes < 0) anneesCompletes = 0;

        // --- Congés acquis : 2 jours par mois ---
        BigDecimal joursAcquis = JOURS_PAR_MOIS.multiply(BigDecimal.valueOf(moisDeService));
        solde.setNbCongesAcquis(joursAcquis);

        // --- Jours déjà pris (déductibles) : valeur actuelle, mise à jour à la validation des demandes ---
        BigDecimal joursPris = solde.getNbJourPrisDeductible() != null
            ? solde.getNbJourPrisDeductible()
            : BigDecimal.ZERO;

        // --- Congés échus = (années complètes × 24) − jours pris ---
        // Ce sont les jours des périodes précédentes arrivés à échéance, donc disponibles.
        BigDecimal joursEchusBruts = JOURS_PAR_AN.multiply(BigDecimal.valueOf(anneesCompletes));
        BigDecimal joursEchus = joursEchusBruts.subtract(joursPris);
        if (joursEchus.compareTo(BigDecimal.ZERO) < 0) {
            joursEchus = BigDecimal.ZERO; // jamais négatif
        }
        solde.setNbCongesEchus(joursEchus);

        // --- Reliquat = acquis − jours pris ---
        BigDecimal reliquat = joursAcquis.subtract(joursPris);
        if (reliquat.compareTo(BigDecimal.ZERO) < 0) {
            reliquat = BigDecimal.ZERO; // jamais négatif
        }
        solde.setNbCongesReliquat(reliquat);

        solde.setDateDernierCalcul(LocalDateTime.now());
        solde.setDateMaj(LocalDateTime.now());
                // --- Congés supplémentaires (ancienneté + mères) ---
        BigDecimal congesSupp = calculerCongesSupplementaires(agent);
        solde.setNbCongesSupplementaire(congesSupp);

        soldeCongesRepository.save(solde);
        return true;
    }

    // L'agent peut-il prendre un congé à cette date ? (période de référence de 12 mois atteinte)
    public boolean peutPrendreConge(Agent agent, LocalDate dateDebutConge) {
        if (agent.getDateEmbauche() == null) return false;
        LocalDate dateJouissance = agent.getDateEmbauche().plusMonths(MOIS_PERIODE_REFERENCE);
        return !dateDebutConge.isBefore(dateJouissance);
    }
   


//=== Méthode : recalcul d'un seul agent ===

@Transactional
public void recalculerUnAgent(Long idAgent) {
    Agent agent = agentRepository.findById(idAgent)
        .orElseThrow(() -> new RuntimeException("Agent introuvable"));
    recalculerSoldeAgent(agent);
}


//=== Méthode : lister les agents avec leur solde (pour la page Régularisation) ===

public List<SoldeRegularisationDTO> listerPourRegularisation() {
    List<SoldeRegularisationDTO> liste = new ArrayList<>();

    for (Agent agent : agentRepository.findAll()) {
        SoldeRegularisationDTO dto = new SoldeRegularisationDTO();
        dto.setIdAgent(agent.getId());
        dto.setMatricule(agent.getMatricule());
        dto.setNom(agent.getNom());
        dto.setPrenom(agent.getPrenom());
        dto.setStatut(agent.getStatut());

        // Direction (lue directement sur l'agent)
        if (agent.getDirection() != null) {
            dto.setIdDirection(agent.getDirection().getId());
            dto.setLibelleDirection(agent.getDirection().getLibelle());
        }
        // Division (unité)
        if (agent.getUnite() != null) {
            dto.setIdUniteo(agent.getUnite().getId());
            dto.setLibelleUniteo(agent.getUnite().getLibelle());
        }

        // Solde
        SoldeConges s = soldeCongesRepository.findByAgentId(agent.getId()).orElse(null);
        if (s != null) {
            dto.setNbCongesAcquis(s.getNbCongesAcquis());
            dto.setNbCongesReliquat(s.getNbCongesReliquat());
            dto.setNbCongesSupplementaire(s.getNbCongesSupplementaire());
            dto.setNbJourPrisDeductible(s.getNbJourPrisDeductible());
            dto.setNbCongesEncours(s.getNbCongesEncours());
            dto.setNbCongesEchus(s.getNbCongesEchus());
            dto.setNbJourSansSolde(s.getNbJourSansSolde());
            dto.setDateDernierCalcul(s.getDateDernierCalcul());
        }

        liste.add(dto);
    }
    return liste;
}




@Transactional
public void ajusterSolde(Long idAgent, SoldeAjustementDTO data) {
    Agent agent = agentRepository.findById(idAgent)
        .orElseThrow(() -> new RuntimeException("Agent introuvable"));

    SoldeConges solde = soldeCongesRepository.findByAgentId(idAgent)
        .orElseGet(() -> {
            SoldeConges s = new SoldeConges();
            s.setAgent(agent);
            return s;
        });

    // Champs ajustables manuellement :
    // - jours pris (impacte reliquat/échus -> on recalcule ensuite)
    // - jours sans solde (purement informatif, aucun impact sur les congés)
    if (data.getNbJourPrisDeductible() != null) {
        solde.setNbJourPrisDeductible(data.getNbJourPrisDeductible());
    }
    if (data.getNbJourSansSolde() != null) {
        solde.setNbJourSansSolde(data.getNbJourSansSolde());
    }

    solde.setDateMaj(java.time.LocalDateTime.now());
    soldeCongesRepository.save(solde);

    // Recalcul de l'agent : met à jour acquis/reliquat/échus en tenant compte
    // du nouveau "jours pris". (Ne touche pas aux jours sans solde.)
    recalculerSoldeAgent(agent);
}




// Génère un Excel des soldes. Si ids fourni -> seulement ces agents ; sinon tous.
public byte[] genererExcelSoldes(List<Long> ids) throws Exception {
    List<Agent> agents;
    if (ids != null && !ids.isEmpty()) {
        agents = agentRepository.findAllById(ids);
    } else {
        agents = agentRepository.findAll();
    }

    try (XSSFWorkbook workbook = new XSSFWorkbook();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {

        Sheet sheet = workbook.createSheet("Soldes");

        // Style en-tête
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        String[] entetes = {
            "Matricule", "Nom", "Prénom", "Direction", "Division", "Statut",
            "Congés acquis", "Reliquat", "Congés échus",
            "Jours pris", "En cours", "Congés supplémentaires", "Jours sans solde",
            "Dernier calcul"
        };

        Row ligneEntete = sheet.createRow(0);
        for (int i = 0; i < entetes.length; i++) {
            Cell cell = ligneEntete.createCell(i);
            cell.setCellValue(entetes[i]);
            cell.setCellStyle(headerStyle);
        }

        int numLigne = 1;
        for (Agent a : agents) {
            Row row = sheet.createRow(numLigne++);
            int c = 0;

            row.createCell(c++).setCellValue(nzSolde(a.getMatricule()));
            row.createCell(c++).setCellValue(nzSolde(a.getNom()));
            row.createCell(c++).setCellValue(nzSolde(a.getPrenom()));
            row.createCell(c++).setCellValue(a.getDirection() != null ? nzSolde(a.getDirection().getLibelle()) : "");
            row.createCell(c++).setCellValue(a.getUnite() != null ? nzSolde(a.getUnite().getLibelle()) : "");
            row.createCell(c++).setCellValue(nzSolde(a.getStatut()));

            SoldeConges s = soldeCongesRepository.findByAgentId(a.getId()).orElse(null);
            row.createCell(c++).setCellValue(valSolde(s != null ? s.getNbCongesAcquis() : null));
            row.createCell(c++).setCellValue(valSolde(s != null ? s.getNbCongesReliquat() : null));
            row.createCell(c++).setCellValue(valSolde(s != null ? s.getNbCongesEchus() : null));
            row.createCell(c++).setCellValue(valSolde(s != null ? s.getNbJourPrisDeductible() : null));
            row.createCell(c++).setCellValue(valSolde(s != null ? s.getNbCongesEncours() : null));
            row.createCell(c++).setCellValue(valSolde(s != null ? s.getNbCongesSupplementaire() : null));
            row.createCell(c++).setCellValue(valSolde(s != null ? s.getNbJourSansSolde() : null));
            row.createCell(c++).setCellValue(s != null && s.getDateDernierCalcul() != null ? s.getDateDernierCalcul().toString() : "");
        }

        for (int i = 0; i < entetes.length; i++) sheet.autoSizeColumn(i);

        workbook.write(out);
        return out.toByteArray();
    }
}

// Helpers (renommés pour éviter tout conflit avec ceux d'EmployeService)
private String nzSolde(String s) { return s != null ? s : ""; }
private double valSolde(java.math.BigDecimal b) { return b != null ? b.doubleValue() : 0; }


    // Calcule les congés supplémentaires (ancienneté + mères de famille)
    private BigDecimal calculerCongesSupplementaires(Agent agent) {
        BigDecimal total = BigDecimal.ZERO;
        LocalDate aujourdhui = LocalDate.now();

        // ===== RÈGLE 1 : ANCIENNETÉ (tous les agents, palier atteint seul) =====
        if (agent.getDateEmbauche() != null) {
            long annees = ChronoUnit.YEARS.between(agent.getDateEmbauche(), aujourdhui);
            if (annees >= 25) {
                total = total.add(BigDecimal.valueOf(7));
            } else if (annees >= 20) {
                total = total.add(BigDecimal.valueOf(3));
            } else if (annees >= 15) {
                total = total.add(BigDecimal.valueOf(2));
            } else if (annees >= 10) {
                total = total.add(BigDecimal.valueOf(1));
            }
        }

        // ===== RÈGLES MÈRES : femmes uniquement, enfants de moins de 14 ans =====
        boolean estFemme = agent.getSexe() != null && agent.getSexe().equalsIgnoreCase("F");
        if (estFemme && agent.getEnfants() != null && !agent.getEnfants().isEmpty()) {

            // Compter les enfants de moins de 14 ans
            long nbEnfantsMoins14 = agent.getEnfants().stream()
                .filter(e -> e.getDateNaissance() != null)
                .filter(e -> ChronoUnit.YEARS.between(e.getDateNaissance(), aujourdhui) < 14)
                .count();

            // --- RÈGLE 2a : +1 jour par enfant de moins de 14 ans ---
            total = total.add(BigDecimal.valueOf(nbEnfantsMoins14));

            // --- RÈGLE 2c : +2 jours par enfant à partir du 4ème (enfants < 14 ans) ---
            if (nbEnfantsMoins14 >= 4) {
                long enfantsAuDela3 = nbEnfantsMoins14 - 3;  // le 4ème et au-delà
                total = total.add(BigDecimal.valueOf(enfantsAuDela3 * 2));
            }
        }

        return total;
    }

}