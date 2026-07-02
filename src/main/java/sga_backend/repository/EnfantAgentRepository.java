package sga_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sga_backend.entity.EnfantAgent;

// Repository pour la table 'enfant_agent'
public interface EnfantAgentRepository extends JpaRepository<EnfantAgent, Long> {

    // Récupère tous les enfants d'un agent précis
    // Utilisé pour afficher la liste dans la modale
    List<EnfantAgent> findByAgentId(Long idAgent);

}