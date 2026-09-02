package com.prdc.mipower.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.prdc.mipower.models.CaseStudy;
import com.prdc.mipower.parser.DatParser;
import com.prdc.mipower.utils.Constants;
import com.prdc.mipower.utils.FileUtils;

/**
 * Owns the Base/Reference File and every Case Study described in the spec.
 * Case Studies still have a parent/child relationship (a child's
 * {@code hierarchicalId} like "1_2" shows which Case Study it's under),
 * but that relationship is metadata only -- callers and the UI both work
 * with the flat, linear list from {@link #allCaseStudies()} rather than
 * walking a nested tree, e.g.:
 *
 * <pre>
 * Base File
 * Case Study 1
 * Case Study 1_1
 * Case Study 1_2
 * Case Study 2
 * Case Study 3
 * </pre>
 *
 * <p><b>The Base File is never modified.</b> Every Case Study's "resolved
 * text" -- what its section/record/field data actually looks like right
 * now -- is computed on demand by starting from the Base File's raw text
 * (or, for a child Case Study, its parent's resolved text) and replaying
 * only that one Case Study's own active {@code ChangeRecord}s on top via
 * {@link com.prdc.mipower.services.ModificationManager#applyToText}. That
 * single mechanism is what gives the whole tree its inheritance semantics
 * for free: a child always sees everything its parent has, plus its own
 * edits, and nothing a sibling did.
 *
 * <p>This class is pure orchestration/state -- it does not touch JavaFX,
 * and it does not itself decide field-level Modified/Inherited/Unchanged
 * status (that's {@link ChangeResolver}'s job, working off the same
 * Case Study objects this class manages).
 */
public class CaseStudyManager {

    private String baseFilePath;
    private String baseRawText;
    /** Read-only structural reference (section names, study type) -- NEVER edited, never saved over. */
    private DatParser baseParser;

    /** Result of running MiPower directly on the Base File, if it's been run. Shared by every root Case Study. */
    public String baseOut0;

    private final Map<Integer, CaseStudy> byId = new LinkedHashMap<>();
    private final List<Integer> rootOrder = new ArrayList<>();
    private int nextId = 1;
    private int nextRootNumber = 1;

    // --------------------------------------------------------------------- //
    // Base File
    // --------------------------------------------------------------------- //
    public void loadBaseFile(String path) throws IOException {
        this.baseFilePath = path;
        this.baseRawText = FileUtils.readText(path);
        DatParser parser = new DatParser();
        parser.loadText(baseRawText);
        parser.parse();
        this.baseParser = parser;

        byId.clear();
        rootOrder.clear();
        nextId = 1;
        nextRootNumber = 1;
        baseOut0 = null;
    }

    public boolean hasBaseFile() {
        return baseFilePath != null;
    }

    public String getBaseFilePath() {
        return baseFilePath;
    }

    public String getBaseRawText() {
        return baseRawText;
    }

    /** Read-only. Callers must never mutate the records returned from this parser. */
    public DatParser getBaseParser() {
        return baseParser;
    }

    // --------------------------------------------------------------------- //
    // Tree creation
    // --------------------------------------------------------------------- //

    /**
     * Creates a new root-level Case Study, referencing the Base File
     * directly. Corresponds to "New Case -> Create From: Base File" when
     * nothing is currently selected, or when the user explicitly chooses
     * Base File as the source.
     */
    public CaseStudy createRootCaseStudy(String displayName) throws IOException {
        String hid = String.valueOf(nextRootNumber++);
        CaseStudy cs = buildCaseStudy(null, hid, displayName, baseRawText);
        rootOrder.add(cs.id);
        return cs;
    }

    /**
     * Creates a child Case Study of {@code parent}, referencing the
     * parent's CURRENT resolved data (Base File + every ancestor's changes
     * + the parent's own changes), never the Base File directly.
     */
    public CaseStudy createChildCaseStudy(CaseStudy parent, String displayName) throws IOException {
        if (parent == null) {
            return createRootCaseStudy(displayName);
        }
        String hid = parent.hierarchicalId + "_" + (parent.childIds.size() + 1);
        String parentResolvedText = resolveText(parent);
        CaseStudy cs = buildCaseStudy(parent.id, hid, displayName, parentResolvedText);
        // The new Case Study's "original input" is whatever the PARENT actually
        // resolves to right now (its own saved .dat0 if it has one, otherwise
        // still the Base File) -- never unconditionally the Base File itself,
        // or picking a deep Case Study as the reference would silently lose
        // everything it inherited/changed. See buildCaseStudy()'s default.
        cs.originalDat0 = (parent.outputFile != null) ? parent.outputFile : baseFilePath;
        parent.childIds.add(cs.id);
        return cs;
    }

    /** "New Case" dispatch matching the spec's "Create From: Base File / Current Case Study" choice. */
    public CaseStudy createCaseStudy(CaseStudy currentSelection, boolean fromBaseFile, String displayName)
            throws IOException {
        if (fromBaseFile || currentSelection == null) {
            return createRootCaseStudy(displayName);
        }
        return createChildCaseStudy(currentSelection, displayName);
    }

    private CaseStudy buildCaseStudy(Integer parentId, String hierarchicalId, String displayName, String originText)
            throws IOException {
        CaseStudy cs = new CaseStudy(nextId++, (displayName != null && !displayName.isBlank())
                ? displayName : ("Case Study " + hierarchicalId));
        cs.parentId = parentId;
        cs.hierarchicalId = hierarchicalId;
        DatParser parser = new DatParser();
        parser.loadText(originText);
        parser.parse();
        cs.parser = parser;
        cs.originalDat0 = baseFilePath;
        byId.put(cs.id, cs);
        return cs;
    }

    // --------------------------------------------------------------------- //
    // Rename / duplicate / delete
    // --------------------------------------------------------------------- //

    /** Renames the DISPLAY name only. hierarchicalId (and every parent/child link) is untouched. */
    public boolean rename(CaseStudy cs, String newName) {
        String trimmed = (newName == null) ? "" : newName.strip();
        if (trimmed.isEmpty()) {
            return false;
        }
        for (CaseStudy other : byId.values()) {
            if (other != cs && other.name.equalsIgnoreCase(trimmed)) {
                return false;
            }
        }
        cs.name = trimmed;
        return true;
    }

    /**
     * Duplicates a Case Study as a NEW SIBLING (same parent), replaying only
     * that Case Study's own local changes (not the whole resolved text --
     * those are recomputed fresh from the shared parent) onto a fresh parse
     * of the parent's current resolved text. A change is skipped, never
     * guessed, if its conditions no longer identify exactly one row.
     */
    public DuplicateResult duplicate(CaseStudy source, String displayName) throws IOException {
        CaseStudy parent = (source.parentId == null) ? null : getById(source.parentId);
        String originText = (parent == null) ? baseRawText : resolveText(parent);
        String newHid = (parent == null)
                ? String.valueOf(nextRootNumber++)
                : (parent.hierarchicalId + "_" + (parent.childIds.size() + 1));

        CaseStudy copy = buildCaseStudy(source.parentId, newHid, displayName, originText);
        copy.originalDat0 = (parent != null && parent.outputFile != null) ? parent.outputFile : baseFilePath;
        if (parent != null) {
            parent.childIds.add(copy.id);
        } else {
            rootOrder.add(copy.id);
        }

        int replayed = 0;
        int skipped = 0;
        for (var change : source.modManager.getPending()) {
            var section = copy.parser.getSection(change.section);
            if (section == null) {
                skipped++;
                continue;
            }
            var matches = section.recordsWithCondition(change.conditions);
            if (matches.size() != 1) {
                skipped++;
                continue;
            }
            String fieldToSet = (change.field != null) ? change.field : "Value";
            matches.get(0).fields.put(fieldToSet, change.newValue);
            copy.modManager.addChange(change);
            copy.addHistoryEntry(change);
            replayed++;
        }
        return new DuplicateResult(copy, replayed, skipped);
    }

    public record DuplicateResult(CaseStudy caseStudy, int replayed, int skipped) {
    }

    /** Deletes a Case Study AND every descendant, recursively. Returns every id removed. */
    public List<Integer> delete(CaseStudy cs) {
        List<Integer> removed = new ArrayList<>();
        deleteRecursive(cs.id, removed);
        if (cs.parentId == null) {
            rootOrder.remove((Integer) cs.id);
        } else {
            CaseStudy parent = getById(cs.parentId);
            if (parent != null) {
                parent.childIds.remove((Integer) cs.id);
            }
        }
        return removed;
    }

    private void deleteRecursive(int id, List<Integer> removed) {
        CaseStudy cs = byId.get(id);
        if (cs == null) {
            return;
        }
        for (int childId : new ArrayList<>(cs.childIds)) {
            deleteRecursive(childId, removed);
        }
        byId.remove(id);
        removed.add(id);
    }

    // --------------------------------------------------------------------- //
    // Resolution / lookup
    // --------------------------------------------------------------------- //

    public CaseStudy getById(int id) {
        return byId.get(id);
    }

    public CaseStudy getParent(CaseStudy cs) {
        return (cs == null || cs.parentId == null) ? null : byId.get(cs.parentId);
    }

    /** "Base File (Reference)" for a root Case Study, or the parent's display name for a child. */
    public String referenceLabel(CaseStudy cs) {
        CaseStudy parent = getParent(cs);
        return (parent == null) ? Constants.BASE_FILE_LABEL : parent.name;
    }

    public List<CaseStudy> rootCaseStudies() {
        List<CaseStudy> out = new ArrayList<>();
        for (int id : rootOrder) {
            CaseStudy cs = byId.get(id);
            if (cs != null) {
                out.add(cs);
            }
        }
        return out;
    }

    public List<CaseStudy> childrenOf(CaseStudy cs) {
        List<CaseStudy> out = new ArrayList<>();
        if (cs == null) {
            return out;
        }
        for (int id : cs.childIds) {
            CaseStudy child = byId.get(id);
            if (child != null) {
                out.add(child);
            }
        }
        return out;
    }

    /** Every Case Study in the tree, flat, in creation order -- for Analytics/Run History windows. */
    public List<CaseStudy> allCaseStudies() {
        return new ArrayList<>(byId.values());
    }

    /**
     * The full ancestor chain from the Base File down to (but NOT
     * including) {@code cs} -- e.g. for "1_2" this returns [Case Study 1].
     * Empty for a root Case Study.
     */
    public List<CaseStudy> ancestorChain(CaseStudy cs) {
        List<CaseStudy> chain = new ArrayList<>();
        CaseStudy cur = getParent(cs);
        while (cur != null) {
            chain.add(0, cur);
            cur = getParent(cur);
        }
        return chain;
    }

    /**
     * Computes what {@code cs}'s data actually looks like right now: the
     * Base File's raw text, with every ancestor's active changes applied
     * in order (oldest ancestor first), then {@code cs}'s own active
     * changes applied last. Always recomputed from scratch (never cached
     * against a stale intermediate), so it's always correct even if an
     * ancestor's pending list changed after {@code cs} was created.
     */
    public String resolveText(CaseStudy cs) {
        String text = baseRawText;
        for (CaseStudy ancestor : ancestorChain(cs)) {
            text = ancestor.modManager.applyToText(text).text();
        }
        return cs.modManager.applyToText(text).text();
    }

    /** Same as {@link #resolveText}, but stops one level short -- i.e. what {@code cs}'s REFERENCE looks like. */
    public String resolveReferenceText(CaseStudy cs) {
        CaseStudy parent = getParent(cs);
        if (parent == null) {
            return baseRawText;
        }
        return resolveText(parent);
    }
}
