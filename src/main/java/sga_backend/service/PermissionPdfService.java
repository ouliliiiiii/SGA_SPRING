package sga_backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;
import sga_backend.entity.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PermissionPdfService {

    private static final DateTimeFormatter FDATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Couleurs
    private static final Color VERT = new Color(0x00, 0x85, 0x3F);
    private static final Color JAUNE = new Color(0xFD, 0xEF, 0x42);
    private static final Color ROUGE = new Color(0xE3, 0x1B, 0x23);
    private static final Color ENCRE = new Color(0x1a, 0x2b, 0x33);
    private static final Color GRIS = new Color(0x6b, 0x7a, 0x82);
    private static final Color GRIS_CLAIR = new Color(0xf4, 0xf7, 0xf8);
    private static final Color FILET = new Color(0xd8, 0xe0, 0xe3);
    private static final Color OR = new Color(0xb7, 0x90, 0x2f);
    private static final Color BANDEAU = new Color(0xe9, 0xee, 0xf0);

    /**
     * Génère le PDF de la permission.
     * @param demande la demande
     * @param etapes  les étapes du circuit (pour les visas)
     * @param titre   le titre à afficher (déjà choisi : auto ou personnalisé)
     */
      public byte[] genererPermission(DemandeConge demande, List<EtapeValidation> etapes, String titre) {
        try {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();
            PdfContentByte cb = writer.getDirectContent();

            float W = PageSize.A4.getWidth();
            float H = PageSize.A4.getHeight();
            float marge = 50f;

            // ===== Cadre décoratif =====
            cb.setColorStroke(FILET); cb.setLineWidth(1.2f);
            cb.rectangle(marge-14, 40, W-2*(marge-14), H-80); cb.stroke();
            cb.setColorStroke(VERT); cb.setLineWidth(2.5f);
            cb.moveTo(marge-14, H-46); cb.lineTo(W-(marge-14), H-46); cb.stroke();
            cb.setColorStroke(OR); cb.setLineWidth(0.8f);
            cb.moveTo(marge-14, H-48); cb.lineTo(W-(marge-14), H-48); cb.stroke();

            float y = H - 75;

            // ===== Drapeau =====
            dessinerDrapeau(cb, W/2 - 40, y, 80, 50);
            y -= 18;

            // ===== En-tête =====
            texteCentre(cb, "RÉPUBLIQUE DU SÉNÉGAL", W/2, y, bold(12.5f), ENCRE); y -= 13;
            texteCentre(cb, "Un Peuple  —  Un But  —  Une Foi", W/2, y, italic(8.5f), OR); y -= 16;
            texteCentre(cb, "MINISTÈRE DE LA FAMILLE, DE L'ACTION SOCIALE ET DES SOLIDARITÉS", W/2, y, bold(8f), GRIS);
            y -= 34;

            // ===== Titre (bande verte) =====
            cb.setColorFill(VERT);
            cb.roundRectangle(marge, y-8, W-2*marge, 26, 4); cb.fill();
            texteCentre(cb, titre, W/2, y+2, bold(10.5f), Color.WHITE);
            y -= 48;

            // ===== I - IDENTIFICATION =====
            y = titreSection(cb, "I", "IDENTIFICATION", marge, W, y);
            y -= 30;

            Agent a = demande.getAgent();
            String nomComplet = (a != null) ? (nz(a.getNom()) + " " + nz(a.getPrenom())) : "";
            String fonction = (a != null && a.getFonction() != null) ? nz(a.getFonction().getLibelle()) : "—";
            String direction = (a != null && a.getDirection() != null) ? nz(a.getDirection().getLibelle()) : "—";
            String unite = (a != null && a.getUnite() != null) ? nz(a.getUnite().getLibelle()) : null;
            boolean aUnite = unite != null && !unite.isEmpty();

            float hauteurBloc = aUnite ? 88 : 68;
            cb.setColorFill(GRIS_CLAIR);
            cb.roundRectangle(marge, y-(hauteurBloc-18), W-2*marge, hauteurBloc, 3); cb.fill();

            float yi = y - 8;
            champ(cb, "Bénéficiaire", nomComplet, marge+12, yi); yi -= 20;
            champ(cb, "Fonction", fonction, marge+12, yi); yi -= 20;
            champ(cb, "Direction", direction, marge+12, yi);
            if (aUnite) {
                yi -= 20;
                champ(cb, "Division / Unité", unite, marge+12, yi);
            }
            y -= (hauteurBloc + 12);

            // ===== II - DEMANDE =====
            y = titreSection(cb, "II", "DEMANDE", marge, W, y);
            y -= 20;
            texte(cb, "Absence du", marge+12, y, normal(8.5f), GRIS);
            texte(cb, demande.getDateDebut() != null ? demande.getDateDebut().format(FDATE) : "—", marge+80, y, bold(9.5f), ENCRE);
            texte(cb, "au", marge+165, y, normal(8.5f), GRIS);
            texte(cb, demande.getDateFin() != null ? demande.getDateFin().format(FDATE) : "—", marge+185, y, bold(9.5f), ENCRE);
            texte(cb, "Durée", marge+300, y, normal(8.5f), GRIS);
            texte(cb, demande.getNombreJours() + " jour(s)", marge+340, y, bold(10f), VERT);
            y -= 22;
            champ(cb, "Motif", nz(demande.getMotif()).isEmpty() ? "—" : demande.getMotif(), marge+12, y);
            y -= 24;
            texte(cb, "Je certifie que l'absence que je demande est motivée par les raisons que j'ai citées.",
                    marge+12, y, italic(8f), GRIS);
            y -= 44;

            // ===== III - VISAS (depuis le circuit) =====
            y = titreSection(cb, "III", "VISAS", marge, W, y);
            y -= 18;
            float largeurBloc = (W - 2*marge - 20) / 3f;
            float x = marge;
            int compteur = 0;
            if (etapes != null) {
                for (EtapeValidation e : etapes) {
                    if (compteur >= 3) break;
                    dessinerVisa(cb, x, y, largeurBloc, e);
                    x += largeurBloc + 10;
                    compteur++;
                }
            }
            y -= 115;

            // ===== Signature (bas droite) =====
            texteDroite(cb, "Fait à Dakar, le " +
                (demande.getDateCreation() != null ? demande.getDateCreation().toLocalDate().format(FDATE) : ""),
                W-marge, y, normal(9f), ENCRE);
            y -= 16;
            texteDroite(cb, "L'intéressé(e)", W-marge, y, bold(9f), ENCRE);
            cb.setColorStroke(FILET); cb.setLineWidth(0.8f);
            cb.moveTo(W-marge-140, y-34); cb.lineTo(W-marge, y-34); cb.stroke();

            // ===== Pied =====
            texteCentre(cb, "Document généré par SGA · Système de Gestion des Absences — SEN-CSU",
                    W/2, 50, italic(7f), GRIS);

            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Erreur génération PDF : " + ex.getMessage());
        }
    }
    // ============ Helpers de dessin ============
    private BaseFont bf() {
        try { return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private Font bold(float s)  { return new Font(Font.HELVETICA, s, Font.BOLD); }
    private Font normal(float s){ return new Font(Font.HELVETICA, s, Font.NORMAL); }
    private Font italic(float s){ return new Font(Font.HELVETICA, s, Font.ITALIC); }

    private void texte(PdfContentByte cb, String t, float x, float y, Font f, Color col) {
        cb.beginText(); cb.setFontAndSize(bf(), f.getSize());
        cb.setColorFill(col);
        cb.setTextMatrix(x, y); cb.showText(t != null ? t : ""); cb.endText();
    }
    private void texteCentre(PdfContentByte cb, String t, float cx, float y, Font f, Color col) {
        cb.beginText(); cb.setFontAndSize(bf(), f.getSize());
        cb.setColorFill(col);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, t != null ? t : "", cx, y, 0); cb.endText();
    }
    private void texteDroite(PdfContentByte cb, String t, float xr, float y, Font f, Color col) {
        cb.beginText(); cb.setFontAndSize(bf(), f.getSize());
        cb.setColorFill(col);
        cb.showTextAligned(PdfContentByte.ALIGN_RIGHT, t != null ? t : "", xr, y, 0); cb.endText();
    }
    private void champ(PdfContentByte cb, String label, String valeur, float x, float y) {
        texte(cb, label, x, y, normal(8.5f), GRIS);
        texte(cb, valeur, x+95, y, bold(9.5f), ENCRE);
    }
    private float titreSection(PdfContentByte cb, String num, String txt, float marge, float W, float y) {
        cb.setColorFill(BANDEAU); cb.roundRectangle(marge, y-7, W-2*marge, 19, 3); cb.fill();
        cb.setColorFill(VERT); cb.rectangle(marge, y-7, 4, 19); cb.fill();
        texte(cb, num, marge+11, y, bold(9.5f), VERT);
        texte(cb, txt, marge+33, y, bold(9.5f), ENCRE);
        return y;
    }
    private void dessinerDrapeau(PdfContentByte cb, float x, float y, float w, float h) {
        float bw = w/3;
        cb.setColorFill(VERT);  cb.rectangle(x, y, bw, h); cb.fill();
        cb.setColorFill(JAUNE); cb.rectangle(x+bw, y, bw, h); cb.fill();
        cb.setColorFill(ROUGE); cb.rectangle(x+2*bw, y, bw, h); cb.fill();
        // étoile verte
        float cx = x+w/2, cy = y+h/2, r = Math.min(h, bw)*0.34f;
        cb.setColorFill(VERT);
        for (int i = 0; i < 10; i++) {
            double ang = Math.PI/2 + i*Math.PI/5;
            double rr = (i%2==0) ? r : r*0.4;
            float px = (float)(cx + rr*Math.cos(ang));
            float py = (float)(cy + rr*Math.sin(ang));
            if (i==0) cb.moveTo(px, py); else cb.lineTo(px, py);
        }
        cb.closePath(); cb.fill();
        cb.setColorStroke(FILET); cb.setLineWidth(0.6f);
        cb.rectangle(x, y, w, h); cb.stroke();
    }
    private void dessinerVisa(PdfContentByte cb, float x, float y, float w, EtapeValidation e) {
        float h = 75;
        cb.setColorFill(Color.WHITE); cb.roundRectangle(x, y-h, w, h, 3); cb.fill();
        cb.setColorStroke(FILET); cb.setLineWidth(0.8f); cb.roundRectangle(x, y-h, w, h, 3); cb.stroke();
        cb.setColorFill(GRIS_CLAIR); cb.roundRectangle(x, y-18, w, 18, 3); cb.fill();
        String lib = e.getLibelle() != null ? e.getLibelle() : "Étape";
        if (lib.length() > 32) lib = lib.substring(0, 32);
        texte(cb, lib, x+6, y-13, bold(7.3f), ENCRE);
        String date = (e.getDateAction() != null) ? e.getDateAction().toLocalDate().format(FDATE) : "—";
        texte(cb, "Date : " + date, x+6, y-32, normal(7.5f), GRIS);
        String st = e.getStatut() != null ? e.getStatut() : "";
        if ("VALIDEE".equals(st)) {
            texte(cb, "[X] APPROUVE", x+6, y-48, bold(8.5f), VERT);
        } else if ("REFUSEE".equals(st)) {
            texte(cb, "[X] REJETE", x+6, y-48, bold(8.5f), ROUGE);
        } else {
            texte(cb, "[ ] En attente", x+6, y-48, normal(8f), GRIS);
        }
        if (e.getCommentaire() != null && !e.getCommentaire().isEmpty()) {
            String obs = e.getCommentaire();
            if (obs.length() > 28) obs = obs.substring(0, 28);
            texte(cb, "Obs : " + obs, x+6, y-62, italic(7f), GRIS);
        }
    }
    private String nz(String s) { return s != null ? s : ""; }
}