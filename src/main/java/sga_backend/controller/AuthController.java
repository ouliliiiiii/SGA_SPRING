package sga_backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import sga_backend.dto.AgentDTO;
import sga_backend.dto.ChangerMotDePasseRequest;
import sga_backend.dto.DemandeResetRequest;
import sga_backend.dto.LoginRequest;
import sga_backend.dto.ResetMotDePasseRequest;
import sga_backend.dto.UtilisateurResponse;
import sga_backend.entity.Utilisateur;
import sga_backend.repository.UtilisateurRepository;
import sga_backend.service.AuthService;
import sga_backend.service.JwtService;
 
   

// @RestController : répond aux requêtes HTTP avec du JSON
@RestController
@RequestMapping("/api/auth")
public class AuthController {
        
    @Autowired private UtilisateurRepository utilisateurRepository;

    @Autowired
    private AuthService authService;
    @Autowired private JwtService jwtService;

    // ============================================
    // ENDPOINT : LOGIN
    // POST /api/auth/login
    // ============================================
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            Utilisateur utilisateur = authService.login(
                request.getLogin(),
                request.getMotDePasse()
            );

            String token = jwtService.genererToken(utilisateur.getLogin());

             // Enregistrer ce token comme le token actif (invalide les précédents)
            utilisateur.setTokenActif(token);
            utilisateurRepository.save(utilisateur);

            UtilisateurResponse reponse = UtilisateurResponse.depuis(utilisateur);
            reponse.setToken(token);

            return ResponseEntity.ok(reponse);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    } 
   
// 
// ============================================================


    // ============================================
    // GET /api/auth/me?userId=X
    // Renvoie le profil complet de l'utilisateur connecté
    // ============================================
    @GetMapping("/me")
    public ResponseEntity<?> getMonProfil(@RequestParam Long userId) {
        try {
            AgentDTO dto = authService.getMonProfil(userId);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ============================================
    // PUT /api/auth/changer-mot-de-passe
    // ============================================
    @PutMapping("/changer-mot-de-passe")
    public ResponseEntity<?> changerMotDePasse(@RequestBody ChangerMotDePasseRequest request) {
        try {
            authService.changerMotDePasse(request);
            return ResponseEntity.ok("Mot de passe modifié avec succès");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
 

    // ============================================
    // POST /api/auth/mot-de-passe-oublie
    // Étape 1 : demande de code
    // ============================================
    @PostMapping("/mot-de-passe-oublie")
    public ResponseEntity<?> motDePasseOublie(@RequestBody DemandeResetRequest request) {
        try {
            authService.demanderReset(request);
            return ResponseEntity.ok("Un code de vérification a été envoyé à votre email");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ============================================
    // POST /api/auth/reinitialiser-mot-de-passe
    // Étape 2 : validation du code + nouveau mot de passe
    // ============================================
    @PostMapping("/reinitialiser-mot-de-passe")
    public ResponseEntity<?> reinitialiserMotDePasse(@RequestBody ResetMotDePasseRequest request) {
        try {
            authService.reinitialiserMotDePasse(request);
            return ResponseEntity.ok("Mot de passe réinitialisé avec succès");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}