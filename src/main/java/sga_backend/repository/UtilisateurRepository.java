package sga_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import sga_backend.entity.Utilisateur;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

     @EntityGraph(attributePaths = "profil")
    Optional<Utilisateur> findByLogin(String login);

    boolean existsByLogin(String login);

// Ajouter cette méthode
Optional<Utilisateur> findByAgentId(Long idAgent); 
}