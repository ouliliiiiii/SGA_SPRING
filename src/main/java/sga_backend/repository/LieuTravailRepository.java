package sga_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sga_backend.entity.LieuTravail;

// Repository pour la table 'lieu_travail' (anciennement 'entite')
// Donne accès à toutes les opérations CRUD sur les lieux de travail
public interface LieuTravailRepository extends JpaRepository<LieuTravail, Long> {

    // Récupère uniquement les lieux de travail actifs
    // Utilisé pour la liste déroulante dans le formulaire d'ajout d'employé
    List<LieuTravail> findByActifTrue();

}