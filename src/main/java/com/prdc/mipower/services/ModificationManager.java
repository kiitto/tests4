package com.prdc.mipower.services;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.prdc.mipower.models.ChangeRecord;

/**
 * Owns one Case Study's *active* pending list + undo/redo stack -- i.e.
 * exactly what {@link #applyToText} will write to disk on the next Save.
 * The actual text-rewriting algorithm lives in {@link ModifierEngine}
 * (package-private); this class is purely state management, kept separate
 * for single responsibility.
 *
 * <p>Each {@link com.prdc.mipower.models.CaseStudy} owns its own instance
 * of this class -- since it holds no shared/static state, N independent
 * Case Studies automatically means N independent pending lists and N
 * independent undo/redo stacks, with nothing to accidentally share between
 * them.
 */
public class ModificationManager {

    private final List<ChangeRecord> pending = new ArrayList<>();
    private final Deque<ChangeRecord> redoStack = new ArrayDeque<>();

    public void addChange(ChangeRecord change) {
        pending.add(change);
        redoStack.clear();
    }

    public void removeChange(int index) {
        if (index >= 0 && index < pending.size()) {
            pending.remove(index);
        }
    }

    public void clear() {
        pending.clear();
        redoStack.clear();
    }

    public List<ChangeRecord> getPending() {
        return new ArrayList<>(pending);
    }

    public int count() {
        return pending.size();
    }

    /** Removes the most recent change, returning it (or null if empty). */
    public ChangeRecord undo() {
        if (pending.isEmpty()) {
            return null;
        }
        ChangeRecord change = pending.remove(pending.size() - 1);
        redoStack.push(change);
        return change;
    }

    /** Re-applies the most recently undone change, returning it (or null). */
    public ChangeRecord redo() {
        if (redoStack.isEmpty()) {
            return null;
        }
        ChangeRecord change = redoStack.pop();
        pending.add(change);
        return change;
    }

    public boolean canUndo() {
        return !pending.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /** Result of applying every pending change to a file's original text. */
    public record ApplyResult(String text, int appliedCount) {
    }

    /**
     * Converts every pending ChangeRecord into a ModifierEngine.Modification
     * and applies them all in order against originalText. Always re-applies
     * against the ORIGINAL text (never against a previous result), so this
     * is idempotent -- calling it twice in a row with the same pending list
     * produces the same output both times.
     */
    public ApplyResult applyToText(String originalText) {
        List<ModifierEngine.Modification> modifications = new ArrayList<>();
        for (ChangeRecord c : pending) {
            modifications.add(toModification(c));
        }
        ModifierEngine.ModifyResult result = ModifierEngine.modifyInput(originalText, modifications);
        return new ApplyResult(result.text(), result.appliedCount());
    }

    private static ModifierEngine.Modification toModification(ChangeRecord c) {
        List<String> conditionList = null;
        if ("tabular".equals(c.formatType) || "two_row_table".equals(c.formatType)) {
        conditionList = new ArrayList<>();
        for (var entry : c.conditions.entrySet()) {
            conditionList.add(entry.getKey() + "=" + entry.getValue());
        }
    }
    Integer row = "two_row_table".equals(c.formatType) ? c.targetRow : null;
    return new ModifierEngine.Modification(c.section, c.field, c.newValue, c.formatType, conditionList, row);
    }
}
