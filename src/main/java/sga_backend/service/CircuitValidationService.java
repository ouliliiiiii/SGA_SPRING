package sga_backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sga_backend.dto.DemandeAValiderDTO;
import sga_backend.dto.EtapeValidationDTO;
import sga_backend.entity.Agent;
import sga_backend.entity.DemandeConge;
import sga_backend.entity.EtapeValidation;
import sga_backend.entity.SoldeConges;
import sga_backend.entity.Utilisateur;
import sga_backend.repository.DemandeCongeRepository;
import sga_backend.repository.EtapeValidationRepository;
import sga_backend.repository.SoldeCongesRepository;
import sga_backend.repository.UtilisateurRepository;

@Service
public class CircuitValidationService {

    @Autowired private EtapeValidationRepository etapeValidationRepository;
    @Autowired private DemandeCongeRepository demandeCongeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private SoldeCongesRepository soldeCongesRepository;
    @Autowired private SoldeCongesService soldeCongesService;

    // ============================================================
    // GÉNÉRATION DES ÉTAPES (couche 1) — avec ciblage par unité/direction
    // SIEGE    : Chef de division -> Directeur -> ADMIN -> DAF
    // REGIONAL : CSR -> SG -> ADMIN -> DAF
    // ============================================================
    @Transactional
    public void genererEtapes(DemandeConge demande) {
        Agent agent = demande.getAgent();

        // Profil de l'agent (code + niveau), via son utilisateur lié
        String codeProfilAgent = "";
        int niveauAgent = 0;
        Utilisateur u = utilisateurRepository.findByAgentId(agent.getId()).orElse(null);
        if (u != null && u.getProfil() != null) {
            codeProfilAgent = u.getProfil().getCode();
            niveauAgent = u.getProfil().getNiveau() != null ? u.getProfil().getNiveau() : 0;
        }

        // Unité et direction du demandeur (pour cibler les bonnes étapes)
        Long idUniteAgent = agent.getUnite() != null ? agent.getUnite().getId() : null;
        Long idDirectionAgent = agent.getDirection() != null ? agent.getDirection().getId() : null;

        List<EtapeValidation> etapes = new ArrayList<>();

        // ===== CAS SPÉCIAUX DU SOMMET =====
        if ("ADMIN".equalsIgnoreCase(codeProfilAgent)) {
            etapes.add(creerEtape(demande, 1, "DAF", "Directeur Administratif et Financier", null, null));
            etapeValidationRepository.saveAll(etapes);
            return;
        }
        if ("DAF".equalsIgnoreCase(codeProfilAgent) || "SG".equalsIgnoreCase(codeProfilAgent)) {
            etapes.add(creerEtape(demande, 1, "DG", "Directeur Général", null, null));
            etapeValidationRepository.saveAll(etapes);
            return;
        }

        // ===== RÈGLE GÉNÉRALE PAR NIVEAU =====
        String typeLieu = "SIEGE";
        if (agent.getLieuTravail() != null && agent.getLieuTravail().getTypeEntite() != null) {
            typeLieu = agent.getLieuTravail().getTypeEntite().name().toUpperCase();
        }

        // Circuit : profil, libellé, niveau
        List<Object[]> circuit = new ArrayList<>();
        if ("REGIONAL".equals(typeLieu)) {
            circuit.add(new Object[]{"CSR",   "Chef de Service Régional", 50});
            circuit.add(new Object[]{"SG",    "Secrétaire Général",       80});
            circuit.add(new Object[]{"ADMIN", "Administration",          100});
            circuit.add(new Object[]{"DAF",   "Directeur Administratif et Financier", 85});
        } else {
            circuit.add(new Object[]{"CHEF_DIVISION", "Chef de Division", 50});
            circuit.add(new Object[]{"DIRECTEUR",     "Directeur",        60});
            circuit.add(new Object[]{"ADMIN",         "Administration",  100});
            circuit.add(new Object[]{"DAF",           "Directeur Administratif et Financier", 85});
        }

        // On garde les étapes de niveau strictement supérieur à celui de l'agent
        int ordre = 1;
        for (Object[] e : circuit) {
            String profilEtape = (String) e[0];
            String libelle     = (String) e[1];
            int niveauEtape    = (int) e[2];

            if (niveauEtape > niveauAgent) {
                // Ciblage selon le profil de l'étape
                Long uniteCible = null;
                Long directionCible = null;

                if ("CHEF_DIVISION".equals(profilEtape) || "CSR".equals(profilEtape)) {
                    // Chef de division / CSR : cibler l'unité du demandeur
                    uniteCible = idUniteAgent;
                } else if ("DIRECTEUR".equals(profilEtape)) {
                    // Directeur : cibler la direction du demandeur
                    directionCible = idDirectionAgent;
                }
                // ADMIN, DAF, SG, DG : pas de ciblage (rôle global)

                etapes.add(creerEtape(demande, ordre, profilEtape, libelle, uniteCible, directionCible));
                ordre++;
            }
        }

        // Garde-fou : si rien ne reste, on met DAF par défaut
        if (etapes.isEmpty()) {
            etapes.add(creerEtape(demande, 1, "DAF", "Directeur Administratif et Financier", null, null));
        }

        etapeValidationRepository.saveAll(etapes);
    }

    // creerEtape avec ciblage (unité / direction)
    private EtapeValidation creerEtape(DemandeConge demande, int ordre, String profil, String libelle,
                                       Long idUniteAttendue, Long idDirectionAttendue) {
        EtapeValidation e = new EtapeValidation();
        e.setDemande(demande);
        e.setOrdre(ordre);
        e.setProfilAttendu(profil);
        e.setLibelle(libelle);
        e.setStatut("EN_ATTENTE");
        e.setIdUniteAttendue(idUniteAttendue);
        e.setIdDirectionAttendue(idDirectionAttendue);
        return e;
    }

    public List<EtapeValidationDTO> getEtapes(Long idDemande) {
        List<EtapeValidationDTO> liste = new ArrayList<>();
        for (EtapeValidation e : etapeValidationRepository.findByDemandeIdOrderByOrdreAsc(idDemande)) {
            liste.add(toDto(e));
        }
        return liste;
    }

    private EtapeValidationDTO toDto(EtapeValidation e) {
        EtapeValidationDTO dto = new EtapeValidationDTO();
        dto.setId(e.getId());
        dto.setIdDemande(e.getDemande() != null ? e.getDemande().getId() : null);
        dto.setOrdre(e.getOrdre());
        dto.setProfilAttendu(e.getProfilAttendu());
        dto.setLibelle(e.getLibelle());
        dto.setStatut(e.getStatut());
        if (e.getValidateur() != null) {
            dto.setIdValidateur(e.getValidateur().getId());
            dto.setNomValidateur(e.getValidateur().getNom() + " " + e.getValidateur().getPrenom());
        }
        dto.setDateAction(e.getDateAction());
        dto.setCommentaire(e.getCommentaire());
        return dto;
    }

    // ============================================================
    // COUCHE 3 : les demandes qu'un utilisateur doit valider
    //   - filtre par profil
    //   - PLUS : ciblage unité/direction
    //   - PLUS : marque l'étape finale (dernière du circuit)
    // ============================================================
    public List<DemandeAValiderDTO> getDemandesAValider(Long idUtilisateur) {
        Utilisateur user = utilisateurRepository.findById(idUtilisateur).orElse(null);
        if (user == null || user.getProfil() == null) return new ArrayList<>();

        String codeProfil = user.getProfil().getCode();

        // Unité et direction de l'utilisateur connecté (via son agent)
        Long idUniteUser = null;
        Long idDirectionUser = null;
        if (user.getAgent() != null) {
            if (user.getAgent().getUnite() != null) {
                idUniteUser = user.getAgent().getUnite().getId();
            }
            if (user.getAgent().getDirection() != null) {
                idDirectionUser = user.getAgent().getDirection().getId();
            }
        }

        // Profils que cet utilisateur peut traiter
        List<String> profilsTraitables = new ArrayList<>();
        profilsTraitables.add(codeProfil);
        if ("ADMIN".equalsIgnoreCase(codeProfil)) {
            profilsTraitables.add("DAF");
        }

        List<EtapeValidation> etapes = etapeValidationRepository
            .findByProfilAttenduInAndStatut(profilsTraitables, "EN_ATTENTE");

        List<DemandeAValiderDTO> resultat = new ArrayList<>();
        for (EtapeValidation etape : etapes) {
            // Ne garder que si c'est l'étape COURANTE
            if (!estEtapeCourante(etape)) continue;

            // ===== FILTRE DE CIBLAGE =====
            if (etape.getIdUniteAttendue() != null) {
                if (idUniteUser == null || !idUniteUser.equals(etape.getIdUniteAttendue())) {
                    continue; // pas le bon chef de division
                }
            }
            if (etape.getIdDirectionAttendue() != null) {
                if (idDirectionUser == null || !idDirectionUser.equals(etape.getIdDirectionAttendue())) {
                    continue; // pas le bon directeur
                }
            }

            DemandeConge d = etape.getDemande();
            DemandeAValiderDTO dto = new DemandeAValiderDTO();
            dto.setIdDemande(d.getId());
            dto.setIdEtape(etape.getId());
            dto.setLibelleEtape(etape.getLibelle());

           if (d.getAgent() != null) {
    dto.setIdAgent(d.getAgent().getId());
    dto.setNomAgent(d.getAgent().getNom());
    dto.setPrenomAgent(d.getAgent().getPrenom());
    dto.setMatriculeAgent(d.getAgent().getMatricule());
    if (d.getAgent().getFonction() != null) {
        dto.setFonctionAgent(d.getAgent().getFonction().getLibelle());
    }
    // Profil du demandeur (via son utilisateur)
    Utilisateur uAgent = utilisateurRepository.findByAgentId(d.getAgent().getId()).orElse(null);
    if (uAgent != null && uAgent.getProfil() != null) {
        dto.setProfilAgent(uAgent.getProfil().getLibelle());
    }
}
            if (d.getTypeConge() != null) {
                dto.setLibelleTypeConge(d.getTypeConge().getLibelle());
                dto.setCategorieConge(d.getTypeConge().getCategorie());
            }
            dto.setDateDebut(d.getDateDebut());
            dto.setDateFin(d.getDateFin());
            dto.setNombreJours(d.getNombreJours());
            dto.setMotif(d.getMotif());
            dto.setStatut(d.getStatut());
            dto.setDateCreation(d.getDateCreation());

            // ===== ÉTAPE FINALE ? (dernière du circuit = ordre le plus élevé) =====
            List<EtapeValidation> toutesEtapes = etapeValidationRepository
                .findByDemandeIdOrderByOrdreAsc(etape.getDemande().getId());
            int ordreMax = toutesEtapes.stream()
                .mapToInt(EtapeValidation::getOrdre)
                .max()
                .orElse(etape.getOrdre());
            dto.setEtapeFinale(etape.getOrdre() == ordreMax);

            resultat.add(dto);
        }
        return resultat;
    }

    // Vérifie qu'une étape est bien la première EN_ATTENTE de sa demande
    private boolean estEtapeCourante(EtapeValidation etape) {
        List<EtapeValidation> toutes = etapeValidationRepository
            .findByDemandeIdOrderByOrdreAsc(etape.getDemande().getId());
        EtapeValidation courante = toutes.stream()
            .filter(e -> "EN_ATTENTE".equals(e.getStatut()))
            .findFirst().orElse(null);
        return courante != null && courante.getId().equals(etape.getId());
    }

    // ============================================================
    // VALIDER UNE ÉTAPE (couche 2)
    // ============================================================
    @Transactional
    public void valider(Long idEtape, Long idValidateur, String commentaire) {
        EtapeValidation etape = etapeValidationRepository.findById(idEtape)
            .orElseThrow(() -> new RuntimeException("Étape introuvable"));

        Utilisateur validateur = utilisateurRepository.findById(idValidateur)
            .orElseThrow(() -> new RuntimeException("Validateur introuvable"));

        if (!"EN_ATTENTE".equals(etape.getStatut())) {
            throw new RuntimeException("Cette étape a déjà été traitée");
        }

        verifierEtapeCourante(etape);
        verifierProfil(validateur, etape);

        etape.setStatut("VALIDEE");
        etape.setValidateur(validateur);
        etape.setDateAction(LocalDateTime.now());
        etape.setCommentaire(commentaire);
        etapeValidationRepository.save(etape);

        DemandeConge demande = etape.getDemande();

        List<EtapeValidation> toutes = etapeValidationRepository.findByDemandeIdOrderByOrdreAsc(demande.getId());
        EtapeValidation suivante = toutes.stream()
            .filter(e -> e.getOrdre() > etape.getOrdre() && "EN_ATTENTE".equals(e.getStatut()))
            .findFirst().orElse(null);

        if (suivante != null) {
            demande.setStatut("EN_COURS");
            demandeCongeRepository.save(demande);
        } else {
            demande.setStatut("VALIDEE");
            demandeCongeRepository.save(demande);
            decrementerSolde(demande);
        }
    }

    // ============================================================
    // REFUSER UNE ÉTAPE (couche 2)
    // ============================================================
    @Transactional
    public void refuser(Long idEtape, Long idValidateur, String commentaire) {
        if (commentaire == null || commentaire.trim().isEmpty()) {
            throw new RuntimeException("Un commentaire est obligatoire pour refuser une demande");
        }

        EtapeValidation etape = etapeValidationRepository.findById(idEtape)
            .orElseThrow(() -> new RuntimeException("Étape introuvable"));

        Utilisateur validateur = utilisateurRepository.findById(idValidateur)
            .orElseThrow(() -> new RuntimeException("Validateur introuvable"));

        if (!"EN_ATTENTE".equals(etape.getStatut())) {
            throw new RuntimeException("Cette étape a déjà été traitée");
        }

        verifierEtapeCourante(etape);
        verifierProfil(validateur, etape);

        etape.setStatut("REFUSEE");
        etape.setValidateur(validateur);
        etape.setDateAction(LocalDateTime.now());
        etape.setCommentaire(commentaire);
        etapeValidationRepository.save(etape);

        DemandeConge demande = etape.getDemande();
        demande.setStatut("REFUSEE");
        demandeCongeRepository.save(demande);
    }

    // Vérifie que l'étape est bien la première EN_ATTENTE (on ne saute pas d'étape)
    private void verifierEtapeCourante(EtapeValidation etape) {
        List<EtapeValidation> toutes = etapeValidationRepository
            .findByDemandeIdOrderByOrdreAsc(etape.getDemande().getId());
        EtapeValidation courante = toutes.stream()
            .filter(e -> "EN_ATTENTE".equals(e.getStatut()))
            .findFirst().orElse(null);
        if (courante == null || !courante.getId().equals(etape.getId())) {
            throw new RuntimeException("Ce n'est pas l'étape en cours de validation");
        }
    }

    // Vérifie que l'utilisateur a le profil ET le ciblage (unité/direction) attendus.
    private void verifierProfil(Utilisateur validateur, EtapeValidation etape) {
        String profilUser = getCodeProfil(validateur);
        String profilAttendu = etape.getProfilAttendu();

        boolean autorise = profilAttendu.equalsIgnoreCase(profilUser);

        // À l'étape DAF, ADMIN peut aussi valider (remplaçant)
        if ("DAF".equalsIgnoreCase(profilAttendu) && "ADMIN".equalsIgnoreCase(profilUser)) {
            autorise = true;
        }

        if (!autorise) {
            throw new RuntimeException("Votre profil (" + profilUser + ") ne permet pas de valider cette étape ("
                + profilAttendu + ")");
        }

        // ===== VÉRIFICATION DU CIBLAGE =====
        Long idUniteUser = null;
        Long idDirectionUser = null;
        if (validateur.getAgent() != null) {
            if (validateur.getAgent().getUnite() != null) {
                idUniteUser = validateur.getAgent().getUnite().getId();
            }
            if (validateur.getAgent().getDirection() != null) {
                idDirectionUser = validateur.getAgent().getDirection().getId();
            }
        }

        if (etape.getIdUniteAttendue() != null) {
            if (idUniteUser == null || !idUniteUser.equals(etape.getIdUniteAttendue())) {
                throw new RuntimeException("Vous n'êtes pas le responsable de l'unité concernée par cette demande");
            }
        }
        if (etape.getIdDirectionAttendue() != null) {
            if (idDirectionUser == null || !idDirectionUser.equals(etape.getIdDirectionAttendue())) {
                throw new RuntimeException("Vous n'êtes pas le responsable de la direction concernée par cette demande");
            }
        }
    }

    private String getCodeProfil(Utilisateur u) {
        if (u.getProfil() == null) return "";
        return u.getProfil().getCode();
    }

    // Décrémente le solde à la validation finale (congés déductibles uniquement)
    private void decrementerSolde(DemandeConge demande) {
        if (demande.getTypeConge() == null) return;
        if (!"DEDUCTIBLE".equalsIgnoreCase(demande.getTypeConge().getCategorie())) return;

        Agent agent = demande.getAgent();
        SoldeConges solde = soldeCongesRepository.findByAgentId(agent.getId()).orElse(null);
        if (solde == null) return;

        BigDecimal pris = solde.getNbJourPrisDeductible() != null
            ? solde.getNbJourPrisDeductible() : BigDecimal.ZERO;
        solde.setNbJourPrisDeductible(pris.add(demande.getNombreJours()));
        soldeCongesRepository.save(solde);

        soldeCongesService.recalculerSoldeAgent(agent);
    }
}