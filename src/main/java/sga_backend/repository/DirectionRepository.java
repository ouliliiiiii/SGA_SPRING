package sga_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sga_backend.entity.Direction;

public interface DirectionRepository extends JpaRepository<Direction, Long> {

    // Directions actives (triées par libellé)
    List<Direction> findByActifTrueOrderByLibelleAsc();

    // Directions archivées
    List<Direction> findByActifFalseOrderByLibelleAsc();

    // Vérifier l'unicité du code
    boolean existsByCode(String code);
}