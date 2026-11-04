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

// POI - styles et mise en forme
import org.apache.poi.ss.usermodel.VerticalAlignment;

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

        // Garde-fous : ids obligatoires (évite findById(null) -> "The given id must not be null")
        if (request.getIdAgent() == null) {
            throw new RuntimeException("L'agent de la demande est obligatoire");
        }
        if (request.getIdTypeConge() == null) {
            throw new RuntimeException("Le type de congé est obligatoire");
        }

        // Agent chargé AVEC ses enfants (nécessaire aux règles d'échus / congés supplémentaires)
        Agent agent = agentRepository.findByIdWithEnfants(request.getIdAgent())
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

        // Empêcher les demandes qui se chevauchent pour le même agent
        List<DemandeConge> chevauchements = demandeCongeRepository.trouverChevauchements(
            agent.getId(), request.getDateDebut(), request.getDateFin());
        if (!chevauchements.isEmpty()) {
            DemandeConge existante = chevauchements.get(0);
            throw new RuntimeException(
                "Cet employé a déjà une demande de congé sur cette période (du "
                + existante.getDateDebut() + " au " + existante.getDateFin() + ").");
        }

        if (nbJours.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("La période sélectionnée ne contient aucun jour décompté");
        }

        // Règle : pour un congé DEDUCTIBLE, les congés échus (qui incluent DÉJÀ le
        // supplément — vision A) doivent couvrir la durée demandée.
        if ("DEDUCTIBLE".equalsIgnoreCase(type.getCategorie())) {
            // Congés échus projetés à la DATE DE DÉBUT (supplément déjà inclus dedans)
            BigDecimal disponible = soldeCongesService.calculerEchusALaDate(agent, request.getDateDebut());

            if (disponible.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException(
                    "Aucun congé disponible à la date de début demandée.");
            }
            if (nbJours.compareTo(disponible) > 0) {
                throw new RuntimeException(
                    "Durée demandée (" + nbJours + " j) supérieure au congé disponible à la date de début ("
                    + disponible + " j).");
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
           dto.setDateEmbaucheAgent(d.getAgent().getDateEmbauche());
            dto.setTypeContratAgent(d.getAgent().getTypeContrat());
            dto.setDateFinContratAgent(d.getAgent().getDateFinContrat());
            dto.setNbEnfantsAgent(d.getAgent().getNbEnfants());
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

    // tout le monde : toutes les demandes
    // ============================================================
      public List<DemandeCongeDTO> getHistorique(Long idUtilisateur) {
        // Accès ouvert : tout le monde voit l'historique complet de l'organisation
        List<DemandeCongeDTO> liste = new ArrayList<>();
        for (DemandeConge d : demandeCongeRepository.findAllByOrderByDateCreationDesc()) {
            liste.add(toDtoAvecEtape(d));
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
            construireFeuilleDemandes(wb, headerStyle, labelStyle, demandes, fDate);

            // ===== Détecter le nombre d'agents distincts =====
                       // ===== FEUILLE 2 : tableau officiel mensuel (toujours, 1 ou plusieurs agents) =====
            construireStatistiques(wb, titreStyle, labelStyle, headerStyle, demandes);

            wb.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'export dashboard : " + e.getMessage());
        }
    }

    // ---- Feuille liste des demandes ----
private void construireFeuilleDemandes(XSSFWorkbook wb, CellStyle headerStyle, CellStyle labelStyle,
                                       List<DemandeConge> demandes, DateTimeFormatter fDate) {
    Sheet sheet = wb.createSheet("Demandes");

    // ===== STYLES =====
    org.apache.poi.xssf.usermodel.XSSFCellStyle styleBande = wb.createCellStyle();
    org.apache.poi.xssf.usermodel.XSSFFont fBande = wb.createFont();
    fBande.setBold(true); fBande.setColor(IndexedColors.WHITE.getIndex()); fBande.setFontHeightInPoints((short)12);
    styleBande.setFont(fBande);
    styleBande.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)0x2c,(byte)0x3e,(byte)0x50}, null));
    styleBande.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    styleBande.setAlignment(HorizontalAlignment.CENTER);
    styleBande.setVerticalAlignment(VerticalAlignment.CENTER);

    org.apache.poi.xssf.usermodel.XSSFCellStyle styleEntete = wb.createCellStyle();
    org.apache.poi.xssf.usermodel.XSSFFont fEnt = wb.createFont();
    fEnt.setBold(true); fEnt.setColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)0x33,(byte)0x42,(byte)0x4f}, null));
    styleEntete.setFont(fEnt);
    styleEntete.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)0xe4,(byte)0xe9,(byte)0xee}, null));
    styleEntete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    styleEntete.setAlignment(HorizontalAlignment.CENTER);
    styleEntete.setVerticalAlignment(VerticalAlignment.CENTER);
    bordures(styleEntete);

    org.apache.poi.xssf.usermodel.XSSFCellStyle styleCell = wb.createCellStyle();
    styleCell.setVerticalAlignment(VerticalAlignment.CENTER);
    bordures(styleCell);

    org.apache.poi.xssf.usermodel.XSSFCellStyle styleCellAlt = wb.createCellStyle();
    styleCellAlt.setVerticalAlignment(VerticalAlignment.CENTER);
    styleCellAlt.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)0xf5,(byte)0xf7,(byte)0xf9}, null));
    styleCellAlt.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    bordures(styleCellAlt);

    org.apache.poi.xssf.usermodel.XSSFCellStyle styleEmp = wb.createCellStyle();
    org.apache.poi.xssf.usermodel.XSSFFont fEmp = wb.createFont(); fEmp.setBold(true);
    styleEmp.setFont(fEmp);
    styleEmp.setAlignment(HorizontalAlignment.CENTER);
    styleEmp.setVerticalAlignment(VerticalAlignment.CENTER);
    bordures(styleEmp);

    org.apache.poi.xssf.usermodel.XSSFCellStyle styleTotal = wb.createCellStyle();
    org.apache.poi.xssf.usermodel.XSSFFont fTot = wb.createFont(); fTot.setBold(true);
    styleTotal.setFont(fTot);
    styleTotal.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)0xff,(byte)0xf6,(byte)0xe0}, null));
    styleTotal.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    bordures(styleTotal);

    // 4 styles ÉTAT selon le statut
    org.apache.poi.xssf.usermodel.XSSFCellStyle styleEtatValide  = styleEtatCouleur(wb, new byte[]{(byte)0x1b,(byte)0x5e,(byte)0x20}, new byte[]{(byte)0xc8,(byte)0xe6,(byte)0xc9});
    org.apache.poi.xssf.usermodel.XSSFCellStyle styleEtatEnCours = styleEtatCouleur(wb, new byte[]{(byte)0x7a,(byte)0x3d,(byte)0x00}, new byte[]{(byte)0xff,(byte)0xcc,(byte)0x80});
    org.apache.poi.xssf.usermodel.XSSFCellStyle styleEtatRefuse  = styleEtatCouleur(wb, new byte[]{(byte)0x8e,(byte)0x1b,(byte)0x1b}, new byte[]{(byte)0xff,(byte)0xcd,(byte)0xd2});
    org.apache.poi.xssf.usermodel.XSSFCellStyle styleEtatAttente = styleEtatCouleur(wb, new byte[]{(byte)0x8a,(byte)0x4b,(byte)0x08}, new byte[]{(byte)0xff,(byte)0xe0,(byte)0xb2});

    String[] entetes = {"Nom", "Prénom", "Matricule", "Direction", "Division", "Poste",
            "Date embauche", "Type contrat", "Date fin contrat", "Nb enfants",
            "Type de congé", "Date début", "Date fin", "Nb jours", "Statut"};
    int totalCols = entetes.length;
    final int COL_NB_JOURS = 13;
    final int NB_COLS_EMP = 10; // Nom..Nb enfants (colonnes à fusionner)

    // ===== Ligne 0 : bande titre =====
    Row bande = sheet.createRow(0); bande.setHeightInPoints(22);
    Cell cb = bande.createCell(0);
    cb.setCellValue("SEN-CSU · DEMANDES DE CONGÉS");
    cb.setCellStyle(styleBande);
    for (int i = 1; i < totalCols; i++) bande.createCell(i).setCellStyle(styleBande);
    sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, totalCols - 1));

    // ===== Ligne 1 : en-têtes =====
    final int ligneEntete = 1;
    Row headerRow = sheet.createRow(ligneEntete);
    for (int i = 0; i < entetes.length; i++) {
        Cell c = headerRow.createCell(i); c.setCellValue(entetes[i]); c.setCellStyle(styleEntete);
    }

    // ===== Trier par agent =====
    List<DemandeCongeDTO> dtos = new ArrayList<>();
    for (DemandeConge d : demandes) dtos.add(toDto(d));
    dtos.sort(java.util.Comparator.comparing(dto -> dto.getIdAgent() != null ? dto.getIdAgent() : 0L));

    int r = ligneEntete + 1;
    Long agentCourant = null;
    String nomAgentCourant = "";
    double sousTotal = 0, totalGeneral = 0;
    int groupeIndex = 0;
    int rDebutGroupe = r;

    for (int i = 0; i < dtos.size(); i++) {
        DemandeCongeDTO dto = dtos.get(i);
        Long idAgent = dto.getIdAgent();
        boolean nouvelAgent = (agentCourant == null) || !agentCourant.equals(idAgent);

        if (nouvelAgent && agentCourant != null) {
            fusionnerColonnes(sheet, rDebutGroupe, r - 1, NB_COLS_EMP);
            r = ecrireSousTotalStyle(sheet, r, styleTotal, nomAgentCourant, sousTotal, COL_NB_JOURS, totalCols);
            sousTotal = 0;
            groupeIndex++;
            rDebutGroupe = r;
        }
        agentCourant = idAgent;
        nomAgentCourant = nvl(dto.getPrenomAgent()) + " " + nvl(dto.getNomAgent());

        CellStyle stDemande = (groupeIndex % 2 == 0) ? styleCell : styleCellAlt;
        Row row = sheet.createRow(r);
        boolean premiere = (r == rDebutGroupe);
        r++;

        int c = 0;
        if (premiere) {
            creerC(row, c++, nvl(dto.getNomAgent()), styleEmp);
            creerC(row, c++, nvl(dto.getPrenomAgent()), styleEmp);
            creerC(row, c++, nvl(dto.getMatriculeAgent()), styleEmp);
            creerC(row, c++, nvl(dto.getDirectionAgent()), styleEmp);
            creerC(row, c++, nvl(dto.getDivisionAgent()), styleEmp);
            creerC(row, c++, nvl(dto.getPosteAgent()), styleEmp);
            creerC(row, c++, dto.getDateEmbaucheAgent() != null ? dto.getDateEmbaucheAgent().format(fDate) : "", styleEmp);
            creerC(row, c++, nvl(dto.getTypeContratAgent()), styleEmp);
            creerC(row, c++, dto.getDateFinContratAgent() != null ? dto.getDateFinContratAgent().format(fDate) : "", styleEmp);
            creerCNum(row, c++, dto.getNbEnfantsAgent() != null ? dto.getNbEnfantsAgent() : 0, styleEmp);
        } else {
            for (int k = 0; k < NB_COLS_EMP; k++) row.createCell(k).setCellStyle(styleEmp);
            c = NB_COLS_EMP;
        }

        // Colonnes de la demande
        creerC(row, c++, nvl(dto.getLibelleTypeConge()), stDemande);
        creerC(row, c++, dto.getDateDebut() != null ? dto.getDateDebut().format(fDate) : "", stDemande);
        creerC(row, c++, dto.getDateFin() != null ? dto.getDateFin().format(fDate) : "", stDemande);
        double nj = dto.getNombreJours() != null ? dto.getNombreJours().doubleValue() : 0;
        creerCNum(row, c++, nj, stDemande);
        // Colonne Statut colorée
        Cell cStatut = row.createCell(c++);
        cStatut.setCellValue(libelleStatut(dto.getStatut()));
        cStatut.setCellStyle(styleSelonStatut(dto.getStatut(), styleEtatValide, styleEtatEnCours, styleEtatRefuse, styleEtatAttente));

        sousTotal += nj; totalGeneral += nj;
    }

    if (agentCourant != null) {
        fusionnerColonnes(sheet, rDebutGroupe, r - 1, NB_COLS_EMP);
        r = ecrireSousTotalStyle(sheet, r, styleTotal, nomAgentCourant, sousTotal, COL_NB_JOURS, totalCols);
    }
    r = ecrireSousTotalStyle(sheet, r, styleTotal, "TOTAL GÉNÉRAL", totalGeneral, COL_NB_JOURS, totalCols);

    r += 1;
    construireSyntheseEnBas(sheet, r, dtos, labelStyle, headerStyle);

    sheet.createFreezePane(0, ligneEntete + 1);
    for (int i = 0; i < totalCols; i++) sheet.autoSizeColumn(i);
}


private void creerC(Row row, int col, String val, CellStyle style) {
    Cell c = row.createCell(col); c.setCellValue(val); c.setCellStyle(style);
}
private void creerCNum(Row row, int col, double val, CellStyle style) {
    Cell c = row.createCell(col); c.setCellValue(val); c.setCellStyle(style);
}
private int ecrireSousTotalStyle(Sheet sheet, int r, CellStyle style, String libelle,
                                 double total, int colJours, int totalCols) {
    Row row = sheet.createRow(r++);
    for (int i = 0; i < totalCols; i++) row.createCell(i).setCellStyle(style);
    row.getCell(0).setCellValue("Total " + libelle);
    row.getCell(colJours).setCellValue(total);
    return r;
}
private void fusionnerColonnes(Sheet sheet, int rDebut, int rFin, int nbCols) {
    if (rFin <= rDebut) return;
    for (int col = 0; col < nbCols; col++) {
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rDebut, rFin, col, col));
    }
}

private void construireSyntheseEnBas(Sheet sheet, int r, List<DemandeCongeDTO> dtos,
                                     CellStyle labelStyle, CellStyle headerStyle) {
    Row titre = sheet.createRow(r++);
    Cell cT = titre.createCell(0);
    cT.setCellValue("Synthèse statistique — " + dtos.size() + " demande(s)");
    cT.setCellStyle(headerStyle);

    java.util.Map<String, Integer> parStatut = new java.util.TreeMap<>();
    java.util.Map<String, Integer> parType = new java.util.TreeMap<>();
    java.util.Map<String, Integer> parSexe = new java.util.TreeMap<>();
    java.util.Map<String, Integer> parDirection = new java.util.TreeMap<>();
    for (DemandeCongeDTO dto : dtos) {
        parStatut.merge(libelleStatut(dto.getStatut()), 1, Integer::sum);
        parType.merge(nvl(dto.getLibelleTypeConge()), 1, Integer::sum);
        String sexe = "M".equalsIgnoreCase(dto.getSexeAgent()) ? "Masculin"
                    : "F".equalsIgnoreCase(dto.getSexeAgent()) ? "Féminin" : "Non défini";
        parSexe.merge(sexe, 1, Integer::sum);
        parDirection.merge(dto.getDirectionAgent() == null || dto.getDirectionAgent().isBlank()
            ? "Non définie" : dto.getDirectionAgent(), 1, Integer::sum);
    }

    r = ecrireBlocSynthese(sheet, r + 1, "Par statut", parStatut, labelStyle);
    r = ecrireBlocSynthese(sheet, r + 1, "Par type de congé", parType, labelStyle);
    r = ecrireBlocSynthese(sheet, r + 1, "Par sexe", parSexe, labelStyle);
    r = ecrireBlocSynthese(sheet, r + 1, "Par direction", parDirection, labelStyle);
}

private int ecrireBlocSynthese(Sheet sheet, int r, String titre,
                               java.util.Map<String, Integer> data, CellStyle labelStyle) {
    Row rt = sheet.createRow(r++);
    Cell ct = rt.createCell(0);
    ct.setCellValue(titre);
    ct.setCellStyle(labelStyle);
    for (java.util.Map.Entry<String, Integer> e : data.entrySet()) {
        Row row = sheet.createRow(r++);
        row.createCell(0).setCellValue(e.getKey());
        row.createCell(1).setCellValue(e.getValue());
    }
    return r;
}

// Applique des bordures fines grises sur un style
private void bordures(org.apache.poi.xssf.usermodel.XSSFCellStyle style) {
    org.apache.poi.ss.usermodel.BorderStyle thin = org.apache.poi.ss.usermodel.BorderStyle.THIN;
    style.setBorderTop(thin); style.setBorderBottom(thin);
    style.setBorderLeft(thin); style.setBorderRight(thin);
}

// Crée un style de colonne État avec couleur de texte et de fond
private org.apache.poi.xssf.usermodel.XSSFCellStyle styleEtatCouleur(XSSFWorkbook wb, byte[] texte, byte[] fond) {
    org.apache.poi.xssf.usermodel.XSSFCellStyle s = wb.createCellStyle();
    org.apache.poi.xssf.usermodel.XSSFFont f = wb.createFont();
    f.setBold(true);
    f.setColor(new org.apache.poi.xssf.usermodel.XSSFColor(texte, null));
    s.setFont(f);
    s.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(fond, null));
    s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    s.setAlignment(HorizontalAlignment.CENTER);
    s.setVerticalAlignment(VerticalAlignment.CENTER);
    bordures(s);
    return s;
}

// Choisit le bon style selon le statut
private org.apache.poi.xssf.usermodel.XSSFCellStyle styleSelonStatut(String statut,
        org.apache.poi.xssf.usermodel.XSSFCellStyle valide,
        org.apache.poi.xssf.usermodel.XSSFCellStyle enCours,
        org.apache.poi.xssf.usermodel.XSSFCellStyle refuse,
        org.apache.poi.xssf.usermodel.XSSFCellStyle attente) {
    String st = statut != null ? statut.toUpperCase() : "";
    if (st.contains("VALID")) return valide;
    if (st.contains("COURS")) return enCours;
    if (st.contains("REFUS")) return refuse;
    return attente;
}

// Écrit une cellule employé numérique avec style
private void cellEmp(Row row, int col, double val, CellStyle style) {
    Cell c = row.createCell(col); c.setCellValue(val); c.setCellStyle(style);
}
// Écrit une cellule employé texte avec style
private void cellEmpTxt(Row row, int col, String val, CellStyle style) {
    Cell c = row.createCell(col); c.setCellValue(val); c.setCellStyle(style);
}

    // ---- Feuille statistiques (plusieurs agents) ----
 private void construireStatistiques(XSSFWorkbook wb, CellStyle titreStyle, CellStyle labelStyle,
                                     CellStyle headerStyle, List<DemandeConge> demandes) {
    Sheet sheet = wb.createSheet("Statistiques");
    DateTimeFormatter fJour = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ===== STYLES =====
    org.apache.poi.xssf.usermodel.XSSFCellStyle styleBande = wb.createCellStyle();
    org.apache.poi.xssf.usermodel.XSSFFont fBande = wb.createFont();
    fBande.setBold(true); fBande.setColor(IndexedColors.WHITE.getIndex()); fBande.setFontHeightInPoints((short)12);
    styleBande.setFont(fBande);
    styleBande.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)0x2c,(byte)0x3e,(byte)0x50}, null));
    styleBande.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    styleBande.setAlignment(HorizontalAlignment.CENTER);
    styleBande.setVerticalAlignment(VerticalAlignment.CENTER);

    org.apache.poi.xssf.usermodel.XSSFCellStyle styleEntete = wb.createCellStyle();
    org.apache.poi.xssf.usermodel.XSSFFont fEnt = wb.createFont();
    fEnt.setBold(true); fEnt.setColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)0x33,(byte)0x42,(byte)0x4f}, null));
    styleEntete.setFont(fEnt);
    styleEntete.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)0xe4,(byte)0xe9,(byte)0xee}, null));
    styleEntete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    styleEntete.setAlignment(HorizontalAlignment.CENTER);
    styleEntete.setVerticalAlignment(VerticalAlignment.CENTER);
    bordures(styleEntete);

    org.apache.poi.xssf.usermodel.XSSFCellStyle styleCell = wb.createCellStyle();
    styleCell.setAlignment(HorizontalAlignment.CENTER);
    styleCell.setVerticalAlignment(VerticalAlignment.CENTER);
    bordures(styleCell);

    org.apache.poi.xssf.usermodel.XSSFCellStyle styleEmp = wb.createCellStyle();
    org.apache.poi.xssf.usermodel.XSSFFont fEmp = wb.createFont(); fEmp.setBold(true);
    styleEmp.setFont(fEmp);
    styleEmp.setAlignment(HorizontalAlignment.CENTER);
    styleEmp.setVerticalAlignment(VerticalAlignment.CENTER);
    bordures(styleEmp);

    org.apache.poi.xssf.usermodel.XSSFCellStyle styleSolde = wb.createCellStyle();
    org.apache.poi.xssf.usermodel.XSSFFont fSol = wb.createFont(); fSol.setBold(true);
    styleSolde.setFont(fSol);
    styleSolde.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)0xee,(byte)0xf2,(byte)0xf6}, null));
    styleSolde.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    styleSolde.setAlignment(HorizontalAlignment.CENTER);
    styleSolde.setVerticalAlignment(VerticalAlignment.CENTER);
    bordures(styleSolde);

    org.apache.poi.xssf.usermodel.XSSFCellStyle styleTotaux = wb.createCellStyle();
    org.apache.poi.xssf.usermodel.XSSFFont fTot = wb.createFont(); fTot.setBold(true);
    styleTotaux.setFont(fTot);
    styleTotaux.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)0xff,(byte)0xf6,(byte)0xe0}, null));
    styleTotaux.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    styleTotaux.setAlignment(HorizontalAlignment.CENTER);
    bordures(styleTotaux);

    // 4 styles ÉTAT selon le statut
    org.apache.poi.xssf.usermodel.XSSFCellStyle styleEtatValide  = styleEtatCouleur(wb, new byte[]{(byte)0x1b,(byte)0x5e,(byte)0x20}, new byte[]{(byte)0xc8,(byte)0xe6,(byte)0xc9});
    org.apache.poi.xssf.usermodel.XSSFCellStyle styleEtatEnCours = styleEtatCouleur(wb, new byte[]{(byte)0x7a,(byte)0x3d,(byte)0x00}, new byte[]{(byte)0xff,(byte)0xcc,(byte)0x80});
    org.apache.poi.xssf.usermodel.XSSFCellStyle styleEtatRefuse  = styleEtatCouleur(wb, new byte[]{(byte)0x8e,(byte)0x1b,(byte)0x1b}, new byte[]{(byte)0xff,(byte)0xcd,(byte)0xd2});
    org.apache.poi.xssf.usermodel.XSSFCellStyle styleEtatAttente = styleEtatCouleur(wb, new byte[]{(byte)0x8a,(byte)0x4b,(byte)0x08}, new byte[]{(byte)0xff,(byte)0xe0,(byte)0xb2});

    // ===== Plage de mois =====
    java.time.YearMonth min = null, max = null;
    for (DemandeConge d : demandes) {
        if (d.getDateDebut() == null) continue;
        java.time.YearMonth ym = java.time.YearMonth.from(d.getDateDebut());
        if (min == null || ym.isBefore(min)) min = ym;
        java.time.YearMonth ymf = (d.getDateFin() != null) ? java.time.YearMonth.from(d.getDateFin()) : ym;
        if (max == null || ymf.isAfter(max)) max = ymf;
    }
    if (min == null) { sheet.createRow(0).createCell(0).setCellValue("Aucune donnée"); return; }
    List<java.time.YearMonth> mois = new ArrayList<>();
    for (java.time.YearMonth ym = min; !ym.isAfter(max); ym = ym.plusMonths(1)) mois.add(ym);

    final int NB_FIXES = 5, SOUS = 4;
    int colTot = NB_FIXES + mois.size() * SOUS;
    int colEtat = colTot + 4;
    int totalCols = colTot + 5;
    DateTimeFormatter fMois = DateTimeFormatter.ofPattern("MMM yy", java.util.Locale.FRENCH);

    // ===== Ligne 0 : bande titre =====
    String periode = min.format(fMois) + " → " + max.format(fMois);
    Row bande = sheet.createRow(0); bande.setHeightInPoints(22);
    Cell cb = bande.createCell(0);
    cb.setCellValue("SEN-CSU · SUIVI DES CONGÉS — Période : " + periode);
    cb.setCellStyle(styleBande);
    for (int i = 1; i < totalCols; i++) bande.createCell(i).setCellStyle(styleBande);
    sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, totalCols - 1));

    // ===== Lignes 1 et 2 : en-têtes =====
    int hr1 = 1, hr2 = 2;
    Row h1 = sheet.createRow(hr1), h2 = sheet.createRow(hr2);
    String[] fixes = {"N°", "Prénom & Nom", "Matricule", "Prise de service", "Fonction"};
    for (int i = 0; i < fixes.length; i++) {
        Cell c = h1.createCell(i); c.setCellValue(fixes[i]); c.setCellStyle(styleEntete);
        h2.createCell(i).setCellStyle(styleEntete);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(hr1, hr2, i, i));
    }
    int col = NB_FIXES;
    for (java.time.YearMonth ym : mois) {
        Cell cm = h1.createCell(col); cm.setCellValue(ym.format(fMois)); cm.setCellStyle(styleEntete);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(hr1, hr1, col, col + SOUS - 1));
        String[] sous = {"dus", "pris", "du", "au"};
        for (int k = 0; k < SOUS; k++) { Cell cs = h2.createCell(col + k); cs.setCellValue(sous[k]); cs.setCellStyle(styleEntete); }
        col += SOUS;
    }
    String[] totaux = {"Total congés dus", "Total supplément", "Total congés pris", "Reliquat"};
    for (int i = 0; i < totaux.length; i++) {
        Cell c = h1.createCell(colTot + i); c.setCellValue(totaux[i]); c.setCellStyle(styleEntete);
        h2.createCell(colTot + i).setCellStyle(styleEntete);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(hr1, hr2, colTot + i, colTot + i));
    }
    Cell cEtatH = h1.createCell(colEtat); cEtatH.setCellValue("État"); cEtatH.setCellStyle(styleEntete);
    h2.createCell(colEtat).setCellStyle(styleEntete);
    sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(hr1, hr2, colEtat, colEtat));

    // ===== Données groupées par agent =====
    Map<Long, List<DemandeConge>> parAgent = new LinkedHashMap<>();
    for (DemandeConge d : demandes) {
        if (d.getAgent() == null) continue;
        parAgent.computeIfAbsent(d.getAgent().getId(), k -> new ArrayList<>()).add(d);
    }

    int r = hr2 + 1, numero = 1;
    double[] totDus = new double[mois.size()], totPris = new double[mois.size()];
    double gDus = 0, gSupp = 0, gPris = 0, gRel = 0;

    for (Map.Entry<Long, List<DemandeConge>> e : parAgent.entrySet()) {
        List<DemandeConge> congesAgent = e.getValue();
        Agent a = congesAgent.get(0).getAgent();
        int nb = congesAgent.size(), rDebut = r;

        SoldeConges solde = soldeCongesRepository.findByAgentId(a.getId()).orElse(null);
        double sDus  = (solde != null && solde.getNbCongesAcquis() != null) ? solde.getNbCongesAcquis().doubleValue() : 0;
        double sSupp = (solde != null && solde.getNbCongesSupplementaire() != null) ? solde.getNbCongesSupplementaire().doubleValue() : 0;
        double sPris = (solde != null && solde.getNbJourPrisDeductible() != null) ? solde.getNbJourPrisDeductible().doubleValue() : 0;
        double sRel  = (solde != null && solde.getNbCongesReliquat() != null) ? solde.getNbCongesReliquat().doubleValue() : 0;

        for (int idx = 0; idx < nb; idx++) {
            DemandeConge d = congesAgent.get(idx);
            Row row = sheet.createRow(r++);

            if (idx == 0) {
                cellEmp(row, 0, numero, styleEmp);
                cellEmpTxt(row, 1, nvl(a.getPrenom()) + " " + nvl(a.getNom()), styleEmp);
                cellEmpTxt(row, 2, nvl(a.getMatricule()), styleEmp);
                cellEmpTxt(row, 3, a.getDateEmbauche() != null ? a.getDateEmbauche().format(fJour) : "", styleEmp);
                cellEmpTxt(row, 4, a.getFonction() != null ? nvl(a.getFonction().getLibelle()) : "", styleEmp);
            } else {
                for (int cc = 0; cc < NB_FIXES; cc++) row.createCell(cc).setCellStyle(styleEmp);
            }

            int cc = NB_FIXES;
            for (int m = 0; m < mois.size(); m++) {
                java.time.YearMonth ym = mois.get(m);
                Cell cd = row.createCell(cc);
                if (idx == 0) { cd.setCellValue(2); totDus[m] += 2; }
                cd.setCellStyle(styleCell);

                double pm = joursOuvrablesDansMois(d.getDateDebut(), d.getDateFin(), ym);
                Cell cp = row.createCell(cc + 1); cp.setCellStyle(styleCell);
                Cell cdu = row.createCell(cc + 2); cdu.setCellStyle(styleCell);
                Cell cau = row.createCell(cc + 3); cau.setCellStyle(styleCell);
                if (pm > 0) {
                    cp.setCellValue(pm);
                    cdu.setCellValue(d.getDateDebut() != null ? d.getDateDebut().format(fJour) : "");
                    cau.setCellValue(d.getDateFin() != null ? d.getDateFin().format(fJour) : "");
                    totPris[m] += pm;
                }
                cc += SOUS;
            }

            if (idx == 0) {
                Cell c1 = row.createCell(colTot);     c1.setCellValue(sDus);  c1.setCellStyle(styleSolde);
                Cell c2 = row.createCell(colTot + 1); c2.setCellValue(sSupp); c2.setCellStyle(styleSolde);
                Cell c3 = row.createCell(colTot + 2); c3.setCellValue(sPris); c3.setCellStyle(styleSolde);
                Cell c4 = row.createCell(colTot + 3); c4.setCellValue(sRel);  c4.setCellStyle(styleSolde);
            } else {
                for (int k = 0; k < 4; k++) row.createCell(colTot + k).setCellStyle(styleSolde);
            }

            // Colonne ÉTAT (couleur selon statut) — sur CHAQUE ligne
            Cell cEtat = row.createCell(colEtat);
            cEtat.setCellValue(libelleStatut(d.getStatut()));
            cEtat.setCellStyle(styleSelonStatut(d.getStatut(), styleEtatValide, styleEtatEnCours, styleEtatRefuse, styleEtatAttente));
        }

        if (nb > 1) {
            for (int cFix = 0; cFix < NB_FIXES; cFix++)
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rDebut, r - 1, cFix, cFix));
            for (int m = 0; m < mois.size(); m++) {
                int colDus = NB_FIXES + m * SOUS;
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rDebut, r - 1, colDus, colDus));
            }
            for (int k = 0; k < 4; k++)
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rDebut, r - 1, colTot + k, colTot + k));
        }

        gDus += sDus; gSupp += sSupp; gPris += sPris; gRel += sRel;
        numero++;
    }

    // ===== Ligne TOTAUX =====
    Row tot = sheet.createRow(r++);
    Cell ct = tot.createCell(0); ct.setCellValue("TOTAUX"); ct.setCellStyle(styleTotaux);
    for (int i = 1; i < NB_FIXES; i++) tot.createCell(i).setCellStyle(styleTotaux);
    sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(r - 1, r - 1, 0, NB_FIXES - 1));
    int cc = NB_FIXES;
    for (int m = 0; m < mois.size(); m++) {
        Cell cd = tot.createCell(cc);     cd.setCellValue(totDus[m]);  cd.setCellStyle(styleTotaux);
        Cell cp = tot.createCell(cc + 1); cp.setCellValue(totPris[m]); cp.setCellStyle(styleTotaux);
        tot.createCell(cc + 2).setCellStyle(styleTotaux);
        tot.createCell(cc + 3).setCellStyle(styleTotaux);
        cc += SOUS;
    }
    Cell g1 = tot.createCell(colTot);     g1.setCellValue(gDus);  g1.setCellStyle(styleTotaux);
    Cell g2 = tot.createCell(colTot + 1); g2.setCellValue(gSupp); g2.setCellStyle(styleTotaux);
    Cell g3 = tot.createCell(colTot + 2); g3.setCellValue(gPris); g3.setCellStyle(styleTotaux);
    Cell g4 = tot.createCell(colTot + 3); g4.setCellValue(gRel);  g4.setCellStyle(styleTotaux);
    tot.createCell(colEtat).setCellStyle(styleTotaux);

    sheet.createFreezePane(NB_FIXES, hr2 + 1);
    for (int i = 0; i < totalCols; i++) sheet.autoSizeColumn(i);
}

private double joursOuvrablesDansMois(LocalDate debut, LocalDate fin, java.time.YearMonth mois) {
    if (debut == null || fin == null) return 0;
    LocalDate premierDuMois = mois.atDay(1);
    LocalDate dernierDuMois = mois.atEndOfMonth();
    LocalDate deb = debut.isAfter(premierDuMois) ? debut : premierDuMois;
    LocalDate f   = fin.isBefore(dernierDuMois) ? fin : dernierDuMois;
    if (deb.isAfter(f)) return 0;
    int n = 0;
    for (LocalDate j = deb; !j.isAfter(f); j = j.plusDays(1)) {
        java.time.DayOfWeek dw = j.getDayOfWeek();
        boolean weekend = (dw == java.time.DayOfWeek.SATURDAY || dw == java.time.DayOfWeek.SUNDAY);
        boolean ferie = jourFerieService.estFerie(j);
        if (!weekend && !ferie) n++;
    }
    return n;
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


    @org.springframework.beans.factory.annotation.Value("${sga.upload.justificatifs}")
private String dossierJustificatifs;

// Export ZIP : Excel des demandes + justificatifs joints
public byte[] exporterDemandesAvecJustificatifs(List<Long> ids) {
    List<DemandeConge> demandes = (ids == null || ids.isEmpty())
        ? demandeCongeRepository.findAllByOrderByDateCreationDesc()
        : demandeCongeRepository.findAllById(ids);

    try (ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
         java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(zipOut)) {

        // 1) Fichier Excel des demandes
        byte[] excel = genererExcelDemandesSimple(demandes);
        zip.putNextEntry(new java.util.zip.ZipEntry("demandes.xlsx"));
        zip.write(excel);
        zip.closeEntry();

        // 2) Justificatifs
        java.nio.file.Path dossier = java.nio.file.Paths.get(dossierJustificatifs);
        for (DemandeConge d : demandes) {
            String nomJustif = d.getJustificatif();
            if (nomJustif == null || nomJustif.isBlank()) continue;
            java.nio.file.Path fichier = dossier.resolve(nomJustif);
            if (java.nio.file.Files.exists(fichier)) {
                zip.putNextEntry(new java.util.zip.ZipEntry("justificatifs/" + nomJustif));
                java.nio.file.Files.copy(fichier, zip);
                zip.closeEntry();
            }
        }

        zip.finish();
        return zipOut.toByteArray();
    } catch (Exception e) {
        throw new RuntimeException("Erreur export ZIP : " + e.getMessage());
    }
}

// Excel simple des demandes
private byte[] genererExcelDemandesSimple(List<DemandeConge> demandes) {
    DateTimeFormatter fDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    try (XSSFWorkbook wb = new XSSFWorkbook();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {

        Sheet sheet = wb.createSheet("Demandes");

        CellStyle headerStyle = wb.createCellStyle();
        Font hf = wb.createFont(); hf.setBold(true); hf.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(hf);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        String[] entetes = {"Nom", "Prénom", "Matricule", "Direction", "Type de congé",
            "Date début", "Date fin", "Nb jours", "Statut", "Motif", "Justificatif"};
        Row h = sheet.createRow(0);
        for (int i = 0; i < entetes.length; i++) {
            Cell c = h.createCell(i); c.setCellValue(entetes[i]); c.setCellStyle(headerStyle);
        }

        int r = 1;
        for (DemandeConge d : demandes) {
            DemandeCongeDTO dto = toDto(d);
            Row row = sheet.createRow(r++);
            int c = 0;
            row.createCell(c++).setCellValue(nvl(dto.getNomAgent()));
            row.createCell(c++).setCellValue(nvl(dto.getPrenomAgent()));
            row.createCell(c++).setCellValue(nvl(dto.getMatriculeAgent()));
            row.createCell(c++).setCellValue(nvl(dto.getDirectionAgent()));
            row.createCell(c++).setCellValue(nvl(dto.getLibelleTypeConge()));
            row.createCell(c++).setCellValue(dto.getDateDebut() != null ? dto.getDateDebut().format(fDate) : "");
            row.createCell(c++).setCellValue(dto.getDateFin() != null ? dto.getDateFin().format(fDate) : "");
            row.createCell(c++).setCellValue(dto.getNombreJours() != null ? dto.getNombreJours().doubleValue() : 0);
            row.createCell(c++).setCellValue(libelleStatut(dto.getStatut()));
            row.createCell(c++).setCellValue(nvl(d.getMotif()));
            row.createCell(c++).setCellValue(d.getJustificatif() != null ? "Oui" : "Non");
        }
        for (int i = 0; i < entetes.length; i++) sheet.autoSizeColumn(i);

        wb.write(out);
        return out.toByteArray();
    } catch (Exception e) {
        throw new RuntimeException("Erreur Excel : " + e.getMessage());
    }
}
}