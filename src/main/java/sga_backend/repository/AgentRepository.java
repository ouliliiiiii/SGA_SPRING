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
     boolean existsByEmail(String email);
    // Récupère tous les agents ayant un statut précis
    // Utilisé pour la liste déroulante "Supérieur hiérarchique"
    // (on affiche uniquement les agents ACTIF)
    List<Agent> findByStatut(StatutAgent statut);
    @Query("SELECT a FROM Agent a LEFT JOIN FETCH a.enfants WHERE a.id = :id")
    Optional<Agent> findByIdWithEnfants(Long id);

    // Compte les chefs de division (profil CHEF_DIVISION) dans une division donnée,
    // en excluant éventuellement un agent (pour la modification)
    @Query("SELECT COUNT(a) FROM Agent a " +
           "JOIN Utilisateur u ON u.agent = a " +
           "WHERE u.profil.code = :codeProfil " +
           "AND a.unite.id = :idUnite " +
           "AND (:idExclu IS NULL OR a.id <> :idExclu)")
    long compterParProfilEtUnite(String codeProfil, Long idUnite, Long idExclu);

    // Compte les responsables (profil DIRECTEUR) dans une direction/cellule donnée
    @Query("SELECT COUNT(a) FROM Agent a " +
           "JOIN Utilisateur u ON u.agent = a " +
           "WHERE u.profil.code = :codeProfil " +
           "AND a.direction.id = :idDirection " +
           "AND (:idExclu IS NULL OR a.id <> :idExclu)")
    long compterParProfilEtDirection(String codeProfil, Long idDirection, Long idExclu);
        @Query("SELECT COUNT(a) FROM Agent a " +
           "JOIN Utilisateur u ON u.agent = a " +
           "WHERE u.profil.code = :codeProfil " +
           "AND (:idExclu IS NULL OR a.id <> :idExclu)")
    long compterParProfil(String codeProfil, Long idExclu);
}