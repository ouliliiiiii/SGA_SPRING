package sga_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sga_backend.entity.TypeConge;

public interface TypeCongeRepository extends JpaRepository<TypeConge, Long> {

    // Types actifs uniquement (pour le formulaire de demande)
    List<TypeConge> findByActifTrue();
    
}