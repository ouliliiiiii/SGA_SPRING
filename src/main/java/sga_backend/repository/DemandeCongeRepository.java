package sga_backend.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sga_backend.entity.DemandeConge;

public interface DemandeCongeRepository extends JpaRepository<DemandeConge, Long> {

    // Toutes les demandes d'un agent (les plus récentes d'abord)
    List<DemandeConge> findByAgentIdOrderByDateCreationDesc(Long idAgent);

    // Demandes par statut
    List<DemandeConge> findByStatut(String statut);

      // Toutes les demandes triées (les plus récentes d'abord) — page Historique (admin)
    List<DemandeConge> findAllByOrderByDateCreationDesc();

    List<DemandeConge> findByAgentIdAndStatut(Long idAgent, String statut);

    @Query("SELECT d FROM DemandeConge d WHERE d.agent.id = :idAgent " +
       "AND d.statut IN ('EN_ATTENTE', 'EN_COURS', 'VALIDEE') " +
       "AND d.dateDebut <= :dateFin AND d.dateFin >= :dateDebut")
List<DemandeConge> trouverChevauchements(@Param("idAgent") Long idAgent,
                                         @Param("dateDebut") LocalDate dateDebut,
                                         @Param("dateFin") LocalDate dateFin);
}