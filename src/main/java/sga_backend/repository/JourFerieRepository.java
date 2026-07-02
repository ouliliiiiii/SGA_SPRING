package sga_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sga_backend.entity.JourFerie;

public interface JourFerieRepository extends JpaRepository<JourFerie, Long> {

    List<JourFerie> findByType(String type);

    boolean existsByNomAndType(String nom, String type);
}