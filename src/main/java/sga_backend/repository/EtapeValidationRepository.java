package sga_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sga_backend.entity.EtapeValidation;

public interface EtapeValidationRepository extends JpaRepository<EtapeValidation, Long> {

    // Toutes les étapes d'une demande, dans l'ordre
    List<EtapeValidation> findByDemandeIdOrderByOrdreAsc(Long idDemande);

    // Les étapes en attente pour un profil donné
    List<EtapeValidation> findByProfilAttenduAndStatut(String profilAttendu, String statut);

    // Les étapes en attente pour plusieurs profils (ex. ADMIN voit ADMIN + DAF)
    List<EtapeValidation> findByProfilAttenduInAndStatut(List<String> profils, String statut);

        List<EtapeValidation> findByStatut(String statut);
}