package com.prdc.mipower.utils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared, immutable configuration used across the app. Centralizing these
 * here (rather than scattering literals through parser/services/GUI code)
 * is what lets, for example, {@code STUDY_TYPE_KEYWORDS} be hand-tuned in
 * exactly one place if a real file's terminology differs.
 */
public final class Constants {

    private Constants() {
    }

    // --------------------------------------------------------------------- //
    // Parser: default section order (display order only -- sections not in
    // this list are still detected and appended automatically; nothing
    // about column layout is ever hardcoded).
    // --------------------------------------------------------------------- //
    public static final List<String> DEFAULT_SECTION_ORDER = List.of(
            "System Specifications",
            "Common Control Options",
            "Cost Factors",
            "Zone wise Multiplication Factors",
            "Area Numbers",
            "Bus Data",
            "Transmission Line",
            "Generator Data",
            "LOAD DATA",
            "Load Characteristic Data",
            "Generator Frequency Characteristics",
            "Slack Bus Angle"
    );

    // --------------------------------------------------------------------- //
    // Study type detection (best-effort heuristic -- see DatParser.detectStudyType()
    // Javadoc for exactly what this is and isn't).
    // --------------------------------------------------------------------- //
    public static final Map<String, String> STUDY_TYPE_LABELS = Map.of(
            "LFA", "Load Flow Analysis (LFA)",
            "TRS", "Transient Stability (TRS)",
            "VFA", "Voltage Fault Analysis (VFA)",
            "SCA", "Short Circuit Analysis (SCA)",
            "OPF", "Optimal Power Flow (OPF)"
    );
    public static final String STUDY_UNKNOWN = "Unknown Study";

    public static final Map<String, List<String>> STUDY_TYPE_KEYWORDS = Map.of(
            "TRS", List.of("transient stability", "swing curve", "fault clearing time",
                    "machine dynamic data", "transient stability analysis", " trs "),
            "SCA", List.of("short circuit analysis", "short circuit", "fault level",
                    "fault mva", "symmetrical fault", " sca "),
            "VFA", List.of("voltage fault analysis", "voltage fault", " vfa "),
            "OPF", List.of("optimal power flow", "objective function", " opf "),
            "LFA", List.of("load flow analysis", "load flow", " lfa ")
    );
    /** Checked in this priority order -- most-specific/least-likely-to-appear-in-passing first. */
    public static final List<String> STUDY_TYPE_PRIORITY = List.of("TRS", "SCA", "VFA", "OPF", "LFA");

    /** Sections essentially unique to a standard Load Flow case (fallback signature). */
    public static final Set<String> LFA_SIGNATURE_SECTIONS = Set.of(
            "Slack Bus Angle", "Generator Frequency Characteristics",
            "Common Control Options", "System Specifications"
    );

    // --------------------------------------------------------------------- //
    // Editable-column heuristic (ValidationUtils / editing services).
    // --------------------------------------------------------------------- //
    public static final Set<String> MOSTLY_READONLY_SECTIONS =
            Set.of("System Specifications", "Common Control Options");
    public static final Set<String> FULLY_EDITABLE_SECTIONS = Set.of("Slack Bus Angle");
    public static final Set<String> EXPLICIT_KEY_FIELDS = Set.of("frombus", "tobus", "area", "zone");

    // --------------------------------------------------------------------- //
    // Run MiPower
    // --------------------------------------------------------------------- //
    public static final String MIPOWER_EXE = "C:\\MiPower10_1\\powerlfa.exe";
    public static final String MIPOWER_HEADER_TOKEN = "+++PowerLFA-R-Dec-1998+++";

    // --------------------------------------------------------------------- //
    // Change status vocabulary (Change History: Pending/Saved/Undone/Deleted)
    // --------------------------------------------------------------------- //
    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_SAVED = "Saved";
    public static final String STATUS_UNDONE = "Undone";
    public static final String STATUS_DELETED = "Deleted";

    // --------------------------------------------------------------------- //
    // Record/field-level change status vocabulary (hierarchical Case Study
    // tree -- see ChangeResolver). Distinct from the above, which describes
    // the lifecycle of one ChangeRecord; these describe, from the point of
    // view of the CURRENTLY SELECTED Case Study, why a field/record differs
    // (or doesn't) from the Base File.
    // --------------------------------------------------------------------- //
    public static final String FIELD_UNCHANGED = "Unchanged";
    public static final String FIELD_INHERITED = "Inherited";
    public static final String FIELD_LOCAL_CHANGE = "Local Change";
    public static final String FIELD_MODIFIED = "Modified";

    public static final String COLOR_STATUS_UNCHANGED = "#9CA3AF";   // grey / white circle
    public static final String COLOR_STATUS_INHERITED = "#3B82F6";   // blue
    public static final String COLOR_STATUS_LOCAL = "#10B981";       // green
    public static final String COLOR_STATUS_MODIFIED = "#F59E0B";    // amber/yellow

    public static final String BASE_FILE_LABEL = "Base File";

    // --------------------------------------------------------------------- //
    // UI palette -- also mirrored in resources/css/theme.css; kept here too
    // so non-CSS-driven drawing (e.g. chart series colors) can reference
    // the same values without duplicating hex literals.
    // --------------------------------------------------------------------- //
    public static final String COLOR_BG = "#F4F7FC";
    public static final String COLOR_CARD = "#FFFFFF";
    public static final String COLOR_TOOLBAR = "#1E3A8A";
    public static final String COLOR_SIDEBAR = "#2563EB";
    public static final String COLOR_ACCENT = "#3B82F6";
    public static final String COLOR_ACCENT_HOVER = "#2563EB";
    public static final String COLOR_SUCCESS = "#10B981";
    public static final String COLOR_WARNING = "#F59E0B";
    public static final String COLOR_DANGER = "#EF4444";
    public static final String COLOR_TEXT = "#1F2937";
    public static final String COLOR_TEXT_MUTED = "#6B7280";
    public static final String COLOR_BORDER = "#D1D5DB";

    // --------------------------------------------------------------------- //
    // Analytics thresholds -- collected here
    // so "what counts as a real change" judgment calls live in one place.
    // --------------------------------------------------------------------- //
    public static final double VOLTAGE_CHANGE_THRESHOLD_PU = 0.002;
    public static final double LOADING_CHANGE_THRESHOLD_PCT = 0.5;
    public static final double OVERLOAD_THRESHOLD_PCT = 100.0;
    public static final double HIGH_LOADING_THRESHOLD_PCT = 75.0;
}
