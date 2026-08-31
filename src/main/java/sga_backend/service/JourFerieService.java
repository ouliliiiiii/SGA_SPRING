package sga_backend.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sga_backend.entity.JourFerie;
import sga_backend.repository.JourFerieRepository;

@Service
public class JourFerieService {

    @Autowired private JourFerieRepository jourFerieRepository;

    // Liste des jours fériés actifs
    public List<JourFerie> getTousActifs() {
        return jourFerieRepository.findByActifTrue();
    }

    // Liste des archivés
    public List<JourFerie> getArchives() {
        return jourFerieRepository.findByActifFalse();
    }

    @Transactional
    public JourFerie creer(JourFerie jf) {
        jf.setActif(true);
        valider(jf);
        return jourFerieRepository.save(jf);
    }

    @Transactional
    public JourFerie modifier(Long id, JourFerie data) {
        JourFerie jf = jourFerieRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Jour férié introuvable"));
        jf.setNom(data.getNom());
        jf.setType(data.getType());
        jf.setJour(data.getJour());
        jf.setMois(data.getMois());
        jf.setDate(data.getDate());
        valider(jf);
        return jourFerieRepository.save(jf);
    }

    @Transactional
    public void archiver(Long id) {
        JourFerie jf = jourFerieRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Jour férié introuvable"));
        jf.setActif(false);
        jourFerieRepository.save(jf);
    }

    @Transactional
    public void restaurer(Long id) {
        JourFerie jf = jourFerieRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Jour férié introuvable"));
        jf.setActif(true);
        jourFerieRepository.save(jf);
    }

    // Validation cohérence FIXE / MOBILE
    private void valider(JourFerie jf) {
        if (jf.getNom() == null || jf.getNom().isBlank()) {
            throw new RuntimeException("Le nom est obligatoire");
        }
        if ("FIXE".equalsIgnoreCase(jf.getType())) {
            if (jf.getJour() == null || jf.getMois() == null) {
                throw new RuntimeException("Un jour fixe nécessite un jour et un mois");
            }
            if (jf.getMois() < 1 || jf.getMois() > 12 || jf.getJour() < 1 || jf.getJour() > 31) {
                throw new RuntimeException("Jour ou mois invalide");
            }
            jf.setDate(null); // on n'utilise pas la date pour un fixe
        } else if ("MOBILE".equalsIgnoreCase(jf.getType())) {
            if (jf.getDate() == null) {
                throw new RuntimeException("Un jour mobile nécessite une date complète");
            }
            jf.setJour(null);
            jf.setMois(null);
        } else {
            throw new RuntimeException("Type invalide (attendu : FIXE ou MOBILE)");
        }
    }

    // ============================================================
    // Utilisé par le calcul des congés : ce jour est-il férié ?
    // ============================================================
    public boolean estFerie(LocalDate jour) {
        for (JourFerie jf : jourFerieRepository.findByActifTrue()) {
            if ("FIXE".equalsIgnoreCase(jf.getType())) {
                if (jf.getJour() != null && jf.getMois() != null
                    && jour.getDayOfMonth() == jf.getJour()
                    && jour.getMonthValue() == jf.getMois()) {
                    return true;
                }
            } else if ("MOBILE".equalsIgnoreCase(jf.getType())) {
                if (jf.getDate() != null && jour.isEqual(jf.getDate())) {
                    return true;
                }
            }
        }
        return false;
    }
}