package sga_backend.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sga_backend.dto.CreerDemandeRequest;
import sga_backend.dto.DemandeCongeDTO;
import sga_backend.entity.Agent;
import sga_backend.entity.DemandeConge;
import sga_backend.entity.EtapeValidation;
import sga_backend.entity.SoldeConges;
import sga_backend.entity.TypeConge;
import sga_backend.entity.Utilisateur;
import sga_backend.repository.AgentRepository;
import sga_backend.repository.DemandeCongeRepository;
import sga_backend.repository.EtapeValidationRepository;
import sga_backend.repository.SoldeCongesRepository;
import sga_backend.repository.TypeCongeRepository;
import sga_backend.repository.UniteRepository;
import sga_backend.repository.UtilisateurRepository;

@Service
public class DemandeCongeService {

    @Autowired private DemandeCongeRepository demandeCongeRepository;
    @Autowired private TypeCongeRepository typeCongeRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private JourFerieService jourFerieService;
    @Autowired private SoldeCongesService soldeCongesService;
    @Autowired private CircuitValidationService circuitValidationService;
    @Autowired private SoldeCongesRepository soldeCongesRepository;
    // Nouvelle injection (avec les autres @Autowired)
    @Autowired private EtapeValidationRepository etapeValidationRepository;
    @Autowired private UniteRepository uniteRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private EmailService emailService;
    

    @Transactional
    public DemandeCongeDTO creerDemande(CreerDemandeRequest request) {

        Agent agent = agentRepository.findById(request.getIdAgent())
            .orElseThrow(() -> new RuntimeException("Agent introuvable"));

        TypeConge type = typeCongeRepository.findById(request.getIdTypeConge())
            .orElseThrow(() -> new RuntimeException("Type de congé introuvable"));

        if (request.getDateDebut() == null || request.getDateFin() == null) {
            throw new RuntimeException("Les dates de début et de fin sont obligatoires");
        }
        if (request.getDateFin().isBefore(request.getDateDebut())) {
            throw new RuntimeException("La date de fin ne peut pas précéder la date de début");
        }
        if (request.getDateDebut().isBefore(LocalDate.now())) {
            throw new RuntimeException("La date de début ne peut pas être dans le passé");
        }

        // Règle de jouissance : l'agent doit avoir 12 mois d'ancienneté à la date de début du congé.
        // (On n'applique cette règle qu'aux congés DEDUCTIBLE, qui puisent dans le solde annuel.)
        if ("DEDUCTIBLE".equalsIgnoreCase(type.getCategorie())) {
            if (!soldeCongesService.peutPrendreConge(agent, request.getDateDebut())) {
                throw new RuntimeException(
                    "L'agent ne peut pas encore prendre de congé : la période de référence de 12 mois "
                    + "depuis la date d'embauche n'est pas atteinte à la date de début demandée.");
            }
        }

        if (Boolean.TRUE.equals(type.getReserveFemme())) {
            if (agent.getSexe() == null || !agent.getSexe().equalsIgnoreCase("F")) {
                throw new RuntimeException("Ce type de congé est réservé aux agents de sexe féminin");
            }
        }

        String momentDebut = request.getMomentDebut() != null ? request.getMomentDebut() : "JOURNEE";
        String momentFin   = request.getMomentFin()   != null ? request.getMomentFin()   : "JOURNEE";

        BigDecimal nbJours = calculerNombreJours(
            request.getDateDebut(), request.getDateFin(),
            Boolean.TRUE.equals(type.getCompteJoursOuvres()),
            momentDebut, momentFin
        );

        if (nbJours.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("La période sélectionnée ne contient aucun jour décompté");
        }

        // Règle : pour un congé DEDUCTIBLE, les congés échus doivent couvrir la durée demandée.
        // (nbJours est désormais calculé, on peut le comparer aux échus disponibles.)
        if ("DEDUCTIBLE".equalsIgnoreCase(type.getCategorie())) {
            SoldeConges solde = soldeCongesRepository.findByAgentId(agent.getId()).orElse(null);
            BigDecimal echus = (solde != null && solde.getNbCongesEchus() != null)
                ? solde.getNbCongesEchus() : BigDecimal.ZERO;

            if (echus.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException(
                    "Aucun congé échu disponible : l'agent ne peut pas encore prendre de congé déductible.");
            }
            if (nbJours.compareTo(echus) > 0) {
                throw new RuntimeException(
                    "Durée demandée (" + nbJours + " j) supérieure aux congés échus disponibles ("
                    + echus + " j).");
            }
        }

        if (type.getDureeMaximale() != null
            && nbJours.compareTo(BigDecimal.valueOf(type.getDureeMaximale())) > 0) {
            throw new RuntimeException("La durée demandée (" + nbJours + " j) dépasse le maximum autorisé ("
                + type.getDureeMaximale() + " j) pour ce type de congé");
        }

        DemandeConge demande = new DemandeConge();
        demande.setAgent(agent);
        demande.setTypeConge(type);
        demande.setDateDebut(request.getDateDebut());
        demande.setDateFin(request.getDateFin());
        demande.setMomentDebut(momentDebut);
        demande.setMomentFin(momentFin);
        demande.setNombreJours(nbJours);
        demande.setMotif(request.getMotif());
        demande.setStatut("EN_ATTENTE");

        // Demandeur (utilisateur connecté) — optionnel mais recommandé
        if (request.getIdDemandeur() != null) {
            Utilisateur demandeur = utilisateurRepository.findById(request.getIdDemandeur()).orElse(null);
            demande.setUtilisateurDemandeur(demandeur);
        }

       DemandeConge sauvegardee = demandeCongeRepository.save(demande);

        // Génère les étapes de validation selon le circuit (Siège ou Régional)
        circuitValidationService.genererEtapes(sauvegardee);

        // Notifier le premier validateur (première étape EN_ATTENTE)
        notifierPremierValidateur(sauvegardee);

        return toDto(sauvegardee);
    }

    // Notifie le premier validateur d'une demande nouvellement créée
    private void notifierPremierValidateur(DemandeConge demande) {
        List<EtapeValidation> etapes =
            etapeValidationRepository.findByDemandeIdOrderByOrdreAsc(demande.getId());
        EtapeValidation premiere = etapes.stream()
            .filter(e -> "EN_ATTENTE".equals(e.getStatut()))
            .findFirst().orElse(null);
        if (premiere == null) return;

        Utilisateur validateur = trouverValidateurDeLEtape(premiere);
        if (validateur == null || validateur.getAgent() == null) return;

        Agent agentDemandeur = demande.getAgent();
        String msg = "Une demande de congé de " + agentDemandeur.getPrenom() + " " + agentDemandeur.getNom()
                   + " attend votre validation.";
        notificationService.creer(validateur.getId(), msg, "DEMANDE_A_VALIDER", demande.getId());

        if (validateur.getAgent().getEmail() != null) {
            emailService.envoyer(validateur.getAgent().getEmail(),
                "Une demande de congé attend votre validation",
                "Bonjour " + validateur.getAgent().getPrenom() + ",\n\n"
                + "Une demande de congé de " + agentDemandeur.getPrenom() + " " + agentDemandeur.getNom()
                + " attend votre validation.\n"
                + "Période : " + demande.getDateDebut() + " au " + demande.getDateFin()
                + " (" + demande.getNombreJours() + " jour(s))\n\n"
                + "Merci de la traiter dans l'application SGA.\n\nSGA - SEN-CSU");
        }
    }

    // Retrouve l'utilisateur qui doit traiter une étape (selon profil + ciblage unité/direction)
    private Utilisateur trouverValidateurDeLEtape(EtapeValidation etape) {
        List<Utilisateur> candidats = utilisateurRepository.findAll();
        for (Utilisateur u : candidats) {
            if (u.getProfil() == null || u.getAgent() == null) continue;
            if (!etape.getProfilAttendu().equalsIgnoreCase(u.getProfil().getCode())) continue;

            if (etape.getIdUniteAttendue() != null) {
                if (u.getAgent().getUnite() != null
                    && etape.getIdUniteAttendue().equals(u.getAgent().getUnite().getId())) {
                    return u;
                }
                continue;
            }
            if (etape.getIdDirectionAttendue() != null) {
                if (u.getAgent().getDirection() != null
                    && etape.getIdDirectionAttendue().equals(u.getAgent().getDirection().getId())) {
                    return u;
                }
                continue;
            }
            return u;
        }
        return null;
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

    @Transactional
    public void enregistrerJustificatif(Long idDemande, String nomFichier) {
        DemandeConge demande = demandeCongeRepository.findById(idDemande)
            .orElseThrow(() -> new RuntimeException("Demande introuvable"));
        demande.setJustificatif(nomFichier);
        demandeCongeRepository.save(demande);
    }

    public List<DemandeCongeDTO> getDemandesAgent(Long idAgent) {
        List<DemandeCongeDTO> liste = new ArrayList<>();
        for (DemandeConge d : demandeCongeRepository.findByAgentIdOrderByDateCreationDesc(idAgent)) {
            liste.add(toDto(d));
        }
        return liste;
    }

    public DemandeCongeDTO getDemande(Long id) {
        DemandeConge d = demandeCongeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Demande introuvable"));
        return toDto(d);
    }

    private DemandeCongeDTO toDto(DemandeConge d) {
        DemandeCongeDTO dto = new DemandeCongeDTO();
        dto.setId(d.getId());

        if (d.getAgent() != null) {
            dto.setIdAgent(d.getAgent().getId());
            dto.setNomAgent(d.getAgent().getNom());
            dto.setPrenomAgent(d.getAgent().getPrenom());
            dto.setMatriculeAgent(d.getAgent().getMatricule());
            dto.setSexeAgent(d.getAgent().getSexe());
            if (d.getAgent().getDirection() != null) {
                dto.setDirectionAgent(d.getAgent().getDirection().getLibelle());
            }
            if (d.getAgent().getUnite() != null) {
                dto.setDivisionAgent(d.getAgent().getUnite().getLibelle());
            }
          dto.setPosteAgent(d.getAgent().getPosteOccupe() != null ? d.getAgent().getPosteOccupe().getLibelle() : null);
            utilisateurRepository.findByAgentId(d.getAgent().getId()).ifPresent(u -> {
                if (u.getProfil() != null) {
                    dto.setProfilAgent(u.getProfil().getLibelle());
                }
            });
                        // Type de lieu de l'agent (Siège / Région)
            if (d.getAgent().getUnite() != null 
                && d.getAgent().getUnite().getLieuTravail() != null
                && d.getAgent().getUnite().getLieuTravail().getTypeEntite() != null) {
                dto.setTypeLieuAgent(d.getAgent().getUnite().getLieuTravail().getTypeEntite().name());
            } else {
                dto.setTypeLieuAgent("SIEGE");  // défaut pour les agents sans unité
            }
        }

        if (d.getUtilisateurDemandeur() != null) {
            dto.setIdDemandeur(d.getUtilisateurDemandeur().getId());
            dto.setNomDemandeur(d.getUtilisateurDemandeur().getNom() + " " + d.getUtilisateurDemandeur().getPrenom());
        }

        if (d.getTypeConge() != null) {
            dto.setIdTypeConge(d.getTypeConge().getId());
            dto.setLibelleTypeConge(d.getTypeConge().getLibelle());
            dto.setCodeTypeConge(d.getTypeConge().getCode());
            dto.setCategorieTypeConge(d.getTypeConge().getCategorie());
        }

        dto.setDateDebut(d.getDateDebut());
        dto.setDateFin(d.getDateFin());
        dto.setMomentDebut(d.getMomentDebut());
        dto.setMomentFin(d.getMomentFin());
        dto.setNombreJours(d.getNombreJours());
        dto.setMotif(d.getMotif());
        dto.setJustificatif(d.getJustificatif());
        dto.setStatut(d.getStatut());
        dto.setDateCreation(d.getDateCreation());

        return dto;
    }

    // ============================================================
    // HISTORIQUE (admin) — toutes les demandes, avec étape courante
    // ============================================================
   // ============================================================
    // HISTORIQUE filtré selon le profil de l'utilisateur connecté
    // ADMIN : toutes les demandes
    // Autres validateurs : les demandes dont une étape cible leur unité/direction
    // ============================================================
   public List<DemandeCongeDTO> getHistorique(Long idUtilisateur) {
        Utilisateur user = utilisateurRepository.findById(idUtilisateur).orElse(null);
        if (user == null || user.getProfil() == null) {
            return new ArrayList<>();
        }

        String codeProfil = user.getProfil().getCode();

        // ADMIN et haute direction (DG, SG, DAF) voient tout
        if ("ADMIN".equalsIgnoreCase(codeProfil)
            || "DG".equalsIgnoreCase(codeProfil)
            || "SG".equalsIgnoreCase(codeProfil)
            || "DAF".equalsIgnoreCase(codeProfil)) {
            List<DemandeCongeDTO> liste = new ArrayList<>();
            for (DemandeConge d : demandeCongeRepository.findAllByOrderByDateCreationDesc()) {
                liste.add(toDtoAvecEtape(d));
            }
            return liste;
        }

        // Direction de l'utilisateur connecté (via son agent)
        Long idDirectionUser = null;
        if (user.getAgent() != null && user.getAgent().getDirection() != null) {
            idDirectionUser = user.getAgent().getDirection().getId();
        }

        // Si l'utilisateur n'a pas de direction, il ne voit que ses propres demandes
        List<DemandeCongeDTO> liste = new ArrayList<>();
        for (DemandeConge d : demandeCongeRepository.findAllByOrderByDateCreationDesc()) {
            Agent demandeur = d.getAgent();
            if (demandeur == null) continue;

            Long idDirectionDemandeur = (demandeur.getDirection() != null)
                ? demandeur.getDirection().getId() : null;

            // On garde la demande si le demandeur est dans la même direction que l'utilisateur
            if (idDirectionUser != null && idDirectionUser.equals(idDirectionDemandeur)) {
                liste.add(toDtoAvecEtape(d));
            }
        }
        return liste;
    }

    // Variante de toDto qui ajoute l'étape courante (« chez qui est la demande »)
   // Variante de toDto qui ajoute l'étape courante (« chez qui est la demande »)
    private DemandeCongeDTO toDtoAvecEtape(DemandeConge d) {
        DemandeCongeDTO dto = toDto(d);

        String statut = d.getStatut();
        boolean enCours = "EN_ATTENTE".equals(statut) || "EN_COURS".equals(statut);
        if (enCours) {
            List<EtapeValidation> etapes =
                etapeValidationRepository.findByDemandeIdOrderByOrdreAsc(d.getId());
            EtapeValidation courante = etapes.stream()
                .filter(e -> "EN_ATTENTE".equals(e.getStatut()))
                .findFirst()
                .orElse(null);
            if (courante != null) {
                dto.setLibelleEtapeCourante(courante.getLibelle());
                dto.setProfilAttenduCourant(courante.getProfilAttendu());
                dto.setOrdreEtapeCourante(courante.getOrdre());

                // Nom de l'unité ciblée par l'étape (pour afficher "Chef de Division Réseaux")
                if (courante.getIdUniteAttendue() != null) {
                    uniteRepository.findById(courante.getIdUniteAttendue())
                        .ifPresent(u -> dto.setUniteEtapeCourante(u.getLibelle()));
                }

                if (courante.getValidateur() != null) {
                    dto.setNomValidateurAttendu(
                        courante.getValidateur().getNom() + " " + courante.getValidateur().getPrenom());
                }
            }
        }
        return dto;
    }
    // ============================================================
    // EXPORT EXCEL de l'historique (filtré côté frontend par IDs)
    // ============================================================
    public byte[] exportHistorique(List<Long> ids) {
        // Si ids null/vide -> on exporte tout l'historique
        List<DemandeConge> demandes;
        if (ids == null || ids.isEmpty()) {
            demandes = demandeCongeRepository.findAllByOrderByDateCreationDesc();
        } else {
            demandes = demandeCongeRepository.findAllById(ids);
        }

        DateTimeFormatter fDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Historique demandes");

            // Style en-tête
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            String[] entetes = {
                "Matricule", "Agent", "Type de congé", "Catégorie",
                "Date début", "Date fin", "Nb jours", "Statut", "Étape actuelle", "Date création"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < entetes.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(entetes[i]);
                c.setCellStyle(headerStyle);
            }

            int r = 1;
            for (DemandeConge d : demandes) {
                DemandeCongeDTO dto = toDtoAvecEtape(d);
                Row row = sheet.createRow(r++);

                row.createCell(0).setCellValue(nvl(dto.getMatriculeAgent()));
                row.createCell(1).setCellValue(nvl(dto.getPrenomAgent()) + " " + nvl(dto.getNomAgent()));
                row.createCell(2).setCellValue(nvl(dto.getLibelleTypeConge()));
                row.createCell(3).setCellValue(nvl(dto.getCategorieTypeConge()));
                row.createCell(4).setCellValue(dto.getDateDebut() != null ? dto.getDateDebut().format(fDate) : "");
                row.createCell(5).setCellValue(dto.getDateFin() != null ? dto.getDateFin().format(fDate) : "");
                row.createCell(6).setCellValue(dto.getNombreJours() != null ? dto.getNombreJours().doubleValue() : 0);
                row.createCell(7).setCellValue(libelleStatut(dto.getStatut()));

                // Étape actuelle : « chez qui » ou tiret si terminé
                String etapeTxt = dto.getLibelleEtapeCourante() != null
                    ? dto.getLibelleEtapeCourante() : "—";
                row.createCell(8).setCellValue(etapeTxt);

                row.createCell(9).setCellValue(
                    dto.getDateCreation() != null ? dto.getDateCreation().format(fDate) : "");
            }

            for (int i = 0; i < entetes.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération de l'export Excel : " + e.getMessage());
        }
    }

        public byte[] exportDashboard(List<Long> ids) {
        List<DemandeConge> demandes;
        if (ids == null || ids.isEmpty()) {
            demandes = demandeCongeRepository.findAllByOrderByDateCreationDesc();
        } else {
            demandes = demandeCongeRepository.findAllById(ids);
        }

        DateTimeFormatter fDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Styles réutilisables
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle titreStyle = wb.createCellStyle();
            Font titreFont = wb.createFont();
            titreFont.setBold(true);
            titreFont.setFontHeightInPoints((short) 13);
            titreFont.setColor(IndexedColors.DARK_GREEN.getIndex());
            titreStyle.setFont(titreFont);

            CellStyle labelStyle = wb.createCellStyle();
            Font labelFont = wb.createFont();
            labelFont.setBold(true);
            labelStyle.setFont(labelFont);

            // ===== FEUILLE 1 : DEMANDES (toujours présente) =====
            construireFeuilleDemandes(wb, headerStyle, demandes, fDate);

            // ===== Détecter le nombre d'agents distincts =====
            Set<Long> agentsDistincts = new HashSet<>();
            for (DemandeConge d : demandes) {
                if (d.getAgent() != null) agentsDistincts.add(d.getAgent().getId());
            }

            if (agentsDistincts.size() == 1 && !demandes.isEmpty()) {
                // ===== CAS 1 : UN SEUL AGENT -> fiche individuelle =====
                construireFicheAgent(wb, titreStyle, labelStyle, headerStyle, demandes, fDate);
            } else {
                // ===== CAS 2 : PLUSIEURS AGENTS -> statistiques =====
                construireStatistiques(wb, titreStyle, labelStyle, headerStyle, demandes);
            }

            wb.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'export dashboard : " + e.getMessage());
        }
    }

    // ---- Feuille liste des demandes ----
    private void construireFeuilleDemandes(XSSFWorkbook wb, CellStyle headerStyle,
                                           List<DemandeConge> demandes, DateTimeFormatter fDate) {
        Sheet sheet = wb.createSheet("Demandes");
        String[] entetes = {"Agent", "Demandeur", "Matricule", "Direction",
                "Type de congé", "Date début", "Date fin", "Nb jours", "Statut"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < entetes.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(entetes[i]);
            c.setCellStyle(headerStyle);
        }
        int r = 1;
        for (DemandeConge d : demandes) {
            DemandeCongeDTO dto = toDto(d);
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(nvl(dto.getPrenomAgent()) + " " + nvl(dto.getNomAgent()));
            row.createCell(1).setCellValue(nvl(dto.getNomDemandeur()));
            row.createCell(2).setCellValue(nvl(dto.getMatriculeAgent()));
            row.createCell(3).setCellValue(nvl(dto.getDirectionAgent()));
            row.createCell(4).setCellValue(nvl(dto.getLibelleTypeConge()));
            row.createCell(5).setCellValue(dto.getDateDebut() != null ? dto.getDateDebut().format(fDate) : "");
            row.createCell(6).setCellValue(dto.getDateFin() != null ? dto.getDateFin().format(fDate) : "");
            row.createCell(7).setCellValue(dto.getNombreJours() != null ? dto.getNombreJours().doubleValue() : 0);
            row.createCell(8).setCellValue(libelleStatut(dto.getStatut()));
        }
        for (int i = 0; i < entetes.length; i++) sheet.autoSizeColumn(i);
    }

    // ---- Feuille statistiques (plusieurs agents) ----
    private void construireStatistiques(XSSFWorkbook wb, CellStyle titreStyle, CellStyle labelStyle,
                                        CellStyle headerStyle, List<DemandeConge> demandes) {
        Sheet sheet = wb.createSheet("Statistiques");
        int r = 0;

        Row titre = sheet.createRow(r++);
        Cell tc = titre.createCell(0);
        tc.setCellValue("Synthèse statistique — " + demandes.size() + " demande(s)");
        tc.setCellStyle(titreStyle);
        r++;

        // Comptages
        Map<String, Integer> parStatut = new LinkedHashMap<>();
        Map<String, Integer> parType = new LinkedHashMap<>();
        Map<String, Integer> parSexe = new LinkedHashMap<>();
        Map<String, Integer> parDirection = new LinkedHashMap<>();
        for (DemandeConge d : demandes) {
            DemandeCongeDTO dto = toDto(d);
            parStatut.merge(libelleStatut(dto.getStatut()), 1, Integer::sum);
            parType.merge(nvl(dto.getLibelleTypeConge()).isEmpty() ? "Non défini" : dto.getLibelleTypeConge(), 1, Integer::sum);
            parSexe.merge("M".equals(dto.getSexeAgent()) ? "Masculin" : "F".equals(dto.getSexeAgent()) ? "Féminin" : "Non défini", 1, Integer::sum);
            parDirection.merge(nvl(dto.getDirectionAgent()).isEmpty() ? "Non définie" : dto.getDirectionAgent(), 1, Integer::sum);
        }

        r = ecrireSection(sheet, r, "Total", Map.of("Nombre de demandes", demandes.size()), labelStyle, headerStyle);
        r = ecrireSection(sheet, r, "Par statut", parStatut, labelStyle, headerStyle);
        r = ecrireSection(sheet, r, "Par type de congé", parType, labelStyle, headerStyle);
        r = ecrireSection(sheet, r, "Par sexe", parSexe, labelStyle, headerStyle);
        // "Par direction" seulement si plus d'une direction (sinon inutile)
        if (parDirection.size() > 1) {
            r = ecrireSection(sheet, r, "Par direction", parDirection, labelStyle, headerStyle);
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    // ---- Écrit une section titre + tableau clé/valeur ----
    private int ecrireSection(Sheet sheet, int r, String titre, Map<String, ? extends Number> data,
                              CellStyle labelStyle, CellStyle headerStyle) {
        Row titreRow = sheet.createRow(r++);
        Cell tc = titreRow.createCell(0);
        tc.setCellValue(titre);
        tc.setCellStyle(headerStyle);
        Cell tc2 = titreRow.createCell(1);
        tc2.setCellStyle(headerStyle);
        for (Map.Entry<String, ? extends Number> e : data.entrySet()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(e.getKey());
            row.createCell(1).setCellValue(e.getValue().doubleValue());
        }
        r++; // ligne vide
        return r;
    }

    // ---- Feuille fiche individuelle (un seul agent) ----
    private void construireFicheAgent(XSSFWorkbook wb, CellStyle titreStyle, CellStyle labelStyle,
                                      CellStyle headerStyle, List<DemandeConge> demandes, DateTimeFormatter fDate) {
        Sheet sheet = wb.createSheet("Fiche agent");
        DemandeConge premiere = demandes.get(0);
        DemandeCongeDTO info = toDto(premiere);
        int r = 0;

        Row titre = sheet.createRow(r++);
        Cell tc = titre.createCell(0);
        tc.setCellValue("Fiche congés — " + nvl(info.getPrenomAgent()) + " " + nvl(info.getNomAgent()));
        tc.setCellStyle(titreStyle);
        r++;

        // Infos agent
        r = ligneInfo(sheet, r, "Matricule", nvl(info.getMatriculeAgent()), labelStyle);
        r = ligneInfo(sheet, r, "Direction", nvl(info.getDirectionAgent()), labelStyle);
        r++;

        // Solde
        if (premiere.getAgent() != null) {
            soldeCongesRepository.findByAgentId(premiere.getAgent().getId()).ifPresent(s -> {
                // (rempli plus bas via variable, voir note)
            });
        }

        // Ses demandes
        Row entete = sheet.createRow(r++);
        String[] cols = {"Type", "Date début", "Date fin", "Nb jours", "Statut"};
        for (int i = 0; i < cols.length; i++) {
            Cell c = entete.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(headerStyle);
        }
        double totalJours = 0;
        for (DemandeConge d : demandes) {
            DemandeCongeDTO dto = toDto(d);
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(nvl(dto.getLibelleTypeConge()));
            row.createCell(1).setCellValue(dto.getDateDebut() != null ? dto.getDateDebut().format(fDate) : "");
            row.createCell(2).setCellValue(dto.getDateFin() != null ? dto.getDateFin().format(fDate) : "");
            double j = dto.getNombreJours() != null ? dto.getNombreJours().doubleValue() : 0;
            row.createCell(3).setCellValue(j);
            row.createCell(4).setCellValue(libelleStatut(dto.getStatut()));
            totalJours += j;
        }
        Row totalRow = sheet.createRow(r++);
        Cell lc = totalRow.createCell(0);
        lc.setCellValue("Total jours pris");
        lc.setCellStyle(labelStyle);
        totalRow.createCell(3).setCellValue(totalJours);

        for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
    }

    private int ligneInfo(Sheet sheet, int r, String label, String valeur, CellStyle labelStyle) {
        Row row = sheet.createRow(r++);
        Cell l = row.createCell(0);
        l.setCellValue(label);
        l.setCellStyle(labelStyle);
        row.createCell(1).setCellValue(valeur);
        return r;
    }
    private String nvl(String s) { return s != null ? s : ""; }

    private String libelleStatut(String s) {
        if (s == null) return "";
        switch (s) {
            case "EN_ATTENTE": return "En attente";
            case "EN_COURS":   return "En cours";
            case "VALIDEE":    return "Validée";
            case "REFUSEE":    return "Refusée";
            default:           return s;
        }
    }

}