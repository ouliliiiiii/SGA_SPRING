package sga_backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sga_backend.dto.DirectionDTO;
import sga_backend.dto.DirectionRequest;
import sga_backend.entity.Direction;
import sga_backend.repository.DirectionRepository;

@Service
public class DirectionService {

    @Autowired private DirectionRepository directionRepository;

    public List<DirectionDTO> getActives() {
        List<DirectionDTO> res = new ArrayList<>();
        for (Direction d : directionRepository.findByActifTrueOrderByLibelleAsc()) {
            res.add(toDto(d));
        }
        return res;
    }

    public List<DirectionDTO> getArchivees() {
        List<DirectionDTO> res = new ArrayList<>();
        for (Direction d : directionRepository.findByActifFalseOrderByLibelleAsc()) {
            res.add(toDto(d));
        }
        return res;
    }

    @Transactional
    public DirectionDTO creer(DirectionRequest req) {
        if (req.getCode() == null || req.getCode().trim().isEmpty())
            throw new RuntimeException("Le code est obligatoire");
        if (req.getLibelle() == null || req.getLibelle().trim().isEmpty())
            throw new RuntimeException("Le libellé est obligatoire");
        if (directionRepository.existsByCode(req.getCode().trim()))
            throw new RuntimeException("Ce code de direction existe déjà");

        Direction d = new Direction();
        d.setCode(req.getCode().trim());
        d.setLibelle(req.getLibelle().trim());
        d.setActif(true);
        return toDto(directionRepository.save(d));
    }

    @Transactional
    public DirectionDTO modifier(Long id, DirectionRequest req) {
        Direction d = directionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Direction introuvable"));

        if (req.getLibelle() != null && !req.getLibelle().trim().isEmpty()) {
            d.setLibelle(req.getLibelle().trim());
        }
        if (req.getCode() != null && !req.getCode().trim().isEmpty()
                && !req.getCode().trim().equals(d.getCode())) {
            if (directionRepository.existsByCode(req.getCode().trim()))
                throw new RuntimeException("Ce code de direction existe déjà");
            d.setCode(req.getCode().trim());
        }
        return toDto(directionRepository.save(d));
    }

    @Transactional
    public void archiver(Long id) {
        Direction d = directionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Direction introuvable"));
        d.setActif(false);
        directionRepository.save(d);
    }

    @Transactional
    public void restaurer(Long id) {
        Direction d = directionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Direction introuvable"));
        d.setActif(true);
        directionRepository.save(d);
    }

    private DirectionDTO toDto(Direction d) {
        DirectionDTO dto = new DirectionDTO();
        dto.setId(d.getId());
        dto.setCode(d.getCode());
        dto.setLibelle(d.getLibelle());
        dto.setActif(d.getActif());
        return dto;
    }
}