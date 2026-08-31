package sga_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sga_backend.entity.DemandeConge;

public interface DemandeCongeRepository extends JpaRepository<DemandeConge, Long> {

    // Toutes les demandes d'un agent (les plus récentes d'abord)
    List<DemandeConge> findByAgentIdOrderByDateCreationDesc(Long idAgent);

    // Demandes par statut
    List<DemandeConge> findByStatut(String statut);

      // Toutes les demandes triées (les plus récentes d'abord) — page Historique (admin)
    List<DemandeConge> findAllByOrderByDateCreationDesc();

    
}