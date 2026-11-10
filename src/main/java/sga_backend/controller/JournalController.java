package sga_backend.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import sga_backend.dto.JournalStatsDTO;
import sga_backend.entity.JournalActivite;
import sga_backend.repository.JournalActiviteRepository;

@RestController
@RequestMapping("/api/journal")
@PreAuthorize("hasRole('SUPERADMIN')")
public class JournalController {

    private final JournalActiviteRepository journalRepository;

    public JournalController(JournalActiviteRepository journalRepository) {
        this.journalRepository = journalRepository;
    }

    /**
     * Liste du journal, avec filtres optionnels (tous facultatifs).
     * - idUtilisateur : filtrer par auteur
     * - action        : filtrer par type d'action (CONNEXION, VALIDATION_DEMANDE, ...)
     * - debut / fin   : bornes de dates (format yyyy-MM-dd)
     */
    @GetMapping
    public List<JournalActivite> lister(
            @RequestParam(required = false) Long idUtilisateur,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String debut,
            @RequestParam(required = false) String fin) {

        LocalDateTime dDebut = (debut != null && !debut.isBlank())
            ? LocalDate.parse(debut).atStartOfDay() : null;
        LocalDateTime dFin = (fin != null && !fin.isBlank())
            ? LocalDate.parse(fin).atTime(LocalTime.MAX) : null;

        String act = (action != null && action.isBlank()) ? null : action;

        return journalRepository.filtrer(idUtilisateur, act, dDebut, dFin);
    }

    /** Compteurs pour les cartes du dashboard. */
    @GetMapping("/stats")
    public JournalStatsDTO stats() {
        LocalDateTime debutJour = LocalDate.now().atStartOfDay();
        LocalDateTime debutSemaine = LocalDate.now().minusDays(7).atStartOfDay();

        JournalStatsDTO dto = new JournalStatsDTO();
        dto.setConnexionsAujourdhui(
            journalRepository.countByActionAndDateHeureAfter("CONNEXION", debutJour));
        dto.setActionsAujourdhui(
            journalRepository.countByDateHeureAfter(debutJour));
        dto.setActionsSemaine(
            journalRepository.countByDateHeureAfter(debutSemaine));
        dto.setTotal(journalRepository.count());
        return dto;
    }

    /** Liste des types d'actions présents (pour alimenter le filtre côté front). */
    @GetMapping("/actions")
    public List<String> actionsDistinctes() {
        return journalRepository.findAllByOrderByDateHeureDesc().stream()
            .map(JournalActivite::getAction)
            .distinct()
            .sorted()
            .toList();
    }
}