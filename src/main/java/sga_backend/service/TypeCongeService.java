package sga_backend.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import sga_backend.entity.TypeConge;
import sga_backend.repository.TypeCongeRepository;

@Service
public class TypeCongeService {

    @Autowired private TypeCongeRepository typeCongeRepository;

    // ============================================
    // PRÉ-REMPLISSAGE des types de congés au démarrage (idempotent)
    // ============================================
    @PostConstruct
    public void initialiserTypes() {
        // libelle, code, categorie, justificatifRequis, dureeMax, reserveFemme, compteJoursOuvres

        // --- DEDUCTIBLE ---
        creer("Congé annuel",   "ANNUEL",   "DEDUCTIBLE",     false, null, false, true);

        // --- NON DEDUCTIBLE (événements + congés payés spéciaux) ---
        creer("Mariage",        "MARIAGE",  "NON_DEDUCTIBLE", true,  null, false, true);
        creer("Veuvage",        "VEUVAGE",  "NON_DEDUCTIBLE", true,  null, false, true);
        creer("Décès",          "DECES",    "NON_DEDUCTIBLE", true,  null, false, true);
        creer("Baptême",        "BAPTEME",  "NON_DEDUCTIBLE", true,  null, false, true);
        creer("Congé maternité","MATERNITE","NON_DEDUCTIBLE", true,  null, true,  false);
        creer("Congé paternité","PATERNITE","NON_DEDUCTIBLE", true,  null, false, false);
        creer("Congé maladie",  "MALADIE",  "NON_DEDUCTIBLE", true,  null, false, false);

        // --- SANS SOLDE ---
        creer("Congé sans solde","SANS_SOLDE","SANS_SOLDE",   false, null, false, true);
    }

    private void creer(String libelle, String code, String categorie, boolean justif,
                       Integer dureeMax, boolean reserveFemme, boolean joursOuvres) {
        boolean existe = typeCongeRepository.findAll().stream()
            .anyMatch(t -> t.getCode().equals(code));
        if (existe) return;

        TypeConge t = new TypeConge();
        t.setLibelle(libelle);
        t.setCode(code);
        t.setCategorie(categorie);
        t.setJustificatifRequis(justif);
        t.setDureeMaximale(dureeMax);
        t.setReserveFemme(reserveFemme);
        t.setCompteJoursOuvres(joursOuvres);
        t.setActif(true);
        typeCongeRepository.save(t);
    }

    public List<TypeConge> getTypesActifs() {
        return typeCongeRepository.findByActifTrue();
    }

    public List<TypeConge> getTous() {
        return typeCongeRepository.findAll();
    }
}