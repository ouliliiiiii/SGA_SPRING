package sga_backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sga_backend.entity.DemandeConge;
import sga_backend.entity.EtapeValidation;
import sga_backend.entity.Utilisateur;
import sga_backend.repository.DemandeCongeRepository;
import sga_backend.repository.EtapeValidationRepository;
import sga_backend.repository.UtilisateurRepository;

@Service
public class EscaladeService {

    @Autowired private EtapeValidationRepository etapeValidationRepository;
    @Autowired private DemandeCongeRepository demandeCongeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private EmailService emailService;

    // Délai avant escalade. Pour TESTER : mets 2 minutes. En PRODUCTION : 24 heures.
    private static final long DELAI_HEURES = 24;   // ← passe à 24 pour la prod
    // Pour tester rapidement, tu peux utiliser des minutes à la place (voir plus bas)

    // Le job tourne toutes les 2 minutes (120000 ms)
    @Scheduled(fixedRate = 120000)
    @Transactional
    public void verifierEtapesEnRetard() {
        // Toutes les étapes actuellement EN_ATTENTE
        List<EtapeValidation> etapesEnAttente =
            etapeValidationRepository.findByStatut("EN_ATTENTE");

        LocalDateTime maintenant = LocalDateTime.now();

        for (EtapeValidation etape : etapesEnAttente) {
            if (etape.getDateActivation() == null) continue;

            // Depuis combien de temps l'étape attend-elle ?
                       // POUR TESTER : escalade après 2 minutes
            LocalDateTime limite = etape.getDateActivation().plusHours(DELAI_HEURES);
            //LocalDateTime limite = etape.getDateActivation().plusMinutes(2);

            // Si la limite est dépassée → escalade
            if (maintenant.isAfter(limite)) {
                escalader(etape);
            }
        }
    }

    private void escalader(EtapeValidation etape) {
        DemandeConge demande = etape.getDemande();

        // 1. Marquer l'étape comme ESCALADEE (sautée automatiquement)
        etape.setStatut("ESCALADEE");
        etape.setDateAction(LocalDateTime.now());
        etape.setCommentaire("Étape escaladée automatiquement après " + DELAI_HEURES + "h sans traitement.");
        etapeValidationRepository.save(etape);

        // 2. Trouver l'étape suivante et l'activer
        List<EtapeValidation> toutes =
            etapeValidationRepository.findByDemandeIdOrderByOrdreAsc(demande.getId());
        EtapeValidation suivante = toutes.stream()
            .filter(e -> e.getOrdre() > etape.getOrdre() && "EN_ATTENTE".equals(e.getStatut()))
            .findFirst().orElse(null);

        if (suivante != null) {
            // Il reste des étapes : activer la suivante
            suivante.setDateActivation(LocalDateTime.now());
            etapeValidationRepository.save(suivante);
            demande.setStatut("EN_COURS");
        } else {
            // C'était la dernière étape : la demande est validée
            demande.setStatut("VALIDEE");
        }
        demandeCongeRepository.save(demande);

        // 3. Notifier tout le monde concerné
        notifierEscalade(demande, etape, suivante);
    }

    private void notifierEscalade(DemandeConge demande, EtapeValidation etapeEscaladee, EtapeValidation suivante) {
        // Notifier le demandeur
        if (demande.getAgent() != null) {
            Utilisateur userDemandeur = utilisateurRepository.findByAgentId(demande.getAgent().getId()).orElse(null);
            if (userDemandeur != null) {
                String msg = "L'étape « " + etapeEscaladee.getLibelle()
                    + " » de votre demande a été escaladée automatiquement (délai dépassé).";
                notificationService.creer(userDemandeur.getId(), msg, "ESCALADE", demande.getId());
                if (demande.getAgent().getEmail() != null) {
                    emailService.envoyer(demande.getAgent().getEmail(),
                        "Votre demande a été escaladée",
                        "Bonjour,\n\nL'étape « " + etapeEscaladee.getLibelle()
                        + " » de votre demande de congé n'a pas été traitée dans le délai imparti. "
                        + "Elle a été escaladée automatiquement à l'étape suivante.\n\nSGA - SEN-CSU");
                }
            }
        }

        // Notifier le validateur suivant (si une étape suivante existe)
        if (suivante != null) {
            Utilisateur validateurSuivant = trouverValidateurDeLEtape(suivante);
            if (validateurSuivant != null && validateurSuivant.getAgent() != null
                && validateurSuivant.getAgent().getEmail() != null) {
                notificationService.creer(validateurSuivant.getId(),
                    "Une demande escaladée attend votre validation.", "DEMANDE_A_VALIDER", demande.getId());
                emailService.envoyer(validateurSuivant.getAgent().getEmail(),
                    "Une demande escaladée attend votre validation",
                    "Bonjour,\n\nUne demande de congé a été escaladée et attend votre validation.\n\nSGA - SEN-CSU");
            }
        }
    }

    // Retrouve l'utilisateur qui doit traiter une étape (selon profil + ciblage)
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
}