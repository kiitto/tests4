package com.prdc.mipower.gui;

import java.util.ArrayList;
import java.util.List;

import com.prdc.mipower.models.CaseStudy;
import com.prdc.mipower.services.CaseStudyManager;

/**
 * Builds a flat, linear list of every Case Study for display -- no nested
 * tree. The Base File always comes first, followed by every saved Case
 * Study in creation order, e.g.:
 *
 * <pre>
 * Base File (Reference)
 * Case Study 1
 * Case Study 1_1
 * Case Study 1_2
 * Case Study 2
 * Case Study 3
 * </pre>
 *
 * <p>Every entry's value is a {@link Node}: either the synthetic Base File
 * entry ({@link Node#isBase} true, {@link Node#caseStudy} null) or a real
 * {@link CaseStudy}. Each Case Study entry also carries its direct parent
 * (or {@code null} for a top-level Case Study) so the relationship is
 * still visible in the label even though the list itself is not nested.
 */
public final class CaseStudyTreeModel {

    private CaseStudyTreeModel() {
    }

    public static final class Node {
        public final boolean isBase;
        public final CaseStudy caseStudy;
        public final CaseStudy parent;

        private Node(boolean isBase, CaseStudy caseStudy, CaseStudy parent) {
            this.isBase = isBase;
            this.caseStudy = caseStudy;
            this.parent = parent;
        }

        static Node base() {
            return new Node(true, null, null);
        }

        static Node of(CaseStudy cs, CaseStudy parent) {
            return new Node(false, cs, parent);
        }

        @Override
        public String toString() {
            if (isBase) {
                return "\uD83D\uDCC1 Base File";
            }
            String refName = (parent != null) ? parent.name : "Base File";
            return "\uD83D\uDCCB " + caseStudy.name + "  (Selected reference: " + refName + ")";
        }
    }

    /**
     * Rebuilds the whole linear list from scratch -- cheap enough (Case
     * Study counts are small) to call after every edit. The Base File is
     * always element 0.
     */
    public static List<Node> buildList(CaseStudyManager manager) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(Node.base());
        if (manager == null || !manager.hasBaseFile()) {
            return nodes;
        }
        for (CaseStudy cs : manager.allCaseStudies()) {
            nodes.add(Node.of(cs, manager.getParent(cs)));
        }
        return nodes;
    }

    /** Finds the Node for this exact CaseStudy (identity); {@code cs == null} finds the Base File entry. */
    public static Node find(List<Node> nodes, CaseStudy cs) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        if (cs == null) {
            return nodes.get(0);
        }
        for (Node n : nodes) {
            if (n.caseStudy == cs) {
                return n;
            }
        }
        return null;
    }
}
