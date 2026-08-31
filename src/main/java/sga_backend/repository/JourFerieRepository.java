package sga_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sga_backend.entity.JourFerie;

public interface JourFerieRepository extends JpaRepository<JourFerie, Long> {

    // Tous les jours fériés actifs
    List<JourFerie> findByActifTrue();

    // Par type (FIXE / MOBILE), actifs seulement
    List<JourFerie> findByTypeAndActifTrue(String type);

    // Archivés
    List<JourFerie> findByActifFalse();
}