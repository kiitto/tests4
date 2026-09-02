package com.prdc.mipower.models;

import com.prdc.mipower.utils.Constants;

/**
 * One row of a Case Study's append-only Change History: a {@link ChangeRecord}
 * plus a mutable {@code status} (Pending/Saved/Undone/Deleted -- see
 * {@link Constants}). ChangeRecord itself carries no notion of status; this
 * class is the thin wrapper that adds it, so the underlying edit fact never
 * needs to change once created, only how it's currently being treated.
 */
public class HistoryEntry {

    private final ChangeRecord change;
    private final String caseStudyName;
    private String status;

    public HistoryEntry(ChangeRecord change, String caseStudyName) {
        this(change, caseStudyName, Constants.STATUS_PENDING);
    }

    public HistoryEntry(ChangeRecord change, String caseStudyName, String status) {
        this.change = change;
        this.caseStudyName = caseStudyName;
        this.status = status;
    }

    public ChangeRecord getChange() {
        return change;
    }

    public String getCaseStudyName() {
        return caseStudyName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isActive() {
        return Constants.STATUS_PENDING.equals(status) || Constants.STATUS_SAVED.equals(status);
    }

    // ---- convenience passthroughs, so callers rarely need getChange() ---- //
    public String getSection() {
        return change.section;
    }

    public String getField() {
        return change.field;
    }

    public String getOldValue() {
        return change.oldValue;
    }

    public String getNewValue() {
        return change.newValue;
    }

    public String getTimestamp() {
        return change.timestamp;
    }

    public String getRecordLabel() {
        return change.recordLabel;
    }
}
