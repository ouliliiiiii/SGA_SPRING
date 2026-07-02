package sga_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sga_backend.entity.SoldeConges;

public interface SoldeCongesRepository extends JpaRepository<SoldeConges, Long> {

    // Récupère le solde d'un agent par son id
    Optional<SoldeConges> findByAgentId(Long idAgent);
}