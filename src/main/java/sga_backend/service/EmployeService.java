package sga_backend.service;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sga_backend.dto.AgentDTO;
import sga_backend.dto.CreerEmployeRequest;
import sga_backend.dto.EnfantAgentDTO;
import sga_backend.dto.SoldeAjustementDTO;
import sga_backend.entity.Agent;
import sga_backend.entity.EnfantAgent;
import sga_backend.entity.SoldeConges;
import sga_backend.entity.Utilisateur;
import sga_backend.repository.AgentRepository;
import sga_backend.repository.CategorieSocioProRepository;
import sga_backend.repository.DirectionRepository;
import sga_backend.repository.EnfantAgentRepository;
import sga_backend.repository.FonctionRepository;
import sga_backend.repository.LieuTravailRepository;
import sga_backend.repository.PosteRepository;
import sga_backend.repository.ProfilRepository;
import sga_backend.repository.SoldeCongesRepository;
import sga_backend.repository.UniteRepository;
import sga_backend.repository.UtilisateurRepository;


@Service
public class EmployeService {

    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private LieuTravailRepository lieuTravailRepository;
    @Autowired private DirectionRepository directionRepository;
    @Autowired private FonctionRepository fonctionRepository;
    @Autowired private CategorieSocioProRepository categorieSocioProRepository;
    @Autowired private ProfilRepository profilRepository;
    @Autowired private EnfantAgentRepository enfantAgentRepository;
    @Autowired private SoldeCongesRepository soldeCongesRepository;
    @Autowired private JavaMailSender mailSender;
    @Autowired private UniteRepository uniteRepository;
    @Autowired private PosteRepository posteRepository;
    @Autowired private EmailService emailService;
   
    @Value("${sga.app.url:http://192.168.1.169:4200}")
    private String appUrl;

    @Value("${spring.mail.username}")
    private String emailExpediteur;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ============================================
    // CRÉER UN EMPLOYÉ
    // ============================================
    @Transactional
         public Utilisateur creerEmploye(CreerEmployeRequest request) {

        // ---- Vérifications d'unicité ----
        if (agentRepository.existsByMatricule(request.getMatricule())) {
            throw new RuntimeException("Ce matricule est déjà utilisé");
        }
        if (agentRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Un employé avec cet email professionnel existe déjà");
        }
        if (utilisateurRepository.existsByLogin(request.getEmail())) {
            throw new RuntimeException("Un compte avec cet email existe déjà");
        }

        // ---- ÉTAPE 1 : Créer l'agent ----
        Agent agent = new Agent();
        agent.setMatricule(request.getMatricule());
        agent.setNom(request.getNom());
        agent.setPrenom(request.getPrenom());
        agent.setDateNaissance(request.getDateNaissance());
        agent.setEmail(request.getEmail());
        agent.setTelephone(request.getTelephone());
        agent.setEmailPersonnel(request.getEmailPersonnel());
        agent.setCni(request.getCni());
        agent.setDateEmbauche(request.getDateEmbauche());
        agent.setAdresse1(request.getAdresse1());
        agent.setAdresse2(request.getAdresse2());
        agent.setVille(request.getVille());
        agent.setSexe(request.getSexe() != null ? request.getSexe() : null);
        agent.setStatut(request.getStatut() != null ? request.getStatut() : "ACTIF");
        agent.setTypeContrat("CDI");
        agent.setNumeroDePoste(request.getNumeroDePoste());

        int nbEnfants = (request.getEnfants() != null) ? request.getEnfants().size() : 0;
        agent.setNbEnfants(nbEnfants);

        // ---- Référentiels simples ----
        if (request.getIdFonction() != null)
            fonctionRepository.findById(request.getIdFonction()).ifPresent(agent::setFonction);
        if (request.getIdCategorieSocioPro() != null)
            categorieSocioProRepository.findById(request.getIdCategorieSocioPro()).ifPresent(agent::setCategorieSocioPro);
        if (request.getIdSuperieur() != null)
            agentRepository.findById(request.getIdSuperieur()).ifPresent(agent::setSuperieur);

        // ---- Poste occupé (intitulé) ----
        if (request.getIdPoste() != null) {
            posteRepository.findById(request.getIdPoste()).ifPresent(agent::setPosteOccupe);
        } else {
            agent.setPosteOccupe(null);
        }

        // ---- Détermination unité + direction selon le profil ----
        String codeProfilAgent = null;
        if (request.getIdProfil() != null) {
            codeProfilAgent = profilRepository.findById(request.getIdProfil())
                .map(p -> p.getCode()).orElse(null);
        }

        boolean sansDivision = "DIRECTEUR".equals(codeProfilAgent)
                            || "DG".equals(codeProfilAgent)
                            || "SG".equals(codeProfilAgent)
                            || "DAF".equals(codeProfilAgent);

        if (sansDivision) {
            // Directeur / DG / SG / DAF : pas de division, direction choisie directement
            agent.setUnite(null);
            if (request.getIdDirection() != null) {
                directionRepository.findById(request.getIdDirection()).ifPresent(agent::setDirection);
            } else {
                agent.setDirection(null);
            }
        } else {
            // Agent / chef division / CSR : unité gardée, direction déduite de l'unité
            if (request.getIdUnite() != null) {
                uniteRepository.findById(request.getIdUnite()).ifPresent(agent::setUnite);
            }
            if (agent.getUnite() != null && agent.getUnite().getDirection() != null) {
                agent.setDirection(agent.getUnite().getDirection());
            } else if (request.getIdDirection() != null) {
                directionRepository.findById(request.getIdDirection()).ifPresent(agent::setDirection);
            }
        }

        // ---- Vérifier l'unicité du responsable ----
        verifierUniciteResponsable(codeProfilAgent, request.getIdUnite(), request.getIdDirection(), null);

        Agent agentSauvegarde = agentRepository.save(agent);

        // ---- ÉTAPE 2 : Sauvegarder les enfants ----
        if (request.getEnfants() != null && !request.getEnfants().isEmpty()) {
            for (EnfantAgentDTO enfantDTO : request.getEnfants()) {
                if (enfantDTO.getDateNaissance() != null) {
                    EnfantAgent enfant = new EnfantAgent();
                    enfant.setAgent(agentSauvegarde);
                    enfant.setPrenom(enfantDTO.getPrenom());
                    enfant.setDateNaissance(enfantDTO.getDateNaissance());
                    enfantAgentRepository.save(enfant);
                }
            }
        }

        // ---- ÉTAPE 3 : Mot de passe ----
        String motDePasseGenere = genererMotDePasse();

        // ---- ÉTAPE 4 : Compte utilisateur ----
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setNom(request.getNom());
        utilisateur.setPrenom(request.getPrenom());
        utilisateur.setLogin(request.getEmail());
        utilisateur.setMotDePasse(passwordEncoder.encode(motDePasseGenere));
        utilisateur.setStatut("VALIDE");
        utilisateur.setAgent(agentSauvegarde);

        if (request.getIdProfil() != null)
            profilRepository.findById(request.getIdProfil()).ifPresent(utilisateur::setProfil);

        Utilisateur utilisateurSauvegarde = utilisateurRepository.save(utilisateur);

        // ---- ÉTAPE 5 : Email ----
        envoyerEmailBienvenue(request.getEmail(), request.getNom(), request.getPrenom(), motDePasseGenere);

        return utilisateurSauvegarde;
    }

    // ============================================
    // MODIFIER UN EMPLOYÉ (agent + enfants + solde) — TRANSACTIONNEL
    // ============================================
    @Transactional
         public AgentDTO modifierEmploye(Long id, CreerEmployeRequest request) {

        Agent agent = agentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Agent introuvable"));

        // ---- 1) Champs simples de l'agent ----
        agent.setNom(request.getNom());
        agent.setPrenom(request.getPrenom());
        agent.setDateNaissance(request.getDateNaissance());
        agent.setCni(request.getCni());
        agent.setSexe(request.getSexe());
        agent.setTelephone(request.getTelephone());
        agent.setNumeroDePoste(request.getNumeroDePoste());
        agent.setEmailPersonnel(request.getEmailPersonnel());
        agent.setAdresse1(request.getAdresse1());
        agent.setAdresse2(request.getAdresse2());
        agent.setVille(request.getVille());
        agent.setDateEmbauche(request.getDateEmbauche());
        agent.setStatut(request.getStatut() != null ? request.getStatut() : "ACTIF");

        // ---- Poste occupé (intitulé) ----
        if (request.getIdPoste() != null) {
            posteRepository.findById(request.getIdPoste()).ifPresent(agent::setPosteOccupe);
        } else {
            agent.setPosteOccupe(null);
        }

        // ---- 2) Référentiels simples ----
        if (request.getIdFonction() != null)
            fonctionRepository.findById(request.getIdFonction()).ifPresent(agent::setFonction);
        if (request.getIdCategorieSocioPro() != null)
            categorieSocioProRepository.findById(request.getIdCategorieSocioPro()).ifPresent(agent::setCategorieSocioPro);
        if (request.getIdSuperieur() != null)
            agentRepository.findById(request.getIdSuperieur()).ifPresent(agent::setSuperieur);

        // ---- 3) Détermination unité + direction selon le profil ----
        String codeProfilAgent = null;
        if (request.getIdProfil() != null) {
            codeProfilAgent = profilRepository.findById(request.getIdProfil())
                .map(p -> p.getCode()).orElse(null);
        }

        boolean sansDivision = "DIRECTEUR".equals(codeProfilAgent)
                            || "DG".equals(codeProfilAgent)
                            || "SG".equals(codeProfilAgent)
                            || "DAF".equals(codeProfilAgent);

        if (sansDivision) {
            // Directeur / DG / SG / DAF : pas de division, direction choisie directement
            agent.setUnite(null);
            if (request.getIdDirection() != null) {
                directionRepository.findById(request.getIdDirection()).ifPresent(agent::setDirection);
            } else {
                agent.setDirection(null);
            }
        } else {
            // Agent / chef division / CSR : unité gardée, direction déduite de l'unité
            if (request.getIdUnite() != null) {
                uniteRepository.findById(request.getIdUnite()).ifPresent(agent::setUnite);
            } else {
                agent.setUnite(null);
            }
            if (agent.getUnite() != null && agent.getUnite().getDirection() != null) {
                agent.setDirection(agent.getUnite().getDirection());
            } else if (request.getIdDirection() != null) {
                directionRepository.findById(request.getIdDirection()).ifPresent(agent::setDirection);
            }
        }

        // ---- Vérifier l'unicité du responsable (en excluant l'agent lui-même) ----
        verifierUniciteResponsable(codeProfilAgent, request.getIdUnite(), request.getIdDirection(), id);

        // ---- 4) Profil (sur le compte utilisateur lié) ----
        if (request.getIdProfil() != null) {
            utilisateurRepository.findByAgentId(id).ifPresent(u -> {
                profilRepository.findById(request.getIdProfil()).ifPresent(u::setProfil);
                utilisateurRepository.save(u);
            });
        }

        // ---- 5) Enfants ----
        synchroniserEnfants(agent, request.getEnfants());

        // ---- 6) Solde ----
        if (request.getSolde() != null) {
            ajusterSolde(agent, request.getSolde());
        }

        agentRepository.save(agent);

        return toDto(agentRepository.findByIdWithEnfants(id).orElse(agent));
    }

    // ---- Synchronisation des enfants (diff) ----
    private void synchroniserEnfants(Agent agent, List<EnfantAgentDTO> enfantsDTO) {
        List<EnfantAgent> existants = enfantAgentRepository.findByAgentId(agent.getId());

        // Ids reçus du frontend (non-null = à conserver/mettre à jour)
        Set<Long> idsRecus = new HashSet<>();
        if (enfantsDTO != null) {
            for (EnfantAgentDTO dto : enfantsDTO) {
                if (dto.getId() != null) idsRecus.add(dto.getId());
            }
        }

        // a) Suppressions : enfants en base absents du payload
        for (EnfantAgent existant : existants) {
            if (!idsRecus.contains(existant.getId())) {
                enfantAgentRepository.delete(existant);
            }
        }

        // b) Créations et mises à jour
        if (enfantsDTO != null) {
            for (EnfantAgentDTO dto : enfantsDTO) {
                if (dto.getDateNaissance() == null) continue; // ligne vide ignorée

                if (dto.getId() == null) {
                    // nouvel enfant
                    EnfantAgent nouveau = new EnfantAgent();
                    nouveau.setAgent(agent);
                    nouveau.setPrenom(dto.getPrenom());
                    nouveau.setDateNaissance(dto.getDateNaissance());
                    enfantAgentRepository.save(nouveau);
                } else {
                    // enfant existant à mettre à jour
                    enfantAgentRepository.findById(dto.getId()).ifPresent(e -> {
                        e.setPrenom(dto.getPrenom());
                        e.setDateNaissance(dto.getDateNaissance());
                        enfantAgentRepository.save(e);
                    });
                }
            }
        }

        // c) Recalcul du nombre d'enfants
        agent.setNbEnfants(enfantAgentRepository.findByAgentId(agent.getId()).size());
    }

    // ---- Ajustement manuel du solde (upsert) ----
    private void ajusterSolde(Agent agent, SoldeAjustementDTO dto) {
        SoldeConges solde = soldeCongesRepository.findByAgentId(agent.getId())
            .orElseGet(() -> {
                SoldeConges s = new SoldeConges();
                s.setAgent(agent);
                return s;
            });

        if (dto.getNbCongesAcquis() != null)         solde.setNbCongesAcquis(dto.getNbCongesAcquis());
        if (dto.getNbCongesReliquat() != null)       solde.setNbCongesReliquat(dto.getNbCongesReliquat());
        if (dto.getNbCongesSupplementaire() != null) solde.setNbCongesSupplementaire(dto.getNbCongesSupplementaire());
        if (dto.getNbJourSansSolde() != null)        solde.setNbJourSansSolde(dto.getNbJourSansSolde());

        solde.setDateMaj(LocalDateTime.now());
        soldeCongesRepository.save(solde);
    }

    // ============================================
    // GÉNÉRATION MOT DE PASSE
    // ============================================
    private String genererMotDePasse() {
        String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder motDePasse = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            motDePasse.append(caracteres.charAt(random.nextInt(caracteres.length())));
        }
        return motDePasse.toString();
    }

    // ============================================
    // EMAIL DE BIENVENUE
    // ============================================
       private void envoyerEmailBienvenue(String email, String nom, String prenom, String motDePasse) {
        String contenu = """
            <div style="font-size:16px;color:#1B4D3E;font-weight:bold;margin-bottom:14px;">Bienvenue, %s %s</div>

            <p style="font-size:14px;color:#444444;line-height:1.6;margin:0 0 18px;">
              Votre compte a été créé avec succès sur le Système de Gestion des Absences.
              Voici vos identifiants de connexion :
            </p>

            <div style="background:#f4f8f6;border-left:3px solid #1B4D3E;border-radius:6px;padding:16px 18px;margin-bottom:18px;">
              <div style="font-size:13px;color:#666666;margin-bottom:6px;">Identifiant (email professionnel)</div>
              <div style="font-size:15px;color:#1B4D3E;font-weight:bold;margin-bottom:14px;">%s</div>
              <div style="font-size:13px;color:#666666;margin-bottom:6px;">Mot de passe temporaire</div>
              <div style="font-size:15px;color:#1B4D3E;font-weight:bold;font-family:monospace;">%s</div>
            </div>

            <p style="font-size:13px;color:#B07D0A;background:#fbf3e0;border-radius:6px;padding:12px 14px;margin:0 0 4px;line-height:1.5;">
              Pour votre sécurité, veuillez changer ce mot de passe lors de votre première connexion via la page Mon Profil.
            </p>

            %s
            """.formatted(prenom, nom, email, motDePasse, emailService.bouton("Se connecter", appUrl));

        emailService.envoyerHtml(email, "Bienvenue sur SGA — Vos identifiants de connexion", contenu);
    }

    // ============================================
    // MAPPING ENTITÉ -> DTO
    // ============================================
    private AgentDTO toDto(Agent agent) {
        AgentDTO dto = new AgentDTO();

        dto.setId(agent.getId());
        dto.setMatricule(agent.getMatricule());
        dto.setNom(agent.getNom());
        dto.setPrenom(agent.getPrenom());
        dto.setDateNaissance(agent.getDateNaissance());
        dto.setDateEmbauche(agent.getDateEmbauche());
        dto.setCni(agent.getCni());
        dto.setSexe(agent.getSexe());
        dto.setTelephone(agent.getTelephone());
       dto.setPoste(agent.getPosteOccupe() != null ? agent.getPosteOccupe().getLibelle() : null);
        dto.setIdPoste(agent.getPosteOccupe() != null ? agent.getPosteOccupe().getId() : null);
       dto.setNumeroDePoste(agent.getNumeroDePoste());
        dto.setEmailPersonnel(agent.getEmailPersonnel());
        dto.setEmail(agent.getEmail());
        dto.setAdresse1(agent.getAdresse1());
        dto.setAdresse2(agent.getAdresse2());
        dto.setVille(agent.getVille());
        dto.setStatut(agent.getStatut());
        dto.setNbEnfants(agent.getNbEnfants());

        // Référentiels en IDs plats + libellés

        if (agent.getFonction() != null) {
            dto.setIdFonction(agent.getFonction().getId());
            dto.setLibelleFonction(agent.getFonction().getLibelle());
        }
        if (agent.getCategorieSocioPro() != null) {
            dto.setIdCategorieSocioPro(agent.getCategorieSocioPro().getId());
            dto.setLibelleCategorieSocioPro(agent.getCategorieSocioPro().getLibelle());
        }
        if (agent.getSuperieur() != null) {
            dto.setIdSuperieur(agent.getSuperieur().getId());
            dto.setNomSuperieur(agent.getSuperieur().getNom() + " " + agent.getSuperieur().getPrenom());
        }
// Direction : lue DIRECTEMENT sur l'agent (toujours remplie, même sans unité)
if (agent.getDirection() != null) {
    dto.setIdDirection(agent.getDirection().getId());
    dto.setLibelleDirection(agent.getDirection().getLibelle());
}

// Unité (division) : présente seulement pour les agents en division
if (agent.getUnite() != null) {
    dto.setIdUniteo(agent.getUnite().getId());
    dto.setLibelleUniteo(agent.getUnite().getLibelle());

        if (agent.getUnite().getLieuTravail() != null) {
        dto.setIdLieuTravail(agent.getUnite().getLieuTravail().getId());
        dto.setLibelleLieuTravail(agent.getUnite().getLieuTravail().getLibelle());
        if (agent.getUnite().getLieuTravail().getTypeEntite() != null) {
            dto.setTypeLieuTravail(agent.getUnite().getLieuTravail().getTypeEntite().name());
        }
        }
    }
            // Si le type n'a pas pu être déterminé (agent sans unité, ex. directeur), on le considère au Siège
            if (dto.getTypeLieuTravail() == null) {
                dto.setTypeLieuTravail("SIEGE");
            }
            // Profil (depuis le compte utilisateur lié)
            utilisateurRepository.findByAgentId(agent.getId()).ifPresent(u -> {
                if (u.getProfil() != null) {
                    dto.setIdProfil(u.getProfil().getId());
                    dto.setLibelleProfil(u.getProfil().getLibelle());
                }
            });

            // Enfants (avec id pour l'update ciblé)
            dto.setEnfants(
                agent.getEnfants() != null
                ? agent.getEnfants().stream().map(e -> {
                    EnfantAgentDTO enfant = new EnfantAgentDTO();
                    enfant.setId(e.getId());
                    enfant.setPrenom(e.getPrenom());
                    enfant.setDateNaissance(e.getDateNaissance());
                    return enfant;
                }).toList()
                : new ArrayList<>()
            );

            // Solde (peut être null)
            soldeCongesRepository.findByAgentId(agent.getId()).ifPresent(s -> {
                SoldeAjustementDTO sdto = new SoldeAjustementDTO();
                sdto.setNbCongesAcquis(s.getNbCongesAcquis());
                sdto.setNbCongesReliquat(s.getNbCongesReliquat());
                sdto.setNbCongesSupplementaire(s.getNbCongesSupplementaire());
                sdto.setNbJourSansSolde(s.getNbJourSansSolde());
                sdto.setNbJourPrisDeductible(s.getNbJourPrisDeductible());
                sdto.setNbCongesEncours(s.getNbCongesEncours());
                sdto.setNbCongesEchus(s.getNbCongesEchus());
                sdto.setDateDernierCalcul(s.getDateDernierCalcul());
                dto.setSolde(sdto);
            });

            return dto;
        }

    public Agent getEmployeById(Long id) {
        return agentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Agent introuvable"));
    }

    public AgentDTO getEmploye(Long id) {
        Agent agent = agentRepository.findByIdWithEnfants(id)
            .orElseThrow(() -> new RuntimeException("Agent introuvable"));
        return toDto(agent);
    }

    public List<AgentDTO> getAllAgents() {
        return agentRepository.findAll()
            .stream()
            .map(this::toDto)
            .toList();
    }
    // ============================================================
// Génère un fichier Excel (.xlsx) avec TOUTES les données des employés
// ============================================================



public byte[] genererExcelEmployes(List<Long> ids) throws Exception{

   List<Agent> agents;
    if (ids != null && !ids.isEmpty()) {
        agents = agentRepository.findAllById(ids);  // seulement les sélectionnés
    } else {
        agents = agentRepository.findAll();  // tous si aucune sélection
    }

    try (XSSFWorkbook workbook = new XSSFWorkbook();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {

        Sheet sheet = workbook.createSheet("Employés");

        // ===== Style de l'en-tête =====
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        // ===== En-têtes de colonnes =====
        String[] entetes = {
            "Matricule", "Nom", "Prénom", "Date naissance", "Sexe", "CNI",
            "Date embauche", "Type contrat", "Poste",
            "Direction", "Division", "Lieu de travail", "Fonction", "Catégorie socio-pro",
            "Statut", "Profil",
            "Tél. personnel", "Email personnel", "Email pro (login)",
            "Adresse 1", "Adresse 2", "Ville", "Nb enfants",
            "Congés acquis", "Reliquat", "Jours pris", "En cours", "Échus"
        };

        Row ligneEntete = sheet.createRow(0);
        for (int i = 0; i < entetes.length; i++) {
            Cell cell = ligneEntete.createCell(i);
            cell.setCellValue(entetes[i]);
            cell.setCellStyle(headerStyle);
        }

        // ===== Données =====
        int numLigne = 1;
        for (Agent a : agents) {
            Row row = sheet.createRow(numLigne++);
            int c = 0;

            row.createCell(c++).setCellValue(nz(a.getMatricule()));
            row.createCell(c++).setCellValue(nz(a.getNom()));
            row.createCell(c++).setCellValue(nz(a.getPrenom()));
            row.createCell(c++).setCellValue(a.getDateNaissance() != null ? a.getDateNaissance().toString() : "");
            row.createCell(c++).setCellValue(nz(a.getSexe()));
            row.createCell(c++).setCellValue(nz(a.getCni()));
            row.createCell(c++).setCellValue(a.getDateEmbauche() != null ? a.getDateEmbauche().toString() : "");
            row.createCell(c++).setCellValue(nz(a.getTypeContrat()));
            row.createCell(c++).setCellValue(nz(a.getNumeroDePoste()));

            row.createCell(c++).setCellValue(a.getDirection() != null ? nz(a.getDirection().getLibelle()) : "");
            row.createCell(c++).setCellValue(a.getUnite() != null ? nz(a.getUnite().getLibelle()) : "");
String lieu = "";
if (a.getUnite() != null && a.getUnite().getLieuTravail() != null) {
    lieu = nz(a.getUnite().getLieuTravail().getLibelle());
}
row.createCell(c++).setCellValue(lieu);            row.createCell(c++).setCellValue(a.getFonction() != null ? nz(a.getFonction().getLibelle()) : "");
            row.createCell(c++).setCellValue(a.getCategorieSocioPro() != null ? nz(a.getCategorieSocioPro().getLibelle()) : "");

            row.createCell(c++).setCellValue(nz(a.getStatut()));

            // Profil (via l'utilisateur lié)
            Utilisateur u = utilisateurRepository.findByAgentId(a.getId()).orElse(null);
            String profil = (u != null && u.getProfil() != null) ? u.getProfil().getLibelle() : "";
            row.createCell(c++).setCellValue(profil);

            row.createCell(c++).setCellValue(nz(a.getTelephone()));
            row.createCell(c++).setCellValue(nz(a.getEmailPersonnel()));
            row.createCell(c++).setCellValue(nz(a.getEmail()));
            row.createCell(c++).setCellValue(nz(a.getAdresse1()));
            row.createCell(c++).setCellValue(nz(a.getAdresse2()));
            row.createCell(c++).setCellValue(nz(a.getVille()));
            row.createCell(c++).setCellValue(a.getNbEnfants() != null ? a.getNbEnfants() : 0);

            // Solde
            SoldeConges s = soldeCongesRepository.findByAgentId(a.getId()).orElse(null);
            row.createCell(c++).setCellValue(s != null && s.getNbCongesAcquis() != null ? s.getNbCongesAcquis().doubleValue() : 0);
            row.createCell(c++).setCellValue(s != null && s.getNbCongesReliquat() != null ? s.getNbCongesReliquat().doubleValue() : 0);
            row.createCell(c++).setCellValue(s != null && s.getNbJourPrisDeductible() != null ? s.getNbJourPrisDeductible().doubleValue() : 0);
            row.createCell(c++).setCellValue(s != null && s.getNbCongesEncours() != null ? s.getNbCongesEncours().doubleValue() : 0);
            row.createCell(c++).setCellValue(s != null && s.getNbCongesEchus() != null ? s.getNbCongesEchus().doubleValue() : 0);
        }

        // Ajuste la largeur des colonnes
        for (int i = 0; i < entetes.length; i++) {
            sheet.autoSizeColumn(i);
        }

        workbook.write(out);
        return out.toByteArray();
    }
}

// Petit helper pour éviter les null
private String nz(String s) {
    return s != null ? s : "";
}
// Vérifie qu'on ne crée pas un 2e responsable sur une structure qui en a déjà un.
    // idAgentExclu : null en création, l'id de l'agent en modification.
    private void verifierUniciteResponsable(String codeProfil, Long idUnite, Long idDirection, Long idAgentExclu) {
        if (codeProfil == null) return;

        // CHEF_DIVISION : un seul par division
        if ("CHEF_DIVISION".equalsIgnoreCase(codeProfil) && idUnite != null) {
            if (agentRepository.compterParProfilEtUnite("CHEF_DIVISION", idUnite, idAgentExclu) > 0) {
                throw new RuntimeException("Cette division a déjà un chef de division.");
            }
        }

        // CSR : un seul par service régional (CSUR)
        if ("CSR".equalsIgnoreCase(codeProfil) && idUnite != null) {
            if (agentRepository.compterParProfilEtUnite("CSR", idUnite, idAgentExclu) > 0) {
                throw new RuntimeException("Cette antenne régionale a déjà un chef de service régional.");
            }
        }

        // DIRECTEUR : un seul par direction/cellule (couvre aussi les coordonnateurs de cellule)
        if ("DIRECTEUR".equalsIgnoreCase(codeProfil) && idDirection != null) {
            if (agentRepository.compterParProfilEtDirection("DIRECTEUR", idDirection, idAgentExclu) > 0) {
                throw new RuntimeException("Cette direction/cellule a déjà un directeur (ou coordonnateur).");
            }
        }
                // Rôles uniques au sommet : un seul DG, un seul SG, un seul DAFCH dans toute l'organisation
        if ("DG".equalsIgnoreCase(codeProfil) || "SG".equalsIgnoreCase(codeProfil) || "DAF".equalsIgnoreCase(codeProfil)) {
            if (agentRepository.compterParProfil(codeProfil, idAgentExclu) > 0) {
                String role = "DG".equalsIgnoreCase(codeProfil) ? "Directeur Général"
                            : "SG".equalsIgnoreCase(codeProfil) ? "Secrétaire Général"
                            : "Directeur de l'Administration, des Finances et du Capital humain";
                throw new RuntimeException("Il existe déjà un " + role + " dans l'organisation.");
            }
        }
    }
}