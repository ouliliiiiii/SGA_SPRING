// FonctionRepository.java
package sga_backend.repository;
import org.springframework.data.jpa.repository.JpaRepository;

import sga_backend.entity.Fonction;
public interface FonctionRepository extends JpaRepository<Fonction, Long> {}