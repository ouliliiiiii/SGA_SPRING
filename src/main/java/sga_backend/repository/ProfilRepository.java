package sga_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sga_backend.entity.Profil;

public interface ProfilRepository extends JpaRepository<Profil, Long> {
}