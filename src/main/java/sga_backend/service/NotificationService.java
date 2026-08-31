package sga_backend.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sga_backend.entity.Notification;
import sga_backend.repository.NotificationRepository;

@Service
public class NotificationService {

    @Autowired private NotificationRepository notificationRepository;

    // Créer une notification (in-app)
    @Transactional
    public void creer(Long idDestinataire, String message, String type, Long idDemande) {
        Notification n = new Notification();
        n.setIdDestinataire(idDestinataire);
        n.setMessage(message);
        n.setType(type);
        n.setIdDemande(idDemande);
        n.setLu(false);
        notificationRepository.save(n);
    }

    public List<Notification> getPourUtilisateur(Long idUtilisateur) {
        return notificationRepository.findByIdDestinataireOrderByDateCreationDesc(idUtilisateur);
    }

    public long compterNonLues(Long idUtilisateur) {
        return notificationRepository.countByIdDestinataireAndLuFalse(idUtilisateur);
    }

    @Transactional
    public void marquerCommeLue(Long idNotification) {
        notificationRepository.findById(idNotification).ifPresent(n -> {
            n.setLu(true);
            notificationRepository.save(n);
        });
    }

    @Transactional
    public void marquerToutesLues(Long idUtilisateur) {
        for (Notification n : notificationRepository.findByIdDestinataireAndLuFalseOrderByDateCreationDesc(idUtilisateur)) {
            n.setLu(true);
            notificationRepository.save(n);
        }
    }
}