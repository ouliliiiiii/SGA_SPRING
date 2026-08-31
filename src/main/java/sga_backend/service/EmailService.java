package sga_backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired private JavaMailSender mailSender;

    // Envoi d'un email simple (texte). Ne bloque pas le circuit si l'envoi échoue.
    public void envoyer(String destinataire, String sujet, String corps) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(destinataire);
            message.setSubject(sujet);
            message.setText(corps);
            message.setFrom("oulygaye19@gmail.com");
            mailSender.send(message);
        } catch (Exception e) {
            // On log sans planter : un email raté ne doit pas casser la validation
            System.err.println("Échec envoi email à " + destinataire + " : " + e.getMessage());
        }
    }
}