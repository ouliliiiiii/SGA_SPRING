package sga_backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import sga_backend.dto.JourFerieDTO;
import sga_backend.entity.JourFerie;
import sga_backend.repository.JourFerieRepository;

@Service
public class JourFerieService {

    @Autowired private JourFerieRepository jourFerieRepository;

    // ============================================
    // PRÉ-REMPLISSAGE des fériés FIXES sénégalais
    // S'exécute au démarrage ; n'insère que ce qui manque (idempotent)
    // ============================================
    @PostConstruct
    public void initialiserFeriesFixes() {
        creerSiAbsent("Jour de l'An", 1, 1);
        creerSiAbsent("Fête de l'Indépendance", 4, 4);
        creerSiAbsent("Fête du Travail", 1, 5);
        creerSiAbsent("Assomption", 15, 8);
        creerSiAbsent("Toussaint", 1, 11);
        creerSiAbsent("Noël", 25, 12);
    }

    private void creerSiAbsent(String nom, int jour, int mois) {
        if (!jourFerieRepository.existsByNomAndType(nom, "FIXE")) {
            JourFerie jf = new JourFerie();
            jf.setNom(nom);
            jf.setType("FIXE");
            jf.setJour(jour);
            jf.setMois(mois);
            jourFerieRepository.save(jf);
        }
    }

    // ============================================
    // CRUD
    // ============================================
    public List<JourFerieDTO> getTous() {
        List<JourFerieDTO> liste = new ArrayList<>();
        for (JourFerie jf : jourFerieRepository.findAll()) {
            liste.add(toDto(jf));
        }
        return liste;
    }

    public List<JourFerieDTO> getParType(String type) {
        List<JourFerieDTO> liste = new ArrayList<>();
        for (JourFerie jf : jourFerieRepository.findByType(type)) {
            liste.add(toDto(jf));
        }
        return liste;
    }

    public JourFerieDTO ajouter(JourFerieDTO dto) {
        JourFerie jf = new JourFerie();
        jf.setNom(dto.getNom());
        jf.setType(dto.getType());
        jf.setJour(dto.getJour());
        jf.setMois(dto.getMois());
        jf.setDate(dto.getDate());
        return toDto(jourFerieRepository.save(jf));
    }

    public void supprimer(Long id) {
        jourFerieRepository.deleteById(id);
    }

    // ============================================
    // UTILITAIRE : une date est-elle fériée ?
    // (utilisé par le calcul des congés)
    // ============================================
    public boolean estFerie(LocalDate date) {
        for (JourFerie jf : jourFerieRepository.findAll()) {
            if ("FIXE".equalsIgnoreCase(jf.getType())) {
                if (jf.getJour() != null && jf.getMois() != null
                    && date.getDayOfMonth() == jf.getJour()
                    && date.getMonthValue() == jf.getMois()) {
                    return true;
                }
            } else if ("MOBILE".equalsIgnoreCase(jf.getType())) {
                if (jf.getDate() != null && jf.getDate().isEqual(date)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Renvoie la liste des dates fériées (utile pour passer au calcul en une fois)
    public List<JourFerie> tousLesFeries() {
        return jourFerieRepository.findAll();
    }

    private JourFerieDTO toDto(JourFerie jf) {
        JourFerieDTO dto = new JourFerieDTO();
        dto.setId(jf.getId());
        dto.setNom(jf.getNom());
        dto.setType(jf.getType());
        dto.setJour(jf.getJour());
        dto.setMois(jf.getMois());
        dto.setDate(jf.getDate());
        return dto;
    }
}