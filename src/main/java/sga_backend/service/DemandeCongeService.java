package sga_backend.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sga_backend.dto.CreerDemandeRequest;
import sga_backend.dto.DemandeCongeDTO;
import sga_backend.entity.Agent;
import sga_backend.entity.DemandeConge;
import sga_backend.entity.TypeConge;
import sga_backend.entity.Utilisateur;
import sga_backend.repository.AgentRepository;
import sga_backend.repository.DemandeCongeRepository;
import sga_backend.repository.TypeCongeRepository;
import sga_backend.repository.UtilisateurRepository;

@Service
public class DemandeCongeService {

    @Autowired private DemandeCongeRepository demandeCongeRepository;
    @Autowired private TypeCongeRepository typeCongeRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private JourFerieService jourFerieService;
    @Autowired private SoldeCongesService soldeCongesService;
    @Autowired private CircuitValidationService circuitValidationService;

    @Transactional
    public DemandeCongeDTO creerDemande(CreerDemandeRequest request) {

        Agent agent = agentRepository.findById(request.getIdAgent())
            .orElseThrow(() -> new RuntimeException("Agent introuvable"));

        TypeConge type = typeCongeRepository.findById(request.getIdTypeConge())
            .orElseThrow(() -> new RuntimeException("Type de congé introuvable"));

        if (request.getDateDebut() == null || request.getDateFin() == null) {
            throw new RuntimeException("Les dates de début et de fin sont obligatoires");
        }
        if (request.getDateFin().isBefore(request.getDateDebut())) {
            throw new RuntimeException("La date de fin ne peut pas précéder la date de début");
        }
        if (request.getDateDebut().isBefore(LocalDate.now())) {
            throw new RuntimeException("La date de début ne peut pas être dans le passé");
        }

        // Règle de jouissance : l'agent doit avoir 12 mois d'ancienneté à la date de début du congé.
        // (On n'applique cette règle qu'aux congés DEDUCTIBLE, qui puisent dans le solde annuel.)
        if ("DEDUCTIBLE".equalsIgnoreCase(type.getCategorie())) {
            if (!soldeCongesService.peutPrendreConge(agent, request.getDateDebut())) {
                throw new RuntimeException(
                    "L'agent ne peut pas encore prendre de congé : la période de référence de 12 mois "
                    + "depuis la date d'embauche n'est pas atteinte à la date de début demandée.");
            }
        }

        if (Boolean.TRUE.equals(type.getReserveFemme())) {
            if (agent.getSexe() == null || !agent.getSexe().equalsIgnoreCase("F")) {
                throw new RuntimeException("Ce type de congé est réservé aux agents de sexe féminin");
            }
        }

        String momentDebut = request.getMomentDebut() != null ? request.getMomentDebut() : "JOURNEE";
        String momentFin   = request.getMomentFin()   != null ? request.getMomentFin()   : "JOURNEE";

        BigDecimal nbJours = calculerNombreJours(
            request.getDateDebut(), request.getDateFin(),
            Boolean.TRUE.equals(type.getCompteJoursOuvres()),
            momentDebut, momentFin
        );

        if (nbJours.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("La période sélectionnée ne contient aucun jour décompté");
        }

        if (type.getDureeMaximale() != null
            && nbJours.compareTo(BigDecimal.valueOf(type.getDureeMaximale())) > 0) {
            throw new RuntimeException("La durée demandée (" + nbJours + " j) dépasse le maximum autorisé ("
                + type.getDureeMaximale() + " j) pour ce type de congé");
        }

        DemandeConge demande = new DemandeConge();
        demande.setAgent(agent);
        demande.setTypeConge(type);
        demande.setDateDebut(request.getDateDebut());
        demande.setDateFin(request.getDateFin());
        demande.setMomentDebut(momentDebut);
        demande.setMomentFin(momentFin);
        demande.setNombreJours(nbJours);
        demande.setMotif(request.getMotif());
        demande.setStatut("EN_ATTENTE");

        // Demandeur (utilisateur connecté) — optionnel mais recommandé
        if (request.getIdDemandeur() != null) {
            Utilisateur demandeur = utilisateurRepository.findById(request.getIdDemandeur()).orElse(null);
            demande.setUtilisateurDemandeur(demandeur);
        }

        DemandeConge sauvegardee = demandeCongeRepository.save(demande);

        // Génère les étapes de validation selon le circuit (Siège ou Régional)
        circuitValidationService.genererEtapes(sauvegardee);

        return toDto(sauvegardee);
    }

    public BigDecimal calculerNombreJours(LocalDate debut, LocalDate fin, boolean joursOuvres,
                                          String momentDebut, String momentFin) {
        int compteur = 0;
        LocalDate jour = debut;
        while (!jour.isAfter(fin)) {
            if (joursOuvres) {
                DayOfWeek j = jour.getDayOfWeek();
                boolean estWeekend = (j == DayOfWeek.SATURDAY || j == DayOfWeek.SUNDAY);
                boolean estFerie = jourFerieService.estFerie(jour);
                if (!estWeekend && !estFerie) compteur++;
            } else {
                compteur++;
            }
            jour = jour.plusDays(1);
        }

        BigDecimal total = BigDecimal.valueOf(compteur);
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            if (debut.isEqual(fin)) {
                if (!"JOURNEE".equals(momentDebut) || !"JOURNEE".equals(momentFin)) {
                    total = BigDecimal.valueOf(0.5);
                }
            } else {
                if ("APRES_MIDI".equals(momentDebut)) total = total.subtract(BigDecimal.valueOf(0.5));
                if ("MATIN".equals(momentFin))        total = total.subtract(BigDecimal.valueOf(0.5));
            }
        }
        return total;
    }

    @Transactional
    public void enregistrerJustificatif(Long idDemande, String nomFichier) {
        DemandeConge demande = demandeCongeRepository.findById(idDemande)
            .orElseThrow(() -> new RuntimeException("Demande introuvable"));
        demande.setJustificatif(nomFichier);
        demandeCongeRepository.save(demande);
    }

    public List<DemandeCongeDTO> getDemandesAgent(Long idAgent) {
        List<DemandeCongeDTO> liste = new ArrayList<>();
        for (DemandeConge d : demandeCongeRepository.findByAgentIdOrderByDateCreationDesc(idAgent)) {
            liste.add(toDto(d));
        }
        return liste;
    }

    public DemandeCongeDTO getDemande(Long id) {
        DemandeConge d = demandeCongeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Demande introuvable"));
        return toDto(d);
    }

    private DemandeCongeDTO toDto(DemandeConge d) {
        DemandeCongeDTO dto = new DemandeCongeDTO();
        dto.setId(d.getId());

        if (d.getAgent() != null) {
            dto.setIdAgent(d.getAgent().getId());
            dto.setNomAgent(d.getAgent().getNom());
            dto.setPrenomAgent(d.getAgent().getPrenom());
            dto.setMatriculeAgent(d.getAgent().getMatricule());
        }

        if (d.getUtilisateurDemandeur() != null) {
            dto.setIdDemandeur(d.getUtilisateurDemandeur().getId());
            dto.setNomDemandeur(d.getUtilisateurDemandeur().getNom() + " " + d.getUtilisateurDemandeur().getPrenom());
        }

        if (d.getTypeConge() != null) {
            dto.setIdTypeConge(d.getTypeConge().getId());
            dto.setLibelleTypeConge(d.getTypeConge().getLibelle());
            dto.setCodeTypeConge(d.getTypeConge().getCode());
            dto.setCategorieTypeConge(d.getTypeConge().getCategorie());
        }

        dto.setDateDebut(d.getDateDebut());
        dto.setDateFin(d.getDateFin());
        dto.setMomentDebut(d.getMomentDebut());
        dto.setMomentFin(d.getMomentFin());
        dto.setNombreJours(d.getNombreJours());
        dto.setMotif(d.getMotif());
        dto.setJustificatif(d.getJustificatif());
        dto.setStatut(d.getStatut());
        dto.setDateCreation(d.getDateCreation());

        return dto;
    }
    
}