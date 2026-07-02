package sga_backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    // TEST : "0 * * * * *" (toutes les minutes)
    // ============================================================
    @Scheduled(cron = "0 0 2 * * *")
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
    public boolean recalculerSoldeAgent(Agent agent) {
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

        soldeCongesRepository.save(solde);
        return true;
    }

    // L'agent peut-il prendre un congé à cette date ? (période de référence de 12 mois atteinte)
    public boolean peutPrendreConge(Agent agent, LocalDate dateDebutConge) {
        if (agent.getDateEmbauche() == null) return false;
        LocalDate dateJouissance = agent.getDateEmbauche().plusMonths(MOIS_PERIODE_REFERENCE);
        return !dateDebutConge.isBefore(dateJouissance);
    }
}