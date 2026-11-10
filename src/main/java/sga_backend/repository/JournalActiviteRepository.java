package sga_backend.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sga_backend.entity.JournalActivite;

public interface JournalActiviteRepository extends JpaRepository<JournalActivite, Long> {

    // Tout le journal, plus recent en premier
    List<JournalActivite> findAllByOrderByDateHeureDesc();

    // Filtrage souple (chaque critere est optionnel : null = ignore)
       // Filtrage souple (chaque critere est optionnel : null = ignore)
    @Query("""
        SELECT j FROM JournalActivite j
        WHERE (:idUtilisateur IS NULL OR j.idUtilisateur = :idUtilisateur)
          AND (:action IS NULL OR j.action = :action)
          AND (CAST(:debut AS timestamp) IS NULL OR j.dateHeure >= :debut)
          AND (CAST(:fin AS timestamp) IS NULL OR j.dateHeure <= :fin)
        ORDER BY j.dateHeure DESC
        """)
    List<JournalActivite> filtrer(@Param("idUtilisateur") Long idUtilisateur,
                                  @Param("action") String action,
                                  @Param("debut") LocalDateTime debut,
                                  @Param("fin") LocalDateTime fin);

    // Compteur d'activites depuis une date (pour les stats)
    long countByDateHeureAfter(LocalDateTime date);

    // Compteur par type d'action depuis une date (pour les stats)
    long countByActionAndDateHeureAfter(String action, LocalDateTime date);
}