package com.prdc.mipower.models;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.prdc.mipower.parser.DatParser;
import com.prdc.mipower.services.ModificationManager;
import com.prdc.mipower.utils.Constants;

/**
 * One independent Case Study. Owns, completely independently of every
 * other Case Study:
 * <ul>
 *   <li>its own {@link DatParser} (its own parsed record graph)</li>
 *   <li>its own {@link ModificationManager} (the *active* pending list +
 *       undo/redo stack)</li>
 *   <li>its own append-only Change History ({@link #history}, list of
 *       {@link HistoryEntry} -- Pending/Saved/Undone/Deleted)</li>
 *   <li>its own output file, last-saved snapshot, and report path</li>
 *   <li>its own MiPower {@link #runHistory}</li>
 *   <li>its own original/modified .dat0/.out0 paths for the Comparison
 *       Dashboard -- filled in automatically as Run MiPower and Save are
 *       used, never browsed for by the user</li>
 * </ul>
 *
 * <p>This class is deliberately NOT pure data (unlike most of the
 * {@code models} package) -- it actively owns and orchestrates a
 * {@link ModificationManager}, which is why it was deferred to module 2
 * rather than built alongside the pure-data models in module 1.
 */
public class CaseStudy {

    public final int id;
    public String name;
    public DatParser parser = new DatParser();
    public final ModificationManager modManager = new ModificationManager();

    // --------------------------------------------------------------------- //
    // Hierarchy (Part 1 of the spec): every Case Study except a root one
    // references either the Base File directly (parentId == null) or
    // another Case Study (its "reference case"). hierarchicalId is the
    // permanent structural label ("1", "1_1", "1_2", "2", ...) used for
    // file naming and tree ordering; it never changes even if the user
    // renames the Case Study's display name. Owned/maintained exclusively
    // by CaseStudyManager -- nothing else should mutate these fields.
    // --------------------------------------------------------------------- //
    /** Null means this Case Study's reference is the Base File itself. */
    public Integer parentId;
    /** Permanent structural label, e.g. "1", "1_1", "1_2", "2". Never renamed. */
    public String hierarchicalId;
    public final List<Integer> childIds = new ArrayList<>();

    /** Append-only. Statuses change (setStatus on the entry); entries are never removed. */
    public final List<HistoryEntry> history = new ArrayList<>();
    /** Append-only. Every MiPower execution attempt for this Case Study. */
    public final List<RunResult> runHistory = new ArrayList<>();

    public String outputFile;
    public List<ChangeRecord> lastAppliedChanges;
    public String lastSavedAt;
    public String reportPath;
    public String latestOut0Path;

    // Comparison Dashboard storage -- filled in automatically by whichever
    // service handles Run MiPower results (see the GUI module), never
    // browsed for by the user. originalDat0 is set once, at construction,
    // to whatever the input file was at that moment.
    public String originalDat0;
    public String originalOut0;
    public String modifiedOut0;

    public CaseStudy(int id, String name) {
        this.id = id;
        this.name = (name != null && !name.isBlank()) ? name : ("Case Study " + id);
    }

    /** Alias for outputFile -- kept as its own accessor so the public shape
     * matches originalDat0/originalOut0/modifiedDat0/modifiedOut0/report/
     * runHistory exactly, without a second piece of mutable state to keep
     * in sync. */
    public String getModifiedDat0() {
        return outputFile;
    }

    /** Alias for reportPath -- see getModifiedDat0() above. */
    public String getReport() {
        return reportPath;
    }

    public boolean hasComparisonData() {
        return originalOut0 != null && modifiedOut0 != null
                && new File(originalOut0).exists() && new File(modifiedOut0).exists();
    }

    /** Filesystem-safe identifier for this Case Study, used in output filenames.
     *  Uses the permanent hierarchicalId (e.g. "Case1_1") rather than the
     *  renameable display name, so sibling/child files never collide and
     *  a rename never orphans previously-saved files. */
    public String slug() {
        return "Case" + (hierarchicalId != null ? hierarchicalId : String.valueOf(id));
    }

    public boolean isRoot() {
        return parentId == null;
    }

    public void addHistoryEntry(ChangeRecord change) {
        history.add(new HistoryEntry(change, name));
    }

    public void recordRun(RunResult result) {
        runHistory.add(result);
    }

    public StatusCounts statusCounts() {
        int pending = 0;
        int saved = 0;
        int undone = 0;
        int deleted = 0;
        for (HistoryEntry e : history) {
            switch (e.getStatus()) {
                case Constants.STATUS_PENDING -> pending++;
                case Constants.STATUS_SAVED -> saved++;
                case Constants.STATUS_UNDONE -> undone++;
                case Constants.STATUS_DELETED -> deleted++;
                default -> {
                }
            }
        }
        return new StatusCounts(pending, saved, undone, deleted);
    }

    public record StatusCounts(int pending, int saved, int undone, int deleted) {
        public int total() {
            return pending + saved + undone + deleted;
        }
    }
}
