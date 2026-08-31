package sga_backend.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sga_backend.entity.Notification;
import sga_backend.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired private NotificationService notificationService;

    @GetMapping("/{idUtilisateur}")
    public List<Notification> getNotifications(@PathVariable Long idUtilisateur) {
        return notificationService.getPourUtilisateur(idUtilisateur);
    }

    @GetMapping("/{idUtilisateur}/non-lues/count")
    public long compterNonLues(@PathVariable Long idUtilisateur) {
        return notificationService.compterNonLues(idUtilisateur);
    }

    @PutMapping("/{id}/lue")
    public ResponseEntity<?> marquerLue(@PathVariable Long id) {
        notificationService.marquerCommeLue(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{idUtilisateur}/toutes-lues")
    public ResponseEntity<?> marquerToutesLues(@PathVariable Long idUtilisateur) {
        notificationService.marquerToutesLues(idUtilisateur);
        return ResponseEntity.ok().build();
    }
}