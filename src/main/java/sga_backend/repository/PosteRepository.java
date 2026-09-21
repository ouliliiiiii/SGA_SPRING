package sga_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sga_backend.entity.Poste;

public interface PosteRepository extends JpaRepository<Poste, Long> {
    List<Poste> findByUniteId(Long idUnite);
        List<Poste> findByDirectionId(Long idDirection);
}