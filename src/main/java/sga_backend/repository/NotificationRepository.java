package sga_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sga_backend.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Toutes les notifications d'un utilisateur, les plus récentes d'abord
    List<Notification> findByIdDestinataireOrderByDateCreationDesc(Long idDestinataire);

    // Les non lues (pour le compteur)
    List<Notification> findByIdDestinataireAndLuFalseOrderByDateCreationDesc(Long idDestinataire);

    long countByIdDestinataireAndLuFalse(Long idDestinataire);
}