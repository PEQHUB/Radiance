package com.radiance.client.util;

/**
 * Physically accurate Fresnel F0 presets for common metals and alloys.
 * Values sourced from:
 *   — Lagarde & de Rousiers, "Moving Frostbite to PBR", GDC 2014
 *   — Johnson & Christy 1972/1974 (J&C)
 *   — Querry, "Optical Constants of Minerals and Other Materials", 1985
 *   — SOPRA optical constants database
 *   — Inagaki, "Optical Properties of Liquid Mercury", 1974
 *
 * F0 is the normal-incidence Fresnel reflectance, computed from complex IOR:
 *   R(λ) = ((n−1)² + k²) / ((n+1)² + k²)
 * Stored as RGB permille (0–1000) at λ≈650 nm (R), 550 nm (G), 450 nm (B).
 *
 * Roughness is perceptual (0–100 %), squared in the shader for GGX α.
 * All presets assume a clean, polished surface. Oxidation/patina should be
 * modelled separately via roughness increase or F0 reduction.
 */
public enum MetalPreset {

    // ── Pure Elements ────────────────────────────────────────────────────────
    //                          name                       R    G    B  rough  source
    ALUMINUM    ("Aluminum",                              914, 922, 924,   5,  "Lagarde 2014 / J&C 1964"),
    BISMUTH     ("Bismuth",                               604, 627, 716,  35,  "SOPRA — base metal (blue F0 tint)"),
    CHROMIUM    ("Chromium",                              549, 556, 554,  15,  "Lagarde 2014"),
    COBALT      ("Cobalt",                                662, 655, 634,  20,  "Lagarde 2014"),
    COPPER      ("Copper",                                955, 637, 538,   5,  "J&C 1972"),
    GOLD        ("Gold",                                 1000, 766, 336,   5,  "J&C 1972"),
    IRIDIUM     ("Iridium",                               672, 646, 622,  10,  "Querry 1985"),
    IRON        ("Iron",                                  562, 565, 578,  15,  "J&C 1974"),
    LEAD        ("Lead",                                  619, 579, 534,  40,  "Querry 1985"),
    MERCURY     ("Mercury (liquid)",                      843, 839, 833,   1,  "Inagaki 1974 — liquid surface"),
    MOLYBDENUM  ("Molybdenum",                            558, 539, 513,  10,  "SOPRA database"),
    NICKEL      ("Nickel",                                660, 609, 526,  10,  "Lagarde 2014"),
    OSMIUM      ("Osmium",                                635, 617, 597,  10,  "Querry (estimated, Pt-group)"),
    PALLADIUM   ("Palladium",                             733, 697, 657,  10,  "Querry 1985"),
    PLATINUM    ("Platinum",                              672, 637, 585,   5,  "Lagarde 2014"),
    RHODIUM     ("Rhodium",                               754, 725, 718,   5,  "Lagarde / Querry 1985"),
    SILVER      ("Silver",                                972, 960, 915,   3,  "Lagarde 2014 / J&C 1972"),
    TANTALUM    ("Tantalum",                              581, 548, 502,  15,  "Querry 1985"),
    TIN         ("Tin",                                   626, 533, 482,  25,  "Querry 1985"),
    TITANIUM    ("Titanium",                              542, 497, 449,  10,  "Lagarde 2014"),
    TUNGSTEN    ("Tungsten",                              504, 479, 429,  15,  "Lagarde 2014"),
    VANADIUM    ("Vanadium",                              491, 454, 424,  12,  "SOPRA database"),
    ZINC        ("Zinc",                                  697, 680, 665,  10,  "SOPRA / RefractiveIndex.info"),
    ZIRCONIUM   ("Zirconium",                             540, 513, 487,  15,  "Querry 1985"),

    // ── Alloys ───────────────────────────────────────────────────────────────
    BRASS       ("Brass (Cu-Zn 70/30)",                   888, 707, 456,  10,  "Weighted Cu+Zn optical blend"),
    BRONZE      ("Bronze (Cu-Sn 80/20)",                  820, 660, 480,  20,  "Weighted Cu+Sn optical blend"),
    CAST_IRON   ("Cast Iron",                             520, 520, 535,  55,  "Fe + ~3% C, from J&C iron"),
    GUNMETAL    ("Gunmetal (Cu-Sn-Zn 88/10/2)",           800, 640, 465,  25,  "Weighted composition blend"),
    PEWTER      ("Pewter (Sn-Cu-Sb 91/7/2)",              555, 528, 505,  35,  "Modern pewter composition"),
    STEEL       ("Steel (mild)",                          565, 566, 578,  15,  "Fe + <1% C, near J&C iron"),
    STAINLESS   ("Stainless Steel (304)",                 574, 570, 566,  10,  "18% Cr / 8% Ni / Fe alloy");

    // ─────────────────────────────────────────────────────────────────────────

    private final String displayName;
    /** F0 reflectance in permille (0–1000) at R≈650nm, G≈550nm, B≈450nm. */
    private final int f0R, f0G, f0B;
    /** Perceptual roughness in percent (0–100). Squared in the shader for GGX α. */
    private final int roughness;
    /** Attribution string — shown nowhere in UI but documents the data source. */
    private final String source;

    MetalPreset(String displayName, int f0R, int f0G, int f0B, int roughness, String source) {
        this.displayName = displayName;
        this.f0R = f0R;
        this.f0G = f0G;
        this.f0B = f0B;
        this.roughness = roughness;
        this.source = source;
    }

    public String getDisplayName() { return displayName; }
    public int getF0R()            { return f0R; }
    public int getF0G()            { return f0G; }
    public int getF0B()            { return f0B; }
    public int getRoughness()      { return roughness; }
    public String getSource()      { return source; }

    /**
     * Short summary for the UI, e.g.:
     *   "Silver  R:97.2  G:96.0  B:91.5  Rough:3%"
     */
    public String getSummary() {
        return String.format("%s  R:%.1f  G:%.1f  B:%.1f  Rough:%d%%",
            displayName, f0R / 10.0, f0G / 10.0, f0B / 10.0, roughness);
    }
}
