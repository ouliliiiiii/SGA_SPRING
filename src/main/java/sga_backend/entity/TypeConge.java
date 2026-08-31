    package sga_backend.entity;

    import jakarta.persistence.Column;
    import jakarta.persistence.Entity;
    import jakarta.persistence.GeneratedValue;
    import jakarta.persistence.GenerationType;
    import jakarta.persistence.Id;
    import jakarta.persistence.Table;
    import lombok.Data;

    @Entity
    @Table(name = "type_conge")
    @Data
    public class TypeConge {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        // Libellé affiché (ex. "Congé annuel", "Mariage")
        @Column(nullable = false, length = 100)
        private String libelle;

        // Code technique (ex. ANNUEL, MARIAGE, VEUVAGE, DECES, BAPTEME, MATERNITE, PATERNITE, MALADIE, SANS_SOLDE)
        @Column(nullable = false, unique = true, length = 30)
        private String code;

        // Catégorie : DEDUCTIBLE, NON_DEDUCTIBLE, SANS_SOLDE
        // - DEDUCTIBLE     : retire des jours du solde annuel
        // - NON_DEDUCTIBLE : congé autorisé/payé sans entamer le solde (mariage, décès...)
        // - SANS_SOLDE     : période non payée, ne touche pas au solde
        @Column(nullable = false, length = 20)
        private String categorie;

        // Faut-il joindre un justificatif ?
        @Column(nullable = false)
        private Boolean justificatifRequis = false;

        // Plafond légal en jours (null = pas de plafond ; à remplir plus tard type par type)
        private Integer dureeMaximale;

        // Réservé aux femmes (ex. maternité)
        @Column(nullable = false)
        private Boolean reserveFemme = false;

        // true = compté en jours ouvrés (hors week-ends/fériés) ; false = jours calendaires
        @Column(nullable = false)
        private Boolean compteJoursOuvres = true;

        // Type actif (visible dans le formulaire) ou désactivé
        @Column(nullable = false)
        private Boolean actif = true;
    }