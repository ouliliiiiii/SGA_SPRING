package sga_backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sga_backend.dto.DemandeAValiderDTO;
import sga_backend.dto.EtapeValidationDTO;
import sga_backend.entity.Agent;
import sga_backend.entity.DemandeConge;
import sga_backend.entity.EtapeValidation;
import sga_backend.entity.SoldeConges;
import sga_backend.entity.Utilisateur;
import sga_backend.repository.DemandeCongeRepository;
import sga_backend.repository.EtapeValidationRepository;
import sga_backend.repository.SoldeCongesRepository;
import sga_backend.repository.UtilisateurRepository;
import java.util.Set;

@Service
public class CircuitValidationService {

    @Autowired private EtapeValidationRepository etapeValidationRepository;
    @Autowired private DemandeCongeRepository demandeCongeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private SoldeCongesRepository soldeCongesRepository;
    @Autowired private SoldeCongesService soldeCongesService;
    @Autowired private NotificationService notificationService;
    @Autowired private EmailService emailService;
    @Autowired
    private JournalService journalService;
    
    // ============================================================
    @Transactional
        public void genererEtapes(DemandeConge demande) {
        Agent agent = demande.getAgent();

        // Profil de l'agent (code + niveau), via son utilisateur lié
        String codeProfilAgent = "";
        int niveauAgent = 0;
        Utilisateur u = utilisateurRepository.findByAgentId(agent.getId()).orElse(null);
        if (u != null && u.getProfil() != null) {
            codeProfilAgent = u.getProfil().getCode();
            niveauAgent = u.getProfil().getNiveau() != null ? u.getProfil().getNiveau() : 0;
        }

        Long idUniteAgent = agent.getUnite() != null ? agent.getUnite().getId() : null;
        Long idDirectionAgent = agent.getDirection() != null ? agent.getDirection().getId() : null;
        List<EtapeValidation> etapes = new ArrayList<>();

        // Libellés unifiés
        final String LIB_ADMIN = "Directeur du Capital Humain";
        final String LIB_DACH  = "Directeur de l'Administration, des Finances et du Capital Humain (DACH)";

        // ===== CAS SPÉCIAUX DU SOMMET (le demandeur EST un dirigeant) =====
        if ("ADMIN".equalsIgnoreCase(codeProfilAgent)) {
            etapes.add(creerEtape(demande, 1, "DAF", LIB_DACH, null, null));
            etapes.add(creerEtape(demande, 2, "ADMIN", LIB_ADMIN, null, null));
            etapeValidationRepository.saveAll(etapes);
            return;
        }
        if ("DG".equalsIgnoreCase(codeProfilAgent)) {
            etapes.add(creerEtape(demande, 1, "ADMIN", LIB_ADMIN, null, null));
            etapes.add(creerEtape(demande, 2, "DAF", LIB_DACH, null, null));
            etapeValidationRepository.saveAll(etapes);
            return;
        }
        if ("DAF".equalsIgnoreCase(codeProfilAgent)) {
            etapes.add(creerEtape(demande, 1, "SG", "Secrétaire Général", null, null));
            etapes.add(creerEtape(demande, 2, "ADMIN", LIB_ADMIN, null, null));
            etapeValidationRepository.saveAll(etapes);
            return;
        }
        if ("SG".equalsIgnoreCase(codeProfilAgent)) {
            etapes.add(creerEtape(demande, 1, "DG", "Directeur Général", null, null));
            etapes.add(creerEtape(demande, 2, "ADMIN", LIB_ADMIN, null, null));
            etapes.add(creerEtape(demande, 3, "DAF", LIB_DACH, null, null));
            etapeValidationRepository.saveAll(etapes);
            return;
        }
        if ("DIRECTEUR".equalsIgnoreCase(codeProfilAgent)) {
            etapes.add(creerEtape(demande, 1, "SG", "Secrétaire Général", null, null));
            etapes.add(creerEtape(demande, 2, "ADMIN", LIB_ADMIN, null, null));
            etapes.add(creerEtape(demande, 3, "DAF", LIB_DACH, null, null));
            etapeValidationRepository.saveAll(etapes);
            return;
        }

        // ===== RÈGLE GÉNÉRALE PAR NIVEAU =====
        String typeLieu = "SIEGE";
        if ((agent.getUnite() != null ? agent.getUnite().getLieuTravail() : null) != null
                && (agent.getUnite().getLieuTravail().getTypeEntite() != null)) {
            typeLieu = agent.getUnite().getLieuTravail().getTypeEntite().name().toUpperCase();
        }

        // Circuit : profil, libellé, niveau
        List<Object[]> circuit = new ArrayList<>();
        if ("REGIONAL".equals(typeLieu)) {
            circuit.add(new Object[]{"CSR",   "Chef de Service Régional", 50});
            circuit.add(new Object[]{"SG",    "Secrétaire Général",       80});
            circuit.add(new Object[]{"ADMIN", LIB_ADMIN,                 100});
            circuit.add(new Object[]{"DAF",   LIB_DACH,                   85});
        } else {
            circuit.add(new Object[]{"CHEF_DIVISION", "Chef de Division", 50});
            circuit.add(new Object[]{"DIRECTEUR",     "Directeur",        60});
            circuit.add(new Object[]{"ADMIN",         LIB_ADMIN,         100});
            circuit.add(new Object[]{"DAF",           LIB_DACH,           85});
        }

        // Déduplication par personne réelle : on ne demande jamais 2 fois
        // la même validation à la même personne (ex. chef de division qui est aussi l'ADMIN).
        Set<Long> validateursDejaVus = new HashSet<>();
        int ordre = 1;

        for (Object[] e : circuit) {
            String profilEtape = (String) e[0];
            String libelle     = (String) e[1];
            int niveauEtape    = (int) e[2];

            if (niveauEtape <= niveauAgent) continue;

            Long uniteCible = null;
            Long directionCible = null;

            if ("CHEF_DIVISION".equals(profilEtape) || "CSR".equals(profilEtape)) {
                // Pas d'unité => pas de chef de division à ce niveau : on saute.
                if (idUniteAgent == null) continue;
                uniteCible = idUniteAgent;
            } else if ("DIRECTEUR".equals(profilEtape)) {
                directionCible = idDirectionAgent;
            }

            // On construit une étape "candidate" pour résoudre QUI validerait
            EtapeValidation candidate = new EtapeValidation();
            candidate.setProfilAttendu(profilEtape);
            candidate.setIdUniteAttendue(uniteCible);
            candidate.setIdDirectionAttendue(directionCible);

            Utilisateur validateur = trouverValidateurDeLEtape(candidate);

            // Si personne ne correspond (ex. DIRECTEUR dans une direction sans ce profil) : on saute.
            if (validateur == null) continue;

            // Si cette personne a déjà une étape dans ce circuit : on ne la remet pas.
            if (!validateursDejaVus.add(validateur.getId())) continue;

            etapes.add(creerEtape(demande, ordre, profilEtape, libelle, uniteCible, directionCible));
            ordre++;
        }

        // Garde-fou : si rien ne reste, on met DAF par défaut
        if (etapes.isEmpty()) {
            etapes.add(creerEtape(demande, 1, "DAF", LIB_DACH, null, null));
        }

        etapeValidationRepository.saveAll(etapes);
    }

    // creerEtape avec ciblage (unité / direction)
    private EtapeValidation creerEtape(DemandeConge demande, int ordre, String profil, String libelle,
                                       Long idUniteAttendue, Long idDirectionAttendue) {
        EtapeValidation e = new EtapeValidation();
        e.setDemande(demande);
        e.setOrdre(ordre);
        e.setProfilAttendu(profil);
        e.setLibelle(libelle);
        e.setStatut("EN_ATTENTE");
        e.setIdUniteAttendue(idUniteAttendue);
        e.setIdDirectionAttendue(idDirectionAttendue);
        if (ordre == 1) {
            e.setDateActivation(LocalDateTime.now());
        }
        return e;
    }

    public List<EtapeValidationDTO> getEtapes(Long idDemande) {
        List<EtapeValidationDTO> liste = new ArrayList<>();
        for (EtapeValidation e : etapeValidationRepository.findByDemandeIdOrderByOrdreAsc(idDemande)) {
            liste.add(toDto(e));
        }
        return liste;
    }

    private EtapeValidationDTO toDto(EtapeValidation e) {
        EtapeValidationDTO dto = new EtapeValidationDTO();
        dto.setId(e.getId());
        dto.setIdDemande(e.getDemande() != null ? e.getDemande().getId() : null);
        dto.setOrdre(e.getOrdre());
        dto.setProfilAttendu(e.getProfilAttendu());
        dto.setLibelle(e.getLibelle());
        dto.setStatut(e.getStatut());
        if (e.getValidateur() != null) {
            dto.setIdValidateur(e.getValidateur().getId());
            dto.setNomValidateur(e.getValidateur().getNom() + " " + e.getValidateur().getPrenom());
        }
        
        dto.setDateAction(e.getDateAction());
        dto.setCommentaire(e.getCommentaire());
        return dto;
    }

    // ============================================================
    // COUCHE 3 : les demandes qu'un utilisateur doit valider
    //   - filtre par profil
    //   - PLUS : ciblage unité/direction
    //   - PLUS : marque l'étape finale (dernière du circuit)
    // ============================================================
       public List<DemandeAValiderDTO> getDemandesAValider(Long idUtilisateur) {
                long debut = System.currentTimeMillis();
        Utilisateur user = utilisateurRepository.findById(idUtilisateur).orElse(null);
        if (user == null || user.getProfil() == null) return new ArrayList<>();

        String codeProfil = user.getProfil().getCode();

        Long idUniteUser = null;
        Long idDirectionUser = null;
        if (user.getAgent() != null) {
            if (user.getAgent().getUnite() != null) {
                idUniteUser = user.getAgent().getUnite().getId();
            }
            if (user.getAgent().getDirection() != null) {
                idDirectionUser = user.getAgent().getDirection().getId();
            }
        }

        List<String> profilsTraitables = new ArrayList<>();
        profilsTraitables.add(codeProfil);
        if ("ADMIN".equalsIgnoreCase(codeProfil)) {
            profilsTraitables.add("DAF");
        }

        List<EtapeValidation> etapes = etapeValidationRepository
            .findByProfilAttenduInAndStatut(profilsTraitables, "EN_ATTENTE");

        List<DemandeAValiderDTO> resultat = new ArrayList<>();

        for (EtapeValidation etape : etapes) {
            DemandeConge d = etape.getDemande();

            // Charger les étapes de CETTE demande UNE SEULE FOIS
            List<EtapeValidation> toutesEtapes = etapeValidationRepository
                .findByDemandeIdOrderByOrdreAsc(d.getId());

            // Étape courante = première EN_ATTENTE
            EtapeValidation courante = toutesEtapes.stream()
                .filter(e -> "EN_ATTENTE".equals(e.getStatut()))
                .findFirst().orElse(null);
            if (courante == null || !courante.getId().equals(etape.getId())) {
                continue; // pas l'étape courante
            }

            // Filtre de ciblage
            if (etape.getIdUniteAttendue() != null) {
                if (idUniteUser == null || !idUniteUser.equals(etape.getIdUniteAttendue())) continue;
            }
            if (etape.getIdDirectionAttendue() != null) {
                if (idDirectionUser == null || !idDirectionUser.equals(etape.getIdDirectionAttendue())) continue;
            }

            DemandeAValiderDTO dto = new DemandeAValiderDTO();
            dto.setIdDemande(d.getId());
            dto.setIdEtape(etape.getId());
            dto.setLibelleEtape(etape.getLibelle());

            if (d.getAgent() != null) {
                dto.setIdAgent(d.getAgent().getId());
                dto.setNomAgent(d.getAgent().getNom());
                dto.setPrenomAgent(d.getAgent().getPrenom());
                dto.setMatriculeAgent(d.getAgent().getMatricule());
                if (d.getAgent().getFonction() != null) {
                    dto.setFonctionAgent(d.getAgent().getFonction().getLibelle());
                }
                Utilisateur uAgent = utilisateurRepository.findByAgentId(d.getAgent().getId()).orElse(null);
                if (uAgent != null && uAgent.getProfil() != null) {
                    dto.setProfilAgent(uAgent.getProfil().getLibelle());
                }
            }
            if (d.getTypeConge() != null) {
                dto.setLibelleTypeConge(d.getTypeConge().getLibelle());
                dto.setCategorieConge(d.getTypeConge().getCategorie());
            }
            dto.setDateDebut(d.getDateDebut());
            dto.setDateFin(d.getDateFin());
            dto.setNombreJours(d.getNombreJours());
            dto.setMotif(d.getMotif());
            dto.setStatut(d.getStatut());
            dto.setDateCreation(d.getDateCreation());

            // Ordre max (réutilise toutesEtapes déjà chargées)
            int ordreMax = toutesEtapes.stream()
                .mapToInt(EtapeValidation::getOrdre)
                .max().orElse(etape.getOrdre());
            dto.setEtapeFinale(etape.getOrdre() == ordreMax);

            resultat.add(dto);
        }
        System.out.println(">>> getDemandesAValider a pris : " + (System.currentTimeMillis() - debut) + " ms");
        return resultat;
    }

    // Vérifie qu'une étape est bien la première EN_ATTENTE de sa demande
    private boolean estEtapeCourante(EtapeValidation etape) {
        List<EtapeValidation> toutes = etapeValidationRepository
            .findByDemandeIdOrderByOrdreAsc(etape.getDemande().getId());
        EtapeValidation courante = toutes.stream()
            .filter(e -> "EN_ATTENTE".equals(e.getStatut()))
            .findFirst().orElse(null);
        return courante != null && courante.getId().equals(etape.getId());
    }

    // ============================================================
    // VALIDER UNE ÉTAPE (couche 2)
    // ============================================================
    @Transactional
    public void valider(Long idEtape, Long idValidateur, String commentaire) {
        EtapeValidation etape = etapeValidationRepository.findById(idEtape)
            .orElseThrow(() -> new RuntimeException("Étape introuvable"));

        Utilisateur validateur = utilisateurRepository.findById(idValidateur)
            .orElseThrow(() -> new RuntimeException("Validateur introuvable"));

        if (!"EN_ATTENTE".equals(etape.getStatut())) {
            throw new RuntimeException("Cette étape a déjà été traitée");
        }

        verifierEtapeCourante(etape);
        verifierProfil(validateur, etape);

        etape.setStatut("VALIDEE");
        etape.setValidateur(validateur);
        etape.setDateAction(LocalDateTime.now());
        etape.setCommentaire(commentaire);
        etapeValidationRepository.save(etape);

        DemandeConge demande = etape.getDemande();
        Agent agentDemandeur = demande.getAgent();
        Utilisateur userDemandeur = utilisateurRepository.findByAgentId(agentDemandeur.getId()).orElse(null);

        // >>> JOURNAL : cible commune aux deux cas
        String cibleDemande = "Demande de " + agentDemandeur.getPrenom() + " " + agentDemandeur.getNom();

        List<EtapeValidation> toutes = etapeValidationRepository.findByDemandeIdOrderByOrdreAsc(demande.getId());
        EtapeValidation suivante = toutes.stream()
            .filter(e -> e.getOrdre() > etape.getOrdre() && "EN_ATTENTE".equals(e.getStatut()))
            .findFirst().orElse(null);

        if (suivante != null) {
            suivante.setDateActivation(LocalDateTime.now());
            etapeValidationRepository.save(suivante);

            demande.setStatut("EN_COURS");
            demandeCongeRepository.save(demande);

            // Notifier le demandeur que sa demande avance
            if (userDemandeur != null) {
                String msg = "Votre demande de congé a été validée par " + validateur.getPrenom() + " "
                    + validateur.getNom() + " et passe à l'étape suivante.";
                notificationService.creer(userDemandeur.getId(), msg, "DEMANDE_AVANCE", demande.getId());

                if (agentDemandeur.getEmail() != null) {
                    String contenu = """
                        <div style="font-size:20px;color:#1a1a1a;font-weight:600;margin-bottom:12px;">Votre demande avance</div>
                        <p style="font-size:14px;color:#666;line-height:1.7;margin:0 0 20px;">
                          Bonjour %s,<br><br>
                          Votre demande de congé a été validée par <strong style="color:#1a1a1a;">%s %s</strong> et passe à l'étape suivante du circuit.
                        </p>
                        <div style="background:#F4F8F6;border-radius:8px;padding:18px 20px;margin-bottom:20px;">
                          %s
                        </div>
                        <p style="font-size:14px;color:#666;line-height:1.7;margin:0 0 24px;">
                          Vous serez informé(e) de la suite du traitement.
                        </p>
                        %s
                        """.formatted(
                            agentDemandeur.getPrenom(),
                            validateur.getPrenom(), validateur.getNom(),
                            detailsDemandeHtml(demande),
                            emailService.bouton("Voir ma demande", emailService.getAppUrl())
                        );
                    emailService.envoyerHtml(agentDemandeur.getEmail(),
                        "Votre demande de congé avance", contenu);
                }
            }

            // Notifier le validateur suivant
            Utilisateur validateurSuivant = trouverValidateurDeLEtape(suivante);
            if (validateurSuivant != null && validateurSuivant.getAgent() != null
                && validateurSuivant.getAgent().getEmail() != null) {

                String msgVal = "Une demande de congé de " + agentDemandeur.getPrenom() + " "
                    + agentDemandeur.getNom() + " attend votre validation.";
                notificationService.creer(validateurSuivant.getId(), msgVal, "DEMANDE_A_VALIDER", demande.getId());

                String contenu = """
                    <div style="font-size:20px;color:#1a1a1a;font-weight:600;margin-bottom:12px;">Une demande attend votre validation</div>
                    <p style="font-size:14px;color:#666;line-height:1.7;margin:0 0 20px;">
                      Bonjour %s,<br><br>
                      Une demande de congé de <strong style="color:#1a1a1a;">%s %s</strong> attend votre validation.
                    </p>
                    <div style="background:#F4F8F6;border-radius:8px;padding:18px 20px;margin-bottom:20px;">
                      %s
                    </div>
                    <p style="font-size:14px;color:#666;line-height:1.7;margin:0 0 24px;">
                      Merci de la traiter dès que possible dans l'application.
                    </p>
                    %s
                    """.formatted(
                        validateurSuivant.getAgent().getPrenom(),
                        agentDemandeur.getPrenom(), agentDemandeur.getNom(),
                        detailsDemandeHtml(demande),
                        emailService.bouton("Traiter la demande", emailService.getAppUrl())
                    );
                emailService.envoyerHtml(validateurSuivant.getAgent().getEmail(),
                    "Une demande de congé attend votre validation", contenu);
            }

            // >>> JOURNAL : étape intermédiaire validée
            journalService.enregistrer(
                "VALIDATION_ETAPE",
                "Validation d'une étape",
                cibleDemande,
                "Étape " + etape.getOrdre() + " validée, demande transmise à l'étape suivante"
            );

        } else {
            demande.setStatut("VALIDEE");
            demandeCongeRepository.save(demande);
            decrementerSolde(demande);

            // Notifier le demandeur : demande approuvée définitivement
            if (userDemandeur != null) {
                String msg = "Votre demande de congé a été approuvée définitivement.";
                notificationService.creer(userDemandeur.getId(), msg, "DEMANDE_VALIDEE", demande.getId());
                if (agentDemandeur.getEmail() != null) {
                    String contenu = """
                        <div style="font-size:20px;color:#1a1a1a;font-weight:600;margin-bottom:12px;">Votre demande est approuvée</div>
                        <p style="font-size:14px;color:#666;line-height:1.7;margin:0 0 20px;">
                          Bonjour %s,<br><br>
                          Bonne nouvelle : votre demande de congé a été <strong style="color:#1a1a1a;">approuvée définitivement</strong>.
                        </p>
                        <div style="background:#F4F8F6;border-radius:8px;padding:18px 20px;margin-bottom:16px;">
                          %s
                        </div>
                        <div style="font-size:13px;color:#2E7D32;background:#e8f3e9;border-radius:8px;padding:14px 18px;margin-bottom:24px;line-height:1.5;">
                          Les jours correspondants ont été déduits de votre solde de congés.
                        </div>
                        %s
                        """.formatted(
                            agentDemandeur.getPrenom(),
                            detailsDemandeHtml(demande),
                            emailService.bouton("Voir ma demande", emailService.getAppUrl())
                        );
                    emailService.envoyerHtml(agentDemandeur.getEmail(),
                        "Votre demande de congé est approuvée", contenu);
                }
            }

            // >>> JOURNAL : validation finale
            journalService.enregistrer(
                "VALIDATION_DEMANDE",
                "Validation finale d'une demande",
                cibleDemande,
                "Demande approuvée définitivement, solde décrémenté"
            );
        }
    }
    // Retrouve l'utilisateur qui doit traiter une étape (selon profil + ciblage unité/direction)
    private Utilisateur trouverValidateurDeLEtape(EtapeValidation etape) {
        List<Utilisateur> candidats = utilisateurRepository.findAll();
        for (Utilisateur u : candidats) {
            if (u.getProfil() == null || u.getAgent() == null) continue;
            if (!etape.getProfilAttendu().equalsIgnoreCase(u.getProfil().getCode())) continue;

            // Ciblage unité
            if (etape.getIdUniteAttendue() != null) {
                if (u.getAgent().getUnite() != null
                    && etape.getIdUniteAttendue().equals(u.getAgent().getUnite().getId())) {
                    return u;
                }
                continue;
            }
            // Ciblage direction
            if (etape.getIdDirectionAttendue() != null) {
                if (u.getAgent().getDirection() != null
                    && etape.getIdDirectionAttendue().equals(u.getAgent().getDirection().getId())) {
                    return u;
                }
                continue;
            }
            // Rôle global (ADMIN, DAF, DG, SG) : premier trouvé
            return u;
        }
        return null;
    }

    // Petit utilitaire de mise en forme des dates d'une demande
    private String detailsDemande(DemandeConge d) {
        return "Période : " + d.getDateDebut() + " au " + d.getDateFin()
             + " (" + d.getNombreJours() + " jour(s))";
    }
          // Version HTML des détails d'une demande (pour les emails HTML)
    private String detailsDemandeHtml(DemandeConge d) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String debut = d.getDateDebut() != null ? d.getDateDebut().format(fmt) : "—";
        String fin = d.getDateFin() != null ? d.getDateFin().format(fmt) : "—";
        String dateCreation = d.getDateCreation() != null ? d.getDateCreation().format(fmt) : "—";
        String type = (d.getTypeConge() != null) ? d.getTypeConge().getLibelle() : "—";
        String agent = (d.getAgent() != null)
            ? d.getAgent().getPrenom() + " " + d.getAgent().getNom() : "—";

        return """
            <table style="width:100%%;border-collapse:collapse;font-size:13px;color:#444;">
              <tr><td style="padding:4px 0;color:#999;width:140px;">Agent</td><td style="padding:4px 0;font-weight:600;">%s</td></tr>
              <tr><td style="padding:4px 0;color:#999;">Type de congé</td><td style="padding:4px 0;">%s</td></tr>
              <tr><td style="padding:4px 0;color:#999;">Date de la demande</td><td style="padding:4px 0;">%s</td></tr>
              <tr><td style="padding:4px 0;color:#999;">Période</td><td style="padding:4px 0;">du %s au %s</td></tr>
              <tr><td style="padding:4px 0;color:#999;">Durée</td><td style="padding:4px 0;font-weight:600;">%s jour(s)</td></tr>
            </table>
            """.formatted(agent, type, dateCreation, debut, fin, d.getNombreJours());
    }
    // ============================================================
    // REFUSER UNE ÉTAPE (couche 2)
    // ============================================================
    @Transactional
       public void refuser(Long idEtape, Long idValidateur, String commentaire) {
        if (commentaire == null || commentaire.trim().isEmpty()) {
            throw new RuntimeException("Un commentaire est obligatoire pour refuser une demande");
        }

        EtapeValidation etape = etapeValidationRepository.findById(idEtape)
            .orElseThrow(() -> new RuntimeException("Étape introuvable"));

        Utilisateur validateur = utilisateurRepository.findById(idValidateur)
            .orElseThrow(() -> new RuntimeException("Validateur introuvable"));

        if (!"EN_ATTENTE".equals(etape.getStatut())) {
            throw new RuntimeException("Cette étape a déjà été traitée");
        }

        verifierEtapeCourante(etape);
        verifierProfil(validateur, etape);

        etape.setStatut("REFUSEE");
        etape.setValidateur(validateur);
        etape.setDateAction(LocalDateTime.now());
        etape.setCommentaire(commentaire);
        etapeValidationRepository.save(etape);

        DemandeConge demande = etape.getDemande();
        demande.setStatut("REFUSEE");
        demandeCongeRepository.save(demande);

        // Notifier le demandeur du refus avec le motif
        Agent agentDemandeur = demande.getAgent();
        Utilisateur userDemandeur = utilisateurRepository.findByAgentId(agentDemandeur.getId()).orElse(null);

        if (userDemandeur != null) {
            String msg = "Votre demande de congé a été refusée par " + validateur.getPrenom() + " "
                + validateur.getNom() + ". Motif : " + commentaire;
            notificationService.creer(userDemandeur.getId(), msg, "DEMANDE_REFUSEE", demande.getId());

            if (agentDemandeur.getEmail() != null) {
                String contenu = """
                    <div style="font-size:16px;color:#C0392B;font-weight:bold;margin-bottom:14px;">Votre demande a été refusée</div>
                    <p style="font-size:14px;color:#444444;line-height:1.6;margin:0 0 16px;">
                      Bonjour %s,<br><br>
                      Votre demande de congé a été refusée par <strong>%s %s</strong>.
                    </p>
                    <div style="background:#f4f8f6;border-left:3px solid #1B4D3E;border-radius:6px;padding:14px 16px;margin-bottom:16px;font-size:14px;color:#444444;">
                      %s
                    </div>
                    <div style="background:#fbeaea;border-left:3px solid #C0392B;border-radius:6px;padding:14px 16px;margin-bottom:16px;">
                      <div style="font-size:13px;color:#C0392B;font-weight:bold;margin-bottom:6px;">Motif du refus</div>
                      <div style="font-size:14px;color:#444444;">%s</div>
                    </div>
                    <p style="font-size:14px;color:#444444;line-height:1.6;margin:0;">
                      Pour toute question, rapprochez-vous de votre responsable ou du service RH.
                    </p>
                    %s
                    """.formatted(
                        agentDemandeur.getPrenom(),
                        validateur.getPrenom(), validateur.getNom(),
                        detailsDemandeHtml(demande),
                        commentaire,
                        emailService.bouton("Voir ma demande", emailService.getAppUrl())
                    );
                emailService.envoyerHtml(agentDemandeur.getEmail(),
                    "Votre demande de congé a été refusée", contenu);
            }
        }

        // >>> JOURNAL : refus d'une demande
        journalService.enregistrer(
            "REFUS_DEMANDE",
            "Refus d'une demande",
            "Demande de " + agentDemandeur.getPrenom() + " " + agentDemandeur.getNom(),
            "Motif : " + commentaire
        );
    }
    // Vérifie que l'étape est bien la première EN_ATTENTE (on ne saute pas d'étape)
    private void verifierEtapeCourante(EtapeValidation etape) {
        List<EtapeValidation> toutes = etapeValidationRepository
            .findByDemandeIdOrderByOrdreAsc(etape.getDemande().getId());
        EtapeValidation courante = toutes.stream()
            .filter(e -> "EN_ATTENTE".equals(e.getStatut()))
            .findFirst().orElse(null);
        if (courante == null || !courante.getId().equals(etape.getId())) {
            throw new RuntimeException("Ce n'est pas l'étape en cours de validation");
        }
    }

    // Vérifie que l'utilisateur a le profil ET le ciblage (unité/direction) attendus.
    private void verifierProfil(Utilisateur validateur, EtapeValidation etape) {
        String profilUser = getCodeProfil(validateur);
        String profilAttendu = etape.getProfilAttendu();

        boolean autorise = profilAttendu.equalsIgnoreCase(profilUser);

        // À l'étape DAF, ADMIN peut aussi valider (remplaçant)
        if ("DAF".equalsIgnoreCase(profilAttendu) && "ADMIN".equalsIgnoreCase(profilUser)) {
            autorise = true;
        }

        if (!autorise) {
            throw new RuntimeException("Votre profil (" + profilUser + ") ne permet pas de valider cette étape ("
                + profilAttendu + ")");
        }

        // ===== VÉRIFICATION DU CIBLAGE =====
        Long idUniteUser = null;
        Long idDirectionUser = null;
       if (validateur.getAgent() != null) {
            // Unité : null pour un directeur
            if (validateur.getAgent().getUnite() != null) {
                idUniteUser = validateur.getAgent().getUnite().getId();
            }
            // Direction : lue DIRECTEMENT sur l'agent (jamais via l'unité)
            if (validateur.getAgent().getDirection() != null) {
                idDirectionUser = validateur.getAgent().getDirection().getId();
            }
        }

        if (etape.getIdUniteAttendue() != null) {
            if (idUniteUser == null || !idUniteUser.equals(etape.getIdUniteAttendue())) {
                throw new RuntimeException("Vous n'êtes pas le responsable de l'unité concernée par cette demande");
            }
        }
        if (etape.getIdDirectionAttendue() != null) {
            if (idDirectionUser == null || !idDirectionUser.equals(etape.getIdDirectionAttendue())) {
                throw new RuntimeException("Vous n'êtes pas le responsable de la direction concernée par cette demande");
            }
        }
    }

    private String getCodeProfil(Utilisateur u) {
        if (u.getProfil() == null) return "";
        return u.getProfil().getCode();
    }

    // Décrémente le solde à la validation finale (congés déductibles uniquement)
    private void decrementerSolde(DemandeConge demande) {
        if (demande.getTypeConge() == null) return;
        if (!"DEDUCTIBLE".equalsIgnoreCase(demande.getTypeConge().getCategorie())) return;

        Agent agent = demande.getAgent();
        SoldeConges solde = soldeCongesRepository.findByAgentId(agent.getId()).orElse(null);
        if (solde == null) return;

        BigDecimal pris = solde.getNbJourPrisDeductible() != null
            ? solde.getNbJourPrisDeductible() : BigDecimal.ZERO;
        solde.setNbJourPrisDeductible(pris.add(demande.getNombreJours()));
        soldeCongesRepository.save(solde);

        soldeCongesService.recalculerSoldeAgent(agent);
    }
}