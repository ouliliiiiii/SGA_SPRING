package sga_backend.service;

import java.time.LocalDateTime;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import sga_backend.entity.JournalActivite;
import sga_backend.entity.Utilisateur;
import sga_backend.repository.JournalActiviteRepository;
import sga_backend.repository.UtilisateurRepository;

@Service
public class JournalService {

    private final JournalActiviteRepository journalRepository;
    private final UtilisateurRepository utilisateurRepository;

    public JournalService(JournalActiviteRepository journalRepository,
                          UtilisateurRepository utilisateurRepository) {
        this.journalRepository = journalRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    /**
     * Enregistre une ligne de journal pour l'utilisateur courant (recupere depuis le token).
     *
     * @param action        code technique (ex. CREATION_EMPLOYE, VALIDATION_DEMANDE, CONNEXION)
     * @param libelleAction libelle lisible (ex. "Creation d'un employe")
     * @param cible         sur quoi porte l'action (ex. "FALL Moussa")
     * @param details       complement optionnel (peut etre null)
     */
    public void enregistrer(String action, String libelleAction, String cible, String details) {
        try {
            JournalActivite ligne = new JournalActivite();
            ligne.setAction(action);
            ligne.setLibelleAction(libelleAction);
            ligne.setCible(cible);
            ligne.setDetails(details);
            ligne.setDateHeure(LocalDateTime.now());

            // Recuperation de l'utilisateur courant via le SecurityContext
            // (JwtFilter y place le login comme "principal")
            String login = loginCourant();
            if (login != null) {
                ligne.setAuteurLogin(login);
                utilisateurRepository.findByLogin(login).ifPresent(u -> {
                    ligne.setIdUtilisateur(u.getId());
                    ligne.setAuteur(nomComplet(u));
                });
            }

            journalRepository.save(ligne);
        } catch (Exception e) {
            // Le journal ne doit JAMAIS casser une action metier.
            // En cas de souci, on log juste cote serveur et on continue.
            System.err.println("[JournalService] echec enregistrement journal : " + e.getMessage());
        }
    }

    /**
     * Variante pratique quand on connait deja le login (ex. a la connexion,
     * avant que le SecurityContext ne soit forcement rempli).
     */
    public void enregistrerPour(String login, String action, String libelleAction,
                                String cible, String details) {
        try {
            JournalActivite ligne = new JournalActivite();
            ligne.setAction(action);
            ligne.setLibelleAction(libelleAction);
            ligne.setCible(cible);
            ligne.setDetails(details);
            ligne.setDateHeure(LocalDateTime.now());
            ligne.setAuteurLogin(login);

            if (login != null) {
                utilisateurRepository.findByLogin(login).ifPresent(u -> {
                    ligne.setIdUtilisateur(u.getId());
                    ligne.setAuteur(nomComplet(u));
                });
            }

            journalRepository.save(ligne);
        } catch (Exception e) {
            System.err.println("[JournalService] echec enregistrement journal : " + e.getMessage());
        }
    }

    // --- Helpers ---------------------------------------------------------

    private String loginCourant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String name = auth.getName();
        // "anonymousUser" quand pas de token (ex. endpoint public)
        if (name == null || "anonymousUser".equals(name)) {
            return null;
        }
        return name;
    }

    private String nomComplet(Utilisateur u) {
        if (u.getAgent() != null) {
            String prenom = u.getAgent().getPrenom() != null ? u.getAgent().getPrenom() : "";
            String nom = u.getAgent().getNom() != null ? u.getAgent().getNom() : "";
            String complet = (prenom + " " + nom).trim();
            if (!complet.isEmpty()) {
                return complet;
            }
        }
        return u.getLogin();
    }
}