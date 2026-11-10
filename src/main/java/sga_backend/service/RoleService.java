package sga_backend.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sga_backend.dto.UtilisateurRoleDTO;
import sga_backend.entity.Utilisateur;
import sga_backend.repository.UtilisateurRepository;

@Service
public class RoleService {

    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired
    private JournalService journalService;

    // Liste tous les utilisateurs avec leur droit admin (pour la page SUPERADMIN)
    @Transactional(readOnly = true)
    public List<UtilisateurRoleDTO> listerUtilisateurs() {
        return utilisateurRepository.findAll().stream().map(u -> {
            UtilisateurRoleDTO dto = new UtilisateurRoleDTO();
            dto.setId(u.getId());
            dto.setNom(u.getNom());
            dto.setPrenom(u.getPrenom());
            dto.setLogin(u.getLogin());
            dto.setStatut(u.getStatut());
            dto.setEstAdmin(Boolean.TRUE.equals(u.getEstAdmin()));

            if (u.getProfil() != null) {
                dto.setCodeProfil(u.getProfil().getCode());
                dto.setLibelleProfil(u.getProfil().getLibelle());
            }
            if (u.getAgent() != null) {
                dto.setMatricule(u.getAgent().getMatricule());
                if (u.getAgent().getDirection() != null) {
                    dto.setLibelleDirection(u.getAgent().getDirection().getLibelle());
                }
                if (u.getAgent().getUnite() != null) {
                    dto.setLibelleUnite(u.getAgent().getUnite().getLibelle());
                }
            }
            return dto;
        }).toList();
    }

    // Donne ou retire le droit d'administration à un utilisateur
    @Transactional
        public void changerDroitAdmin(Long idUtilisateur, boolean valeur) {
        Utilisateur u = utilisateurRepository.findById(idUtilisateur)
            .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        // Un SUPERADMIN ne reçoit jamais le droit admin métier (séparation stricte des rôles)
        if (u.getProfil() != null && "SUPERADMIN".equalsIgnoreCase(u.getProfil().getCode())) {
            throw new RuntimeException("Le SUPERADMIN ne peut pas recevoir de droits d'administration métier.");
        }

        u.setEstAdmin(valeur);
        utilisateurRepository.save(u);

        // >>> JOURNAL : attribution ou retrait du droit admin
        String cible = (u.getAgent() != null)
            ? u.getAgent().getPrenom() + " " + u.getAgent().getNom()
            : u.getLogin();

        journalService.enregistrer(
            "ATTRIBUTION_ROLE",
            valeur ? "Attribution du droit admin" : "Retrait du droit admin",
            cible,
            valeur ? "Droit administrateur accordé" : "Droit administrateur retiré"
        );
    }
}