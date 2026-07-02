package sga_backend.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import sga_backend.dto.AgentDTO;
import sga_backend.dto.ChangerMotDePasseRequest;
import sga_backend.dto.DemandeResetRequest;
import sga_backend.dto.ResetMotDePasseRequest;
import sga_backend.entity.Agent;
import sga_backend.entity.Utilisateur;
import sga_backend.repository.UtilisateurRepository;

// @Service : contient la logique métier d'authentification
@Service
public class AuthService {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ============================================
    // MÉTHODE : LOGIN
    // ============================================
    public Utilisateur login(String login, String motDePasse) {

        // Étape 1 : chercher l'utilisateur par son login
        Utilisateur utilisateur = utilisateurRepository.findByLogin(login)
            .orElseThrow(() -> new RuntimeException("Login ou mot de passe incorrect"));

        // Étape 2 : vérifier le mot de passe avec BCrypt
        if (!passwordEncoder.matches(motDePasse, utilisateur.getMotDePasse())) {
            throw new RuntimeException("Login ou mot de passe incorrect");
        }

        // Étape 3 : vérifier que le compte est actif
       // Par
    if (!utilisateur.getStatut().equals("VALIDE")) {
            throw new RuntimeException("Compte inactif. Veuillez contacter l'administrateur.");
        }

        return utilisateur;
    }

    // ============================================
    // MÉTHODE : CHANGER LE STATUT
    // (utilisée par EmployeService pour activer un compte)
    // ============================================
    public Utilisateur changerStatut(Long id, String nouveauStatut) {

        Utilisateur utilisateur = utilisateurRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        utilisateur.setStatut(nouveauStatut);
        return utilisateurRepository.save(utilisateur);
    }

 

    // ============================================
    // GET : profil de l'utilisateur connecté
    // Renvoie l'AgentDTO complet (enfants, solde, libellés)
    // ============================================
    public AgentDTO getMonProfil(Long userId) {
        Utilisateur utilisateur = utilisateurRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        Agent agent = utilisateur.getAgent();
        if (agent == null) {
            throw new RuntimeException("Aucun agent associé à ce compte");
        }

        // On réutilise le mapping déjà construit dans EmployeService
        return employeService.getEmploye(agent.getId());
    }


         @Autowired private EmployeService employeService;
    //    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    // ============================================
    // PUT : changer son mot de passe
    // ============================================
    public void changerMotDePasse(ChangerMotDePasseRequest request) {
        Utilisateur utilisateur = utilisateurRepository.findById(request.getUserId())
            .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        // Vérifier l'ancien mot de passe
        if (!passwordEncoder.matches(request.getAncienMotDePasse(), utilisateur.getMotDePasse())) {
            throw new RuntimeException("L'ancien mot de passe est incorrect");
        }

        // Contrôle minimal sur le nouveau
        if (request.getNouveauMotDePasse() == null || request.getNouveauMotDePasse().length() < 6) {
            throw new RuntimeException("Le nouveau mot de passe doit contenir au moins 6 caractères");
        }

        utilisateur.setMotDePasse(passwordEncoder.encode(request.getNouveauMotDePasse()));
        utilisateurRepository.save(utilisateur);
    }


    // ============================================
    // ÉTAPE 1 : générer et envoyer le code de réinitialisation
    // ============================================
    public void demanderReset(DemandeResetRequest request) {
        Utilisateur utilisateur = utilisateurRepository.findByLogin(request.getLogin())
            .orElseThrow(() -> new RuntimeException("Aucun compte associé à cet email"));

        // Générer un code à 6 chiffres
        SecureRandom random = new SecureRandom();
        String code = String.format("%06d", random.nextInt(1_000_000));

        // Stocker le code + expiration (5 minutes)
        utilisateur.setCodeReset(code);
        utilisateur.setCodeResetExpiration(LocalDateTime.now().plusMinutes(5));
        utilisateurRepository.save(utilisateur);

        // Envoyer par email
        envoyerEmailCodeReset(utilisateur.getLogin(), utilisateur.getPrenom(), code);
    }

    // ============================================
    // ÉTAPE 2 : vérifier le code et changer le mot de passe
    // ============================================
    public void reinitialiserMotDePasse(ResetMotDePasseRequest request) {
        Utilisateur utilisateur = utilisateurRepository.findByLogin(request.getLogin())
            .orElseThrow(() -> new RuntimeException("Aucun compte associé à cet email"));

        // Vérifier qu'un code existe
        if (utilisateur.getCodeReset() == null || utilisateur.getCodeResetExpiration() == null) {
            throw new RuntimeException("Aucune demande de réinitialisation en cours");
        }

        // Vérifier l'expiration
        if (LocalDateTime.now().isAfter(utilisateur.getCodeResetExpiration())) {
            throw new RuntimeException("Le code a expiré, veuillez recommencer");
        }

        // Vérifier le code
        if (!utilisateur.getCodeReset().equals(request.getCode())) {
            throw new RuntimeException("Code incorrect");
        }

        // Contrôle minimal sur le nouveau mot de passe
        if (request.getNouveauMotDePasse() == null || request.getNouveauMotDePasse().length() < 6) {
            throw new RuntimeException("Le mot de passe doit contenir au moins 6 caractères");
        }

        // Changer le mot de passe et invalider le code
        utilisateur.setMotDePasse(passwordEncoder.encode(request.getNouveauMotDePasse()));
        utilisateur.setCodeReset(null);
        utilisateur.setCodeResetExpiration(null);
        utilisateurRepository.save(utilisateur);
    }

    // ============================================
    // Email contenant le code
    // ============================================
    @Autowired
    private JavaMailSender mailSender;
    @Value("${spring.mail.username}")
    private String emailExpediteur;
    private void envoyerEmailCodeReset(String email, String prenom, String code) {
        org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
        message.setFrom(emailExpediteur);
        message.setTo(email);
        message.setSubject("SGA — Code de réinitialisation de mot de passe");
        message.setText(
            "Bonjour " + prenom + ",\n\n" +
            "Vous avez demandé la réinitialisation de votre mot de passe.\n\n" +
            "Votre code de vérification est : " + code + "\n\n" +
            "Ce code est valable 5 minutes. Si vous n'êtes pas à l'origine de cette demande, " +
            "ignorez cet email.\n\n" +
            "Cordialement,\n" +
            "L'équipe administrative SGA"
        );
        mailSender.send(message);
    }
}