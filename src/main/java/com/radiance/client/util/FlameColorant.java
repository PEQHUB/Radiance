package com.radiance.client.util;

/**
 * Real-world spectral emission lines for flame / discharge sources.
 * Sources: NIST Atomic Spectra Database, CRC Handbook of Chemistry and Physics.
 * Entries are ordered by dominant wavelength (nm) for readability.
 * Used for the material dropdown in the emission settings UI.
 */
public enum FlameColorant {
    NONE("None", 0),

    // ── Violet (380–430 nm) ───────────────────────────────────────────────────
    MANGANESE        ("Manganese",            403),  // Mn I 403.1 nm — notable in flame/AAS
    MERCURY_VIOLET   ("Mercury (violet)",     405),  // Hg I 404.7 nm — mercury discharge
    LEAD             ("Lead",                 406),  // Pb I 405.8 nm — lead flame test
    HYDROGEN_DELTA   ("Hydrogen (H-δ)",       410),  // Balmer H-δ 410.2 nm
    SELENIUM         ("Selenium",             417),  // Se I 416.7 nm — selenium arc
    // ── Blue-Violet (430–460 nm) ──────────────────────────────────────────────
    HYDROGEN_GAMMA   ("Hydrogen (H-γ)",       434),  // Balmer H-γ 434.0 nm
    MERCURY_BLUE     ("Mercury (blue)",       436),  // Hg I 435.8 nm — mercury discharge
    HELIUM_BLUE      ("Helium",               447),  // He I 447.1 nm — helium discharge
    INDIUM           ("Indium",               451),  // In I 451.1 nm — characteristic blue
    TIN              ("Tin",                  452),  // Sn I 452.5 nm — tin arc
    SULFUR           ("Sulfur",               460),  // S2 band ~460 nm — visible blue of sulfur flame
    STRONTIUM_BLUE   ("Strontium (blue)",     461),  // Sr I 460.7 nm — strontium discharge
    // ── Blue (465–490 nm) ────────────────────────────────────────────────────
    ZINC_BLUE        ("Zinc (blue)",          472),  // Zn I 472.2 nm — zinc arc
    ZINC_CYAN        ("Zinc (cyan)",          481),  // Zn I 481.1 nm — zinc arc
    HYDROGEN_BETA    ("Hydrogen (H-β)",       486),  // Balmer H-β 486.1 nm
    // ── Green (490–560 nm) ───────────────────────────────────────────────────
    COPPER           ("Copper",               510),  // Cu I 510.6 nm — classic green flame test
    MAGNESIUM        ("Magnesium",            518),  // Mg I triplet 516.7/517.3/518.4 nm
    PHOSPHORUS       ("Phosphorus",           525),  // P2 band ~525 nm — phosphorus flame
    THALLIUM         ("Thallium",             535),  // Tl I 535.0 nm — distinctive green
    MERCURY_GREEN    ("Mercury (green)",      546),  // Hg I 546.1 nm — mercury discharge
    BARIUM           ("Barium",               554),  // Ba I 553.6 nm — barium flame test
    // ── Yellow (560–590 nm) ──────────────────────────────────────────────────
    MERCURY_YELLOW   ("Mercury (yellow)",     578),  // Hg I 576.9 / 579.1 nm doublet
    SODIUM           ("Sodium",               589),  // Na I D-line doublet 589.0/589.6 nm
    // ── Orange (590–630 nm) ──────────────────────────────────────────────────
    STRONTIUM        ("Strontium",            606),  // Sr I 606.0 nm — strontium flame test
    NEON_ORANGE      ("Neon (orange)",        614),  // Ne I 614.3 nm — neon discharge
    CALCIUM          ("Calcium",              622),  // Ca I 616.2/619.1/622.0 nm — calcium flame
    // ── Red (630–680 nm) ─────────────────────────────────────────────────────
    NEON_RED         ("Neon (red)",           633),  // Ne I 632.8 nm — neon discharge (HeNe laser)
    HYDROGEN_ALPHA   ("Hydrogen (H-α)",       656),  // Balmer H-α 656.3 nm — most visible Balmer line
    LITHIUM          ("Lithium",              670),  // Li I 670.8 nm — lithium flame test
    // ── Deep Red (690–780 nm) ─────────────────────────────────────────────────
    NEON_DEEP_RED    ("Neon (deep red)",      703),  // Ne I 703.2 nm — neon discharge
    POTASSIUM        ("Potassium",            766),  // K I 766.5 nm — potassium flame test
    RUBIDIUM         ("Rubidium",             780),  // Rb I 780.0 nm — rubidium flame test

    CUSTOM("Custom", -1);

    private final String displayName;
    private final int wavelengthNm;

    FlameColorant(String displayName, int wavelengthNm) {
        this.displayName = displayName;
        this.wavelengthNm = wavelengthNm;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWavelengthNm() {
        return wavelengthNm;
    }

    public String getLabel() {
        if (this == NONE) return "None";
        if (this == CUSTOM) return "Custom";
        return displayName + " (" + wavelengthNm + "nm)";
    }

    /**
     * Find the FlameColorant matching a wavelength, or CUSTOM if no match.
     */
    public static FlameColorant fromWavelength(int nm) {
        if (nm <= 0) return NONE;
        for (FlameColorant c : values()) {
            if (c != CUSTOM && c != NONE && c.wavelengthNm == nm) return c;
        }
        return CUSTOM;
    }
}
