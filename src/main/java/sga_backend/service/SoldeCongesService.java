package sga_backend.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import sga_backend.dto.RegularisationDTO;
import sga_backend.dto.SoldeAjustementDTO;
import sga_backend.dto.SoldeRegularisationDTO;
import sga_backend.entity.Agent;
import sga_backend.entity.Regularisation;
import sga_backend.entity.SoldeConges;
import sga_backend.repository.AgentRepository;
import sga_backend.repository.DemandeCongeRepository;
import sga_backend.repository.SoldeCongesRepository;
import sga_backend.repository.RegularisationRepository;


import java.util.Map;




@Service
public class SoldeCongesService {

    private static final Logger log = LoggerFactory.getLogger(SoldeCongesService.class);

    @Autowired private AgentRepository agentRepository;
    @Autowired private SoldeCongesRepository soldeCongesRepository;
    @Autowired private JourFerieService jourFerieService;
    @Autowired private RegularisationRepository regularisationRepository;
    @Autowired private DemandeCongeRepository demandeCongeRepository;

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

        // --- Jours déjà pris (déductibles) ---
        BigDecimal joursPris = solde.getNbJourPrisDeductible() != null
            ? solde.getNbJourPrisDeductible()
            : BigDecimal.ZERO;

        // --- Congés supplémentaires (ancienneté + mères) ---
        BigDecimal congesSupp = calculerCongesSupplementaires(agent);
        solde.setNbCongesSupplementaire(congesSupp);

        // --- Congés échus = (années complètes × 24) − jours pris, puis plafonnés ---
        BigDecimal joursEchusBruts = JOURS_PAR_AN.multiply(BigDecimal.valueOf(anneesCompletes));
        BigDecimal joursEchus = joursEchusBruts.subtract(joursPris);
        if (joursEchus.compareTo(BigDecimal.ZERO) < 0) {
            joursEchus = BigDecimal.ZERO;
        }
        // Plafond : (24 × 3) + congés supplémentaires  (VISION A : le supplément est INCLUS dans les échus)
        BigDecimal plafondEchus = JOURS_PAR_AN.multiply(BigDecimal.valueOf(3)).add(congesSupp);
        if (joursEchus.compareTo(plafondEchus) > 0) {
            joursEchus = plafondEchus;
        }
        solde.setNbCongesEchus(joursEchus);

        // --- Reliquat = congés échus (qui contiennent DÉJÀ le supplément) − jours pris ---
        // On ne rajoute PAS le supplément ici (il est déjà dans joursEchus), sinon double comptage.
        // Le reliquat PEUT être négatif (congés pris au-delà du droit), on ne le force pas à 0.
        BigDecimal reliquat = joursEchus.subtract(joursPris);
        solde.setNbCongesReliquat(reliquat);

        solde.setDateDernierCalcul(LocalDateTime.now());
        solde.setDateMaj(LocalDateTime.now());

        soldeCongesRepository.save(solde);
        return true;
    }


    @Transactional
public void rattraperJoursPris() {
    List<Agent> agents = agentRepository.findAll();
    for (Agent agent : agents) {
        // Somme des jours des demandes VALIDEE déductibles de cet agent
        BigDecimal totalPris = demandeCongeRepository
            .findByAgentIdAndStatut(agent.getId(), "VALIDEE")
            .stream()
            .filter(d -> d.getTypeConge() != null
                      && "DEDUCTIBLE".equalsIgnoreCase(d.getTypeConge().getCategorie()))
            .map(d -> d.getNombreJours() != null ? d.getNombreJours() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        soldeCongesRepository.findByAgentId(agent.getId()).ifPresent(solde -> {
            solde.setNbJourPrisDeductible(totalPris);
            soldeCongesRepository.save(solde);
            recalculerSoldeAgent(agent);
        });
    }
}


       public BigDecimal calculerEchusALaDate(Agent agentParam, LocalDate date) {
        if (agentParam == null || agentParam.getId() == null) return BigDecimal.ZERO;

        // Recharger l'agent AVEC ses enfants (nécessaire à calculerCongesSupplementaires)
        Agent agent = agentRepository.findByIdWithEnfants(agentParam.getId()).orElse(agentParam);

        if (agent.getDateEmbauche() == null) return BigDecimal.ZERO;
        if (date == null) return BigDecimal.ZERO;

        long anneesCompletes = ChronoUnit.YEARS.between(agent.getDateEmbauche(), date);
        if (anneesCompletes < 0) anneesCompletes = 0;

        SoldeConges solde = soldeCongesRepository.findByAgentId(agent.getId()).orElse(null);
        BigDecimal joursPris = (solde != null && solde.getNbJourPrisDeductible() != null)
            ? solde.getNbJourPrisDeductible() : BigDecimal.ZERO;

        BigDecimal echus = JOURS_PAR_AN.multiply(BigDecimal.valueOf(anneesCompletes)).subtract(joursPris);
        if (echus.compareTo(BigDecimal.ZERO) < 0) {
            echus = BigDecimal.ZERO;
        }
        // Plafond : (24 × 3) + congés supplémentaires  (VISION A : le supplément est INCLUS dans les échus)
        BigDecimal supplement = calculerCongesSupplementaires(agent);
        BigDecimal plafond = JOURS_PAR_AN.multiply(BigDecimal.valueOf(3)).add(supplement);
        if (echus.compareTo(plafond) > 0) {
            echus = plafond;
        }
        return echus;
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
        dto.setDateEmbauche(agent.getDateEmbauche());

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
public void ajusterSolde(Long idAgent, SoldeAjustementDTO data, String effectuePar) {
    Agent agent = agentRepository.findById(idAgent)
        .orElseThrow(() -> new RuntimeException("Agent introuvable"));

    if (data.getDateDebut() == null || data.getDateFin() == null) {
        throw new RuntimeException("Les dates de début et de fin sont obligatoires");
    }
    if (data.getDateFin().isBefore(data.getDateDebut())) {
        throw new RuntimeException("La date de fin ne peut pas précéder la date de début");
    }

    String momentDebut = data.getMomentDebut() != null ? data.getMomentDebut() : "JOURNEE";
    String momentFin   = data.getMomentFin()   != null ? data.getMomentFin()   : "JOURNEE";

    // Jours ouvrables (week-ends + fériés exclus)
    BigDecimal nbJours = calculerNombreJours(
        data.getDateDebut(), data.getDateFin(), true, momentDebut, momentFin);

    if (nbJours.compareTo(BigDecimal.ZERO) <= 0) {
        throw new RuntimeException("La période sélectionnée ne contient aucun jour ouvrable");
    }

    SoldeConges solde = soldeCongesRepository.findByAgentId(idAgent)
        .orElseGet(() -> {
            SoldeConges s = new SoldeConges();
            s.setAgent(agent);
            return s;
        });

    // On AJOUTE les jours régularisés aux jours déjà pris
    BigDecimal prisActuel = (solde.getNbJourPrisDeductible() != null)
        ? solde.getNbJourPrisDeductible() : BigDecimal.ZERO;
    solde.setNbJourPrisDeductible(prisActuel.add(nbJours));
    solde.setDateMaj(java.time.LocalDateTime.now());
    soldeCongesRepository.save(solde);

    // Historiser
    Regularisation reg = new Regularisation();
    reg.setAgent(agent);
    reg.setDateDebut(data.getDateDebut());
    reg.setDateFin(data.getDateFin());
    reg.setMomentDebut(momentDebut);
    reg.setMomentFin(momentFin);
    reg.setNbJours(nbJours);
    reg.setDateOperation(java.time.LocalDateTime.now());
    reg.setEffectuePar(effectuePar);
    regularisationRepository.save(reg);

    // Recalcul du solde
    recalculerSoldeAgent(agent);
}

public BigDecimal calculerNombreJours(LocalDate debut, LocalDate fin, boolean joursOuvres,
                                          String momentDebut, String momentFin) {
        int compteur = 0;
        LocalDate jour = debut;
        while (!jour.isAfter(fin)) {
            if (joursOuvres) {
                DayOfWeek j = jour.getDayOfWeek();
                boolean estWeekend = (j == DayOfWeek.SATURDAY || j == DayOfWeek.SUNDAY);
                boolean estFerie = jourFerieService.estFerie(jour);
                if (!estWeekend && !estFerie) compteur++;
            } else {
                compteur++;
            }
            jour = jour.plusDays(1);
        }

        BigDecimal total = BigDecimal.valueOf(compteur);
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            if (debut.isEqual(fin)) {
                if (!"JOURNEE".equals(momentDebut) || !"JOURNEE".equals(momentFin)) {
                    total = BigDecimal.valueOf(0.5);
                }
            } else {
                if ("APRES_MIDI".equals(momentDebut)) total = total.subtract(BigDecimal.valueOf(0.5));
                if ("MATIN".equals(momentFin))        total = total.subtract(BigDecimal.valueOf(0.5));
            }
        }
        return total;
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
    public  BigDecimal calculerCongesSupplementaires(Agent agent) {
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

    @Transactional
public ResultatImportRegul importerRegularisations(MultipartFile fichier, String effectuePar) throws Exception {
    ResultatImportRegul resultat = new ResultatImportRegul();
    DataFormatter fmt = new DataFormatter();

    // Lignes valides préparées en passe 1
    List<Object[]> valides = new ArrayList<>(); // [agent, dateDebut, dateFin, momentDebut, momentFin, nbJours]

    try (InputStream is = fichier.getInputStream();
         Workbook wb = new XSSFWorkbook(is)) {
        Sheet sheet = wb.getSheetAt(0);

        // En-têtes
        Row entete = sheet.getRow(0);
        Map<String, Integer> idx = new HashMap<>();
        if (entete != null) {
            for (int c = 0; c < entete.getLastCellNum(); c++) {
                Cell cell = entete.getCell(c);
                String nom = (cell == null) ? "" : fmt.formatCellValue(cell).trim().toLowerCase();
                if (!nom.isEmpty()) idx.put(nom, c);
            }
        }

        // PASSE 1 : validation
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String matricule = lire(fmt, row, idx, "matricule");
            String dDebut    = lire(fmt, row, idx, "datedebut");
            String dFin      = lire(fmt, row, idx, "datefin");
            String mDebut    = lire(fmt, row, idx, "momentdebut");
            String mFin      = lire(fmt, row, idx, "momentfin");

            if (matricule.isEmpty() && dDebut.isEmpty() && dFin.isEmpty()) continue;
            resultat.totalLignes++;
            int numLigne = i + 1;

            try {
                if (matricule.isEmpty()) throw new RuntimeException("Matricule manquant");
                Agent agent = agentRepository.findByMatricule(matricule)
                    .orElseThrow(() -> new RuntimeException("Employé introuvable : " + matricule));

                LocalDate dateDebut = parseDate(dDebut);
                LocalDate dateFin   = parseDate(dFin);
                if (dateDebut == null || dateFin == null)
                    throw new RuntimeException("Dates invalides");
                if (dateFin.isBefore(dateDebut))
                    throw new RuntimeException("Date de fin avant la date de début");

                String momentDebut = vide(mDebut) ? "JOURNEE" : mDebut.toUpperCase();
                String momentFin   = vide(mFin)   ? "JOURNEE" : mFin.toUpperCase();

                BigDecimal nbJours = calculerNombreJours(dateDebut, dateFin, true, momentDebut, momentFin);
                if (nbJours.compareTo(BigDecimal.ZERO) <= 0)
                    throw new RuntimeException("Aucun jour ouvrable dans la période");

                valides.add(new Object[]{agent, dateDebut, dateFin, momentDebut, momentFin, nbJours});

            } catch (Exception e) {
                resultat.erreurs++;
                resultat.messagesErreur.add("Ligne " + numLigne + " : " + e.getMessage());
            }
        }
    }

    // TOUT OU RIEN
    if (resultat.erreurs > 0) {
        resultat.crees = 0;
        resultat.messagesErreur.add(0, "Import annulé : " + resultat.erreurs
            + " erreur(s). Aucune régularisation enregistrée.");
        return resultat;
    }

    // PASSE 2 : création
    for (Object[] v : valides) {
        Agent agent = (Agent) v[0];
        LocalDate dateDebut = (LocalDate) v[1];
        LocalDate dateFin = (LocalDate) v[2];
        String momentDebut = (String) v[3];
        String momentFin = (String) v[4];
        BigDecimal nbJours = (BigDecimal) v[5];

        SoldeConges solde = soldeCongesRepository.findByAgentId(agent.getId())
            .orElseGet(() -> { SoldeConges s = new SoldeConges(); s.setAgent(agent); return s; });
        BigDecimal prisActuel = solde.getNbJourPrisDeductible() != null ? solde.getNbJourPrisDeductible() : BigDecimal.ZERO;
        solde.setNbJourPrisDeductible(prisActuel.add(nbJours));
        solde.setDateMaj(LocalDateTime.now());
        soldeCongesRepository.save(solde);

        Regularisation reg = new Regularisation();
        reg.setAgent(agent);
        reg.setDateDebut(dateDebut);
        reg.setDateFin(dateFin);
        reg.setMomentDebut(momentDebut);
        reg.setMomentFin(momentFin);
        reg.setNbJours(nbJours);
        reg.setDateOperation(LocalDateTime.now());
        reg.setEffectuePar(effectuePar);
        regularisationRepository.save(reg);

        recalculerSoldeAgent(agent);
        resultat.crees++;
    }
    return resultat;
}

// Classe résultat (peux la mettre en classe interne ou à part)
public static class ResultatImportRegul {
    public int totalLignes = 0, crees = 0, erreurs = 0;
    public List<String> messagesErreur = new ArrayList<>();
    // getters si besoin pour la sérialisation JSON (ou rends les champs publics suffit)
}

// Utilitaire de lecture de cellule par nom de colonne
private String lire(DataFormatter fmt, Row row, Map<String,Integer> idx, String col) {
    Integer i = idx.get(col);
    if (i == null) return "";
    Cell c = row.getCell(i);
    return c == null ? "" : fmt.formatCellValue(c).trim();
}
private boolean vide(String s) { return s == null || s.trim().isEmpty(); }
private LocalDate parseDate(String s) {
    if (s == null || s.trim().isEmpty()) return null;
    s = s.trim();
    // Essaie plusieurs formats courants
    String[] formats = {"dd/MM/yyyy", "yyyy-MM-dd", "d/M/yyyy", "dd-MM-yyyy"};
    for (String f : formats) {
        try {
            return LocalDate.parse(s, java.time.format.DateTimeFormatter.ofPattern(f));
        } catch (Exception ignored) {}
    }
    return null;
}

@Transactional(readOnly = true)
public List<RegularisationDTO> listerRegularisations() {
    return regularisationRepository.findAllByOrderByDateOperationDesc()
        .stream()
        .map(reg -> {
            RegularisationDTO dto = new RegularisationDTO();
            dto.setId(reg.getId());
            if (reg.getAgent() != null) {
                dto.setMatricule(reg.getAgent().getMatricule());
                dto.setNom(reg.getAgent().getNom());
                dto.setPrenom(reg.getAgent().getPrenom());
            }
            dto.setDateDebut(reg.getDateDebut());
            dto.setDateFin(reg.getDateFin());
            dto.setMomentDebut(reg.getMomentDebut());
            dto.setMomentFin(reg.getMomentFin());
            dto.setNbJours(reg.getNbJours());
            dto.setDateOperation(reg.getDateOperation());
            dto.setEffectuePar(reg.getEffectuePar());
            return dto;
        })
        .toList();
}

}