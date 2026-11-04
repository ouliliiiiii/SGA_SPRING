package sga_backend.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

        // Trouver l'étape suivante EN_ATTENTE (s'il en existe une)
        List<EtapeValidation> toutes =
            etapeValidationRepository.findByDemandeIdOrderByOrdreAsc(demande.getId());
        EtapeValidation suivante = toutes.stream()
            .filter(e -> e.getOrdre() > etape.getOrdre() && "EN_ATTENTE".equals(e.getStatut()))
            .findFirst().orElse(null);

        // ⚠️ Si c'est la DERNIÈRE étape (pas de suivante) → NE PAS escalader.
        // L'étape terminale (ex : le RH) reste EN_ATTENTE jusqu'à validation humaine.
        // L'escalade automatique ne saute QUE les étapes intermédiaires.
        if (suivante == null) {
            return;
        }

        // 1. Marquer l'étape courante comme ESCALADEE (sautée automatiquement)
        etape.setStatut("ESCALADEE");
        etape.setDateAction(LocalDateTime.now());
        etape.setCommentaire("Étape escaladée automatiquement après " + DELAI_HEURES + "h sans traitement.");
        etapeValidationRepository.save(etape);

        // 2. Activer l'étape suivante
        suivante.setDateActivation(LocalDateTime.now());
        etapeValidationRepository.save(suivante);
        demande.setStatut("EN_COURS");
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
                    String contenu = """
                        <div style="font-size:16px;color:#B07D0A;font-weight:bold;margin-bottom:14px;">Votre demande a été escaladée</div>
                        <p style="font-size:14px;color:#444444;line-height:1.6;margin:0 0 16px;">
                          Bonjour,<br><br>
                          L'étape « <strong>%s</strong> » de votre demande de congé n'a pas été traitée dans le délai imparti.
                          Elle a donc été <strong>escaladée automatiquement</strong> à l'étape suivante du circuit.
                        </p>
                        <div style="background:#f4f8f6;border-left:3px solid #1B4D3E;border-radius:6px;padding:14px 16px;margin-bottom:16px;font-size:14px;color:#444444;">
                          %s
                        </div>
                        <p style="font-size:14px;color:#444444;line-height:1.6;margin:0;">
                          Vous serez informé(e) de la suite du traitement.
                        </p>
                        %s
                        """.formatted(
                            etapeEscaladee.getLibelle(),
                            detailsDemandeHtml(demande),
                            emailService.bouton("Voir ma demande", emailService.getAppUrl())
                        );
                    emailService.envoyerHtml(demande.getAgent().getEmail(),
                        "Votre demande a été escaladée", contenu);
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

                        String contenu = """
                        <div style="font-size:20px;color:#1a1a1a;font-weight:600;margin-bottom:12px;">Votre demande a été escaladée</div>
                        <p style="font-size:14px;color:#666;line-height:1.7;margin:0 0 20px;">
                          Bonjour,<br><br>
                          L'étape « <strong style="color:#1a1a1a;">%s</strong> » de votre demande de congé n'a pas été traitée dans le délai imparti.
                          Elle a donc été <strong style="color:#1a1a1a;">escaladée automatiquement</strong> à l'étape suivante du circuit.
                        </p>
                        <div style="background:#F4F8F6;border-radius:8px;padding:18px 20px;margin-bottom:20px;">
                          %s
                        </div>
                        <p style="font-size:14px;color:#666;line-height:1.7;margin:0 0 24px;">
                          Vous serez informé(e) de la suite du traitement.
                        </p>
                        %s
                        """.formatted(
                            etapeEscaladee.getLibelle(),
                            detailsDemandeHtml(demande),
                            emailService.bouton("Voir ma demande", emailService.getAppUrl())
                        );
                emailService.envoyerHtml(validateurSuivant.getAgent().getEmail(),
                    "Une demande escaladée attend votre validation", contenu);
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
}