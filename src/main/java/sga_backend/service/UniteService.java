package sga_backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sga_backend.dto.UniteDTO;
import sga_backend.dto.UniteRequest;
import sga_backend.entity.Direction;
import sga_backend.entity.LieuTravail;
import sga_backend.entity.UniteOrganisationnelle;
import sga_backend.repository.DirectionRepository;
import sga_backend.repository.LieuTravailRepository;
import sga_backend.repository.UniteRepository;

@Service
public class UniteService {

    @Autowired private UniteRepository uniteRepository;
    @Autowired private DirectionRepository directionRepository;
    @Autowired private LieuTravailRepository lieuTravailRepository;

    public List<UniteDTO> getActives() {
        return toDtoList(uniteRepository.findByActifTrueOrderByLibelleAsc());
    }

    public List<UniteDTO> getArchivees() {
        return toDtoList(uniteRepository.findByActifFalseOrderByLibelleAsc());
    }

    // Filtrer par type (DIVISION ou SERVICE_REGIONAL)
    public List<UniteDTO> getParType(String type) {
        return toDtoList(uniteRepository.findByTypeAndActifTrueOrderByLibelleAsc(type));
    }

    @Transactional
    public UniteDTO creer(UniteRequest req) {
        validerCommun(req);
        if (uniteRepository.existsByCode(req.getCode().trim())) {
            throw new RuntimeException("Ce code existe déjà");
        }

        UniteOrganisationnelle u = new UniteOrganisationnelle();
        u.setCode(req.getCode().trim());
        u.setLibelle(req.getLibelle().trim());
        u.setType(req.getType());
        u.setActif(true);
        appliquerRattachement(u, req);

        return toDto(uniteRepository.save(u));
    }

    @Transactional
    public UniteDTO modifier(Long id, UniteRequest req) {
        UniteOrganisationnelle u = uniteRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Unité introuvable"));

        if (req.getLibelle() != null && !req.getLibelle().trim().isEmpty()) {
            u.setLibelle(req.getLibelle().trim());
        }
        if (req.getCode() != null && !req.getCode().trim().isEmpty()
                && !req.getCode().trim().equals(u.getCode())) {
            if (uniteRepository.existsByCode(req.getCode().trim())) {
                throw new RuntimeException("Ce code existe déjà");
            }
            u.setCode(req.getCode().trim());
        }
        if (req.getType() != null && !req.getType().isEmpty()) {
            u.setType(req.getType());
        }
        appliquerRattachement(u, req);

        return toDto(uniteRepository.save(u));
    }

    @Transactional
    public void archiver(Long id) {
        UniteOrganisationnelle u = uniteRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Unité introuvable"));
        u.setActif(false);
        uniteRepository.save(u);
    }

    @Transactional
    public void restaurer(Long id) {
        UniteOrganisationnelle u = uniteRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Unité introuvable"));
        u.setActif(true);
        uniteRepository.save(u);
    }

    // --- Helpers ---

    private void validerCommun(UniteRequest req) {
        if (req.getCode() == null || req.getCode().trim().isEmpty())
            throw new RuntimeException("Le code est obligatoire");
        if (req.getLibelle() == null || req.getLibelle().trim().isEmpty())
            throw new RuntimeException("Le libellé est obligatoire");
        if (req.getType() == null || req.getType().isEmpty())
            throw new RuntimeException("Le type est obligatoire");
        if (!"DIVISION".equals(req.getType()) && !"SERVICE_REGIONAL".equals(req.getType()))
            throw new RuntimeException("Type invalide (DIVISION ou SERVICE_REGIONAL)");
    }

    // Applique le bon rattachement selon le type
    private void appliquerRattachement(UniteOrganisationnelle u, UniteRequest req) {
        if ("DIVISION".equals(u.getType())) {
            if (req.getIdDirection() == null)
                throw new RuntimeException("Une division doit être rattachée à une direction");
            Direction d = directionRepository.findById(req.getIdDirection())
                .orElseThrow(() -> new RuntimeException("Direction introuvable"));
            u.setDirection(d);
            u.setLieuTravail(null);
        } else if ("SERVICE_REGIONAL".equals(u.getType())) {
            if (req.getIdLieuTravail() == null)
                throw new RuntimeException("Un service régional doit être rattaché à une agence (lieu)");
            LieuTravail lt = lieuTravailRepository.findById(req.getIdLieuTravail())
                .orElseThrow(() -> new RuntimeException("Lieu de travail introuvable"));
            u.setLieuTravail(lt);
            u.setDirection(null);
        }
    }

    private List<UniteDTO> toDtoList(List<UniteOrganisationnelle> liste) {
        List<UniteDTO> res = new ArrayList<>();
        for (UniteOrganisationnelle u : liste) res.add(toDto(u));
        return res;
    }

    private UniteDTO toDto(UniteOrganisationnelle u) {
        UniteDTO dto = new UniteDTO();
        dto.setId(u.getId());
        dto.setCode(u.getCode());
        dto.setLibelle(u.getLibelle());
        dto.setType(u.getType());
        dto.setActif(u.getActif());
        if (u.getDirection() != null) {
            dto.setIdDirection(u.getDirection().getId());
            dto.setLibelleDirection(u.getDirection().getLibelle());
        }
        if (u.getLieuTravail() != null) {
            dto.setIdLieuTravail(u.getLieuTravail().getId());
            dto.setLibelleLieuTravail(u.getLieuTravail().getLibelle());
        }
        return dto;
    }
}