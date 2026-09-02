package com.prdc.mipower.services;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.prdc.mipower.models.CaseStudy;
import com.prdc.mipower.models.ChangeRecord;
import com.prdc.mipower.models.DatRecord;
import com.prdc.mipower.models.DatSection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ModifierEngineTest {

    private static final String SAMPLE_DAT = """
        LOAD FLOW BY NEWTON RAPHSON METHOD
CASE NO :  1     CONTINGENCY  : 0  SCHEDULE NO : 0
CONTINGENCY NAME : Base Case \t RATING CONSIDERED : NOMINAL 
VERSION 10.3
%% 
%Cost Factors
% 1. Interest Charges  2. Operational Charges   3. Life of Equipment (yrs)
% 4. Energy Charges\t5. Loss Load Factor\t\t 6. Cost per Mvar(in Lakhs) 7. Currency
15.00000   4.00000  20.00000   2.50000   0.30000   5.00000 Rs
""";

    @Test
    public void testChildCaseInheritsCostFactorsModification() throws IOException {
        CaseStudyManager manager = new CaseStudyManager();
        // Load raw text as base
        java.io.File temp = java.io.File.createTempFile("test_base", ".dat0");
        temp.deleteOnExit();
        java.nio.file.Files.writeString(temp.toPath(), SAMPLE_DAT);

        manager.loadBaseFile(temp.getAbsolutePath());

        // Create Case 1
        CaseStudy case1 = manager.createRootCaseStudy("Case 1");
        DatSection costSection = case1.parser.getSection("Cost Factors");
        assertNotNull(costSection);
        DatRecord costRecord = costSection.records.get(0);
        assertEquals("15.00000", costRecord.fields.get("Interest Charges"));

        // Simulate user editing Interest Charges from 15.00000 to 20 in Case 1
        Map<String, String> conditions = new LinkedHashMap<>();
        conditions.put("Interest Charges", "15.00000");

        ChangeRecord change = new ChangeRecord(
                "Cost Factors",
                "Interest Charges",
                "15.00000",
                "20",
                "tabular",
                conditions,
                null,
                costRecord.label()
        );
        case1.modManager.addChange(change);
        case1.addHistoryEntry(change);
        costRecord.fields.put("Interest Charges", "20");

        // Create Case 1.1 with Case 1 as reference
        CaseStudy case1_1 = manager.createChildCaseStudy(case1, "case 1.1");
        DatSection childCostSection = case1_1.parser.getSection("Cost Factors");
        assertNotNull(childCostSection);
        DatRecord childCostRecord = childCostSection.records.get(0);

        // Verify that Case 1.1 inherits the 20 value from Case 1
        assertEquals("20", childCostRecord.fields.get("Interest Charges"));
    }

    @Test
    public void testConsecutiveCostFactorsEdits() throws IOException {
        CaseStudyManager manager = new CaseStudyManager();
        java.io.File temp = java.io.File.createTempFile("test_base2", ".dat0");
        temp.deleteOnExit();
        java.nio.file.Files.writeString(temp.toPath(), SAMPLE_DAT);

        manager.loadBaseFile(temp.getAbsolutePath());
        CaseStudy case1 = manager.createRootCaseStudy("Case 1");

        // Edit 1: Interest Charges -> 20
        ChangeRecord change1 = new ChangeRecord(
                "Cost Factors", "Interest Charges", "15.00000", "20",
                "tabular", Map.of(), null, "Cost Factors"
        );
        case1.modManager.addChange(change1);

        // Edit 2: Operational Charges -> 5.5
        ChangeRecord change2 = new ChangeRecord(
                "Cost Factors", "Operational Charges", "4.00000", "5.5",
                "tabular", Map.of(), null, "Cost Factors"
        );
        case1.modManager.addChange(change2);

        String resolved = manager.resolveText(case1);
        org.junit.jupiter.api.Assertions.assertTrue(resolved.contains("20"));
        org.junit.jupiter.api.Assertions.assertTrue(resolved.contains("5.5"));
    }
}
