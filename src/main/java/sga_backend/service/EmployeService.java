package sga_backend.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
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
import sga_backend.repository.ProfilRepository;
import sga_backend.repository.SoldeCongesRepository;
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

    @Value("${spring.mail.username}")
    private String emailExpediteur;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ============================================
    // CRÉER UN EMPLOYÉ
    // ============================================
    @Transactional
    public Utilisateur creerEmploye(CreerEmployeRequest request) {

        if (agentRepository.existsByMatricule(request.getMatricule())) {
            throw new RuntimeException("Ce matricule est déjà utilisé");
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
        agent.setNoPoste(request.getNoPoste());
        agent.setEmailPersonnel(request.getEmailPersonnel());
        agent.setCni(request.getCni());
        agent.setDateEmbauche(request.getDateEmbauche());
        agent.setAdresse1(request.getAdresse1());
        agent.setAdresse2(request.getAdresse2());
        agent.setVille(request.getVille());
        agent.setSexe(request.getSexe() != null ? request.getSexe() : null);
        agent.setStatut(request.getStatut() != null ? request.getStatut() : "ACTIF");
        agent.setTypeContrat("CDI");

        int nbEnfants = (request.getEnfants() != null) ? request.getEnfants().size() : 0;
        agent.setNbEnfants(nbEnfants);

        if (request.getIdEntite() != null)
            lieuTravailRepository.findById(request.getIdEntite()).ifPresent(agent::setLieuTravail);
        if (request.getIdDirection() != null)
            directionRepository.findById(request.getIdDirection()).ifPresent(agent::setDirection);
        if (request.getIdFonction() != null)
            fonctionRepository.findById(request.getIdFonction()).ifPresent(agent::setFonction);
        if (request.getIdCategorieSocioPro() != null)
            categorieSocioProRepository.findById(request.getIdCategorieSocioPro()).ifPresent(agent::setCategorieSocioPro);
        if (request.getIdSuperieur() != null)
            agentRepository.findById(request.getIdSuperieur()).ifPresent(agent::setSuperieur);

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
        agent.setNoPoste(request.getNoPoste());
        agent.setEmailPersonnel(request.getEmailPersonnel());
        agent.setAdresse1(request.getAdresse1());
        agent.setAdresse2(request.getAdresse2());
        agent.setVille(request.getVille());
        agent.setDateEmbauche(request.getDateEmbauche());
        agent.setStatut(request.getStatut() != null ? request.getStatut() : "ACTIF");

        // ---- 2) Référentiels ----
        if (request.getIdEntite() != null)
            lieuTravailRepository.findById(request.getIdEntite()).ifPresent(agent::setLieuTravail);
        if (request.getIdDirection() != null)
            directionRepository.findById(request.getIdDirection()).ifPresent(agent::setDirection);
        if (request.getIdFonction() != null)
            fonctionRepository.findById(request.getIdFonction()).ifPresent(agent::setFonction);
        if (request.getIdCategorieSocioPro() != null)
            categorieSocioProRepository.findById(request.getIdCategorieSocioPro()).ifPresent(agent::setCategorieSocioPro);
        if (request.getIdSuperieur() != null)
            agentRepository.findById(request.getIdSuperieur()).ifPresent(agent::setSuperieur);

        // ---- 3) Profil (sur le compte utilisateur lié) ----
        if (request.getIdProfil() != null) {
            utilisateurRepository.findByAgentId(id).ifPresent(u -> {
                profilRepository.findById(request.getIdProfil()).ifPresent(u::setProfil);
                utilisateurRepository.save(u);
            });
        }

        // ---- 4) Enfants : diff par id (update / create / delete) ----
        synchroniserEnfants(agent, request.getEnfants());

        // ---- 5) Solde : upsert ----
        if (request.getSolde() != null) {
            ajusterSolde(agent, request.getSolde());
        }

        agentRepository.save(agent);

        // On renvoie le DTO complet rechargé
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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailExpediteur);
        message.setTo(email);
        message.setSubject("Bienvenue sur SGA — Vos identifiants de connexion");
        message.setText(
            "Bonjour " + prenom + " " + nom + ",\n\n" +
            "Votre compte a été créé sur le Système de Gestion des Absences (SGA).\n\n" +
            "Vos identifiants de connexion :\n" +
            "   Login        : " + email + "\n" +
            "   Mot de passe : " + motDePasse + "\n\n" +
            "Nous vous recommandons de changer votre mot de passe dès votre première " +
            "connexion via la page Mon Profil.\n\n" +
            "Cordialement,\n" +
            "L'équipe administrative"
        );
        mailSender.send(message);
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
        dto.setNoPoste(agent.getNoPoste());
        dto.setEmailPersonnel(agent.getEmailPersonnel());
        dto.setEmail(agent.getEmail());
        dto.setAdresse1(agent.getAdresse1());
        dto.setAdresse2(agent.getAdresse2());
        dto.setVille(agent.getVille());
        dto.setStatut(agent.getStatut());
        dto.setNbEnfants(agent.getNbEnfants());

        // Référentiels en IDs plats + libellés
        if (agent.getLieuTravail() != null) {
            dto.setIdLieuTravail(agent.getLieuTravail().getId());
            dto.setLibelleLieuTravail(agent.getLieuTravail().getLibelle());
        }
        if (agent.getDirection() != null) {
            dto.setIdDirection(agent.getDirection().getId());
            dto.setLibelleDirection(agent.getDirection().getLibelle());
        }
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
}