package sga_backend.service;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import sga_backend.dto.CreerEmployeRequest;
import sga_backend.entity.CategorieSocioPro;
import sga_backend.entity.Direction;
import sga_backend.entity.Fonction;
import sga_backend.entity.Profil;
import sga_backend.entity.UniteOrganisationnelle;
import sga_backend.repository.AgentRepository;
import sga_backend.repository.CategorieSocioProRepository;
import sga_backend.repository.DirectionRepository;
import sga_backend.repository.FonctionRepository;
import sga_backend.repository.ProfilRepository;
import sga_backend.repository.UniteRepository;

@Service
public class ImportEmployeService {

    @Autowired private EmployeService employeService;
    @Autowired private AgentRepository agentRepository;
    @Autowired private DirectionRepository directionRepository;
    @Autowired private UniteRepository uniteRepository;
    @Autowired private FonctionRepository fonctionRepository;
    @Autowired private CategorieSocioProRepository categorieSocioProRepository;
    @Autowired private ProfilRepository profilRepository;

    // Résultat d'un import
    public static class ResultatImport {
        public int totalLignes;
        public int crees;
        public int erreurs;
        public List<String> messagesErreur = new ArrayList<>();
    }

    @org.springframework.transaction.annotation.Transactional
    public ResultatImport importer(MultipartFile fichier) throws Exception {
        ResultatImport resultat = new ResultatImport();
        DataFormatter fmt = new DataFormatter();

        // Liste des requêtes valides (préparées en passe 1, créées en passe 2)
        List<CreerEmployeRequest> requetesValides = new ArrayList<>();

        try (InputStream is = fichier.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            // ===== PASSE 1 : VALIDATION (rien n'est créé) =====
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String matricule = get(fmt, row, 0);
                String nom       = get(fmt, row, 1);
                String prenom    = get(fmt, row, 2);
                String emailPro  = get(fmt, row, 18);

                // Ligne vide ? on ignore
                if (matricule.isEmpty() && nom.isEmpty() && prenom.isEmpty()) continue;

                resultat.totalLignes++;
                int numLigneAffiche = i + 1;

                try {
                    if (matricule.isEmpty()) throw new RuntimeException("Matricule manquant");
                    if (nom.isEmpty() || prenom.isEmpty()) throw new RuntimeException("Nom ou prénom manquant");
                    if (emailPro.isEmpty()) throw new RuntimeException("Email professionnel manquant");

                    if (agentRepository.existsByMatricule(matricule)) {
                        throw new RuntimeException("Matricule déjà existant : " + matricule);
                    }
                    if (agentRepository.existsByEmail(emailPro)) {
                        throw new RuntimeException("Email déjà existant : " + emailPro);
                    }

                    CreerEmployeRequest req = new CreerEmployeRequest();
                    req.setMatricule(matricule);
                    req.setNom(nom);
                    req.setPrenom(prenom);
                    req.setDateNaissance(parseDate(get(fmt, row, 3)));
                    req.setSexe(get(fmt, row, 4));
                    req.setCni(get(fmt, row, 5));
                    req.setDateEmbauche(parseDate(get(fmt, row, 6)));
                    req.setPoste(get(fmt, row, 8));
                    req.setStatut(vide(get(fmt, row, 14)) ? "ACTIF" : get(fmt, row, 14));
                    req.setTelephone(get(fmt, row, 16));
                    req.setEmailPersonnel(get(fmt, row, 17));
                    req.setEmail(emailPro);
                    req.setAdresse1(get(fmt, row, 19));
                    req.setAdresse2(get(fmt, row, 20));
                    req.setVille(get(fmt, row, 21));

                    String libDirection = get(fmt, row, 9);
                    String libDivision  = get(fmt, row, 10);
                    String libFonction  = get(fmt, row, 12);
                    String libCategorie = get(fmt, row, 13);
                    String libProfil    = get(fmt, row, 15);

                    if (!vide(libDivision)) {
                        UniteOrganisationnelle u = trouverUnite(libDivision);
                        if (u == null) throw new RuntimeException("Division introuvable : " + libDivision);
                        req.setIdUnite(u.getId());
                    }
                    if (!vide(libDirection)) {
                        Direction dir = trouverDirection(libDirection);
                        if (dir == null) throw new RuntimeException("Direction introuvable : " + libDirection);
                        req.setIdDirection(dir.getId());
                    }
                    if (!vide(libFonction)) {
                        Fonction f = trouverFonction(libFonction);
                        if (f != null) req.setIdFonction(f.getId());
                    }
                    if (!vide(libCategorie)) {
                        CategorieSocioPro cat = trouverCategorie(libCategorie);
                        if (cat != null) req.setIdCategorieSocioPro(cat.getId());
                    }
                    if (!vide(libProfil)) {
                        Profil p = trouverProfil(libProfil);
                        if (p == null) throw new RuntimeException("Profil introuvable : " + libProfil);
                        req.setIdProfil(p.getId());
                    } else {
                        throw new RuntimeException("Profil manquant (obligatoire)");
                    }

                    // La ligne est valide : on la met de côté pour la passe 2
                    requetesValides.add(req);

                } catch (Exception e) {
                    resultat.erreurs++;
                    resultat.messagesErreur.add("Ligne " + numLigneAffiche + " : " + e.getMessage());
                }
            }
        }

        // ===== DÉCISION : TOUT OU RIEN =====
        if (resultat.erreurs > 0) {
            // Au moins une erreur → on n'importe RIEN
            resultat.crees = 0;
            resultat.messagesErreur.add(0, "Import annulé : " + resultat.erreurs
                + " erreur(s) détectée(s). Aucun employé n'a été importé. Corrigez le fichier et réessayez.");
            return resultat;
        }

        // ===== PASSE 2 : CRÉATION (uniquement si zéro erreur) =====
        for (CreerEmployeRequest req : requetesValides) {
            employeService.creerEmploye(req);
            resultat.crees++;
        }

        return resultat;
    }
        @org.springframework.transaction.annotation.Transactional
    public ResultatImport importerAvecMapping(MultipartFile fichier, java.util.Map<String, String> mapping) throws Exception {
        ResultatImport resultat = new ResultatImport();
        DataFormatter fmt = new DataFormatter();
        List<CreerEmployeRequest> requetesValides = new ArrayList<>();

        try (InputStream is = fichier.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            // 1. Lire les en-têtes pour connaître l'index de chaque colonne
            Row entete = sheet.getRow(0);
            java.util.Map<String, Integer> indexColonne = new java.util.HashMap<>();
            if (entete != null) {
                for (int c = 0; c < entete.getLastCellNum(); c++) {
                    Cell cell = entete.getCell(c);
                    String nomCol = (cell == null) ? "" : fmt.formatCellValue(cell).trim();
                    if (!nomCol.isEmpty()) indexColonne.put(nomCol, c);
                }
            }

            // 2. PASSE 1 : validation
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String matricule = lireChamp(fmt, row, mapping, indexColonne, "matricule");
                String nom       = lireChamp(fmt, row, mapping, indexColonne, "nom");
                String prenom    = lireChamp(fmt, row, mapping, indexColonne, "prenom");
                String emailPro  = lireChamp(fmt, row, mapping, indexColonne, "email");

                if (matricule.isEmpty() && nom.isEmpty() && prenom.isEmpty()) continue;

                resultat.totalLignes++;
                int numLigne = i + 1;

                try {
                    if (matricule.isEmpty()) throw new RuntimeException("Matricule manquant");
                    if (nom.isEmpty() || prenom.isEmpty()) throw new RuntimeException("Nom ou prénom manquant");
                    if (emailPro.isEmpty()) throw new RuntimeException("Email professionnel manquant");

                    if (agentRepository.existsByMatricule(matricule))
                        throw new RuntimeException("Matricule déjà existant : " + matricule);
                    if (agentRepository.existsByEmail(emailPro))
                        throw new RuntimeException("Email déjà existant : " + emailPro);

                    CreerEmployeRequest req = new CreerEmployeRequest();
                    req.setMatricule(matricule);
                    req.setNom(nom);
                    req.setPrenom(prenom);
                    req.setDateNaissance(parseDate(lireChamp(fmt, row, mapping, indexColonne, "dateNaissance")));
                    req.setSexe(lireChamp(fmt, row, mapping, indexColonne, "sexe"));
                    req.setCni(lireChamp(fmt, row, mapping, indexColonne, "cni"));
                    req.setDateEmbauche(parseDate(lireChamp(fmt, row, mapping, indexColonne, "dateEmbauche")));
                    req.setPoste(lireChamp(fmt, row, mapping, indexColonne, "poste"));
                    String statut = lireChamp(fmt, row, mapping, indexColonne, "statut");
                    req.setStatut(vide(statut) ? "ACTIF" : statut);
                    req.setTelephone(lireChamp(fmt, row, mapping, indexColonne, "telephone"));
                    req.setEmailPersonnel(lireChamp(fmt, row, mapping, indexColonne, "emailPersonnel"));
                    req.setEmail(emailPro);
                    req.setAdresse1(lireChamp(fmt, row, mapping, indexColonne, "adresse1"));
                    req.setAdresse2(lireChamp(fmt, row, mapping, indexColonne, "adresse2"));
                    req.setVille(lireChamp(fmt, row, mapping, indexColonne, "ville"));

                    String libDirection = lireChamp(fmt, row, mapping, indexColonne, "direction");
                    String libDivision  = lireChamp(fmt, row, mapping, indexColonne, "division");
                    String libFonction  = lireChamp(fmt, row, mapping, indexColonne, "fonction");
                    String libCategorie = lireChamp(fmt, row, mapping, indexColonne, "categorie");
                    String libProfil    = lireChamp(fmt, row, mapping, indexColonne, "profil");

                    if (!vide(libDivision)) {
                        UniteOrganisationnelle u = trouverUnite(libDivision);
                        if (u == null) throw new RuntimeException("Division introuvable : " + libDivision);
                        req.setIdUnite(u.getId());
                    }
                    if (!vide(libDirection)) {
                        Direction dir = trouverDirection(libDirection);
                        if (dir == null) throw new RuntimeException("Direction introuvable : " + libDirection);
                        req.setIdDirection(dir.getId());
                    }
                    if (!vide(libFonction)) {
                        Fonction f = trouverFonction(libFonction);
                        if (f != null) req.setIdFonction(f.getId());
                    }
                    if (!vide(libCategorie)) {
                        CategorieSocioPro cat = trouverCategorie(libCategorie);
                        if (cat != null) req.setIdCategorieSocioPro(cat.getId());
                    }
                    if (!vide(libProfil)) {
                        Profil p = trouverProfil(libProfil);
                        if (p == null) throw new RuntimeException("Profil introuvable : " + libProfil);
                        req.setIdProfil(p.getId());
                    } else {
                        throw new RuntimeException("Profil manquant (obligatoire)");
                    }

                    requetesValides.add(req);

                } catch (Exception e) {
                    resultat.erreurs++;
                    resultat.messagesErreur.add("Ligne " + numLigne + " : " + e.getMessage());
                }
            }
        }

        // TOUT OU RIEN
        if (resultat.erreurs > 0) {
            resultat.crees = 0;
            resultat.messagesErreur.add(0, "Import annulé : " + resultat.erreurs
                + " erreur(s) détectée(s). Aucun employé n'a été importé.");
            return resultat;
        }

        for (CreerEmployeRequest req : requetesValides) {
            employeService.creerEmploye(req);
            resultat.crees++;
        }
        return resultat;
    }

    // Lit la valeur d'un champ selon le mapping : champ -> nom colonne -> index -> cellule
    private String lireChamp(DataFormatter fmt, Row row, java.util.Map<String, String> mapping,
                             java.util.Map<String, Integer> indexColonne, String champ) {
        String nomColonne = mapping.get(champ);
        if (nomColonne == null || nomColonne.isEmpty()) return "";
        Integer idx = indexColonne.get(nomColonne);
        if (idx == null) return "";
        Cell cell = row.getCell(idx);
        if (cell == null) return "";
        return fmt.formatCellValue(cell).trim();
    }

    // ===== Helpers de lecture =====
    private String get(DataFormatter fmt, Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return fmt.formatCellValue(cell).trim();
    }

    private boolean vide(String s) { return s == null || s.trim().isEmpty(); }

    private LocalDate parseDate(String s) {
        if (vide(s)) return null;
        try {
            return LocalDate.parse(s.trim()); // format attendu : yyyy-MM-dd
        } catch (Exception e) {
            return null;
        }
    }

    // ===== Résolution des libellés (insensible à la casse) =====
    private Direction trouverDirection(String libelle) {
        for (Direction d : directionRepository.findAll()) {
            if (d.getLibelle() != null && d.getLibelle().equalsIgnoreCase(libelle.trim())) return d;
        }
        return null;
    }
    private UniteOrganisationnelle trouverUnite(String libelle) {
        for (UniteOrganisationnelle u : uniteRepository.findAll()) {
            if (u.getLibelle() != null && u.getLibelle().equalsIgnoreCase(libelle.trim())) return u;
        }
        return null;
    }
    private Fonction trouverFonction(String libelle) {
        for (Fonction f : fonctionRepository.findAll()) {
            if (f.getLibelle() != null && f.getLibelle().equalsIgnoreCase(libelle.trim())) return f;
        }
        return null;
    }
    private CategorieSocioPro trouverCategorie(String libelle) {
        for (CategorieSocioPro c : categorieSocioProRepository.findAll()) {
            if (c.getLibelle() != null && c.getLibelle().equalsIgnoreCase(libelle.trim())) return c;
        }
        return null;
    }
    private Profil trouverProfil(String libelle) {
        for (Profil p : profilRepository.findAll()) {
            if (p.getLibelle() != null && p.getLibelle().equalsIgnoreCase(libelle.trim())) return p;
        }
        return null;
    }
        // Lit uniquement la première ligne (en-têtes) et renvoie les noms de colonnes
    public List<String> lireColonnes(MultipartFile fichier) throws Exception {
        List<String> colonnes = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();

        try (InputStream is = fichier.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row entete = sheet.getRow(0);
            if (entete == null) return colonnes;

            for (int c = 0; c < entete.getLastCellNum(); c++) {
                Cell cell = entete.getCell(c);
                String nom = (cell == null) ? "" : fmt.formatCellValue(cell).trim();
                colonnes.add(nom.isEmpty() ? ("Colonne " + (c + 1)) : nom);
            }
        }
        return colonnes;
    }
}