package sga_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sga_backend.entity.UniteOrganisationnelle;

public interface UniteRepository extends JpaRepository<UniteOrganisationnelle, Long> {

    List<UniteOrganisationnelle> findByActifTrueOrderByLibelleAsc();

    List<UniteOrganisationnelle> findByActifFalseOrderByLibelleAsc();

    // Par type (DIVISION ou SERVICE_REGIONAL), actives
    List<UniteOrganisationnelle> findByTypeAndActifTrueOrderByLibelleAsc(String type);

    boolean existsByCode(String code);
}