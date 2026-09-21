package sga_backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    @Autowired private JavaMailSender mailSender;
    @Value("${sga.backend.url}")
    private String backendUrl;

    @Value("${sga.app.url}")
    private String appUrl;

    @Async
    // Envoi texte simple (gardé pour compatibilité)
    public void envoyer(String destinataire, String sujet, String corps) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(destinataire);
            message.setSubject(sujet);
            message.setText(corps);
            message.setFrom("oulygaye19@gmail.com");
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Échec envoi email à " + destinataire + " : " + e.getMessage());
        }
    }

    @Async
    // Envoi HTML (nouveau) : enveloppe le contenu dans le template SGA
    public void envoyerHtml(String destinataire, String sujet, String contenuHtml) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(destinataire);
            helper.setSubject(sujet);
            helper.setFrom("oulygaye19@gmail.com");
            helper.setText(construireTemplate(contenuHtml), true);  // true = HTML
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Échec envoi email HTML à " + destinataire + " : " + e.getMessage());
        }
    }

    // Template commun : enveloppe le contenu + ajoute la signature RH
        private String construireTemplate(String contenu) {
        return """
            <div style="max-width:500px;margin:0 auto;background:#ffffff;border:1px solid #eeeeee;font-family:Arial,Helvetica,sans-serif;">
              <div style="height:4px;background:#1B4D3E;"></div>
              <div style="padding:40px 40px 32px;">

                <div style="margin-bottom:28px;">
                  <span style="font-size:13px;font-weight:bold;color:#1B4D3E;letter-spacing:1px;">SEN-CSU</span>
                  <span style="font-size:13px;color:#cccccc;margin:0 8px;">|</span>
                  <span style="font-size:12px;color:#999999;">Gestion des absences</span>
                </div>

                %s

                <div style="margin-top:36px;padding-top:20px;border-top:1px solid #eeeeee;">
                  <div style="font-size:13px;color:#1a1a1a;font-weight:bold;">Mme BARRO, Adélaïde DARY</div>
                  <div style="font-size:12px;color:#999999;margin-top:3px;line-height:1.5;">
                    Chef de Division des Ressources Humaines<br>
                    SEN-CSU &middot; 33 859 10 09
                  </div>
                </div>

              </div>
            </div>
            """.formatted(contenu);
    }

    // Bouton réutilisable (lien vers l'application)
    public String bouton(String texte, String lien) {
        return """
            <div style="margin:8px 0;">
              <a href="%s" style="display:inline-block;background:#1B4D3E;color:#ffffff;font-size:14px;font-weight:500;padding:13px 30px;border-radius:4px;text-decoration:none;">%s &rarr;</a>
            </div>
            """.formatted(lien, texte);
    }

    public String getAppUrl() {
        return appUrl;
    }
}