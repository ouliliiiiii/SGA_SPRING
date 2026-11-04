package sga_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sga_backend.entity.Regularisation;
import java.util.List;

public interface RegularisationRepository extends JpaRepository<Regularisation, Long> {
    List<Regularisation> findByAgentIdOrderByDateOperationDesc(Long idAgent);


    List<Regularisation> findAllByOrderByDateOperationDesc();
}

