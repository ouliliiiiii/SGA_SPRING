package sga_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import sga_backend.entity.Agent;
import sga_backend.entity.StatutAgent;

// Repository pour la table 'agent'
// Donne accès à toutes les opérations CRUD sur les agents
public interface AgentRepository extends JpaRepository<Agent, Long> {

    // Vérifie si un matricule existe déjà en base
    // Utilisé dans EmployeService pour éviter les doublons
    boolean existsByMatricule(String matricule);

    // Récupère tous les agents ayant un statut précis
    // Utilisé pour la liste déroulante "Supérieur hiérarchique"
    // (on affiche uniquement les agents ACTIF)
    List<Agent> findByStatut(StatutAgent statut);
    @Query("SELECT a FROM Agent a LEFT JOIN FETCH a.enfants WHERE a.id = :id")
    Optional<Agent> findByIdWithEnfants(Long id);
}