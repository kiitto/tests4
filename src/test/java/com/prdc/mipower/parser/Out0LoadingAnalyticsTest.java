package com.prdc.mipower.parser;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.prdc.mipower.models.Branch;
import com.prdc.mipower.models.Line;
import com.prdc.mipower.models.Out0Results;
import com.prdc.mipower.models.Transformer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Out0LoadingAnalyticsTest {

    private static final String SAMPLE_OUT0 = """
            OUTPUT RESULTS OF LOAD FLOW STUDY
            Number of P Iterations : 4 and Number of Q Iterations : 3
            TOTAL REAL POWER GENERATION (CONVENTIONAL) : 150.25D+00
            TOTAL REACT. POWER GENERATION (CONVENTIONAL) : 45.10D+00
            TOTAL REAL POWER LOAD : 145.00D+00
            TOTAL REACTIVE POWER LOAD : 40.00D+00
            TOTAL REAL POWER LOSS (AC+DC) : 5.25D+00
            PERCENTAGE REAL  LOSS (AC+DC) : 3.50D+00
            TOTAL REACTIVE POWER  LOSS : 12.80D+00
            --------------------------------------------------------------------------------
            BUS VOLTAGES AND POWERS
            1  BUS_ONE    1.0200   0.0000   100.0000   30.0000   0.0000   0.0000  
            2  BUS_TWO    0.9850  -2.5000     0.0000    0.0000  80.0000  20.0000  
            3  BUS_THREE  0.9400  -5.1000     0.0000    0.0000  65.0000  20.0000  @
            --------------------------------------------------------------------------------
            TRANSFORMER FLOWS AND TRANSFORMER LOSSES
            1  1  1  BUS_ONE    2  BUS_TWO    45.50D+00   12.30D+00   0.85D+00   2.10D+00   48.75D+00
            2  1  1  BUS_ONE    3  BUS_THREE  55.20D+00   18.40D+00   1.20D+00   3.40D+00  102.50D+00  #
            --------------------------------------------------------------------------------
            LINE FLOWS AND LINE LOSSES
            1  1  2  BUS_TWO    3  BUS_THREE  25.10D+00    8.20D+00   0.45D+00   1.15D+00   65.30D+00
            2  1  3  BUS_THREE  1  BUS_ONE    15.00D+00    5.00D+00   0.30D+00   0.80D+00   22.15D+00
            --------------------------------------------------------------------------------
            """;

    @Test
    public void testOut0TransformerAndLineParsing() throws Exception {
        Out0Results results = Out0Parser.parseText(SAMPLE_OUT0, "sample.out0");
        assertNotNull(results);
        assertEquals(2, results.transformers.size());
        assertEquals(2, results.lines.size());
        assertEquals(4, results.branches().size());

        Transformer t1 = results.transformers.get(0);
        assertEquals(1, t1.fromBus);
        assertEquals("BUS_ONE", t1.fromName);
        assertEquals(2, t1.toBus);
        assertEquals("BUS_TWO", t1.toName);
        assertEquals(45.50, t1.mwFlow, 1e-4);
        assertEquals(48.75, t1.loadingPercent, 1e-4);
        assertFalse(t1.isOverloaded());

        Transformer t2 = results.transformers.get(1);
        assertEquals(102.50, t2.loadingPercent, 1e-4);
        assertTrue(t2.isOverloaded());

        Line l1 = results.lines.get(0);
        assertEquals(2, l1.fromBus);
        assertEquals(3, l1.toBus);
        assertEquals(65.30, l1.loadingPercent, 1e-4);
        assertFalse(l1.isOverloaded());

        Line l2 = results.lines.get(1);
        assertEquals(22.15, l2.loadingPercent, 1e-4);
    }

    @Test
    public void testChartBinningAndFilteringPredicateMatch() throws Exception {
        Out0Results results = Out0Parser.parseText(SAMPLE_OUT0, "sample.out0");

        // Bin: 0.0% to 50.0% vs 50.0% to 100.0% vs 100.0% to 150.0%
        double bLo1 = 0.0, bHi1 = 50.0;
        double bLo2 = 50.0, bHi2 = 100.0;
        double bLo3 = 100.0, bHi3 = 150.0;

        long xfmrBin1Count = results.transformers.stream()
                .filter(b -> b.loadingPercent >= bLo1 - 1e-7 && b.loadingPercent < bHi1 - 1e-7)
                .count();
        List<Transformer> xfmrBin1Table = results.transformers.stream()
                .filter(b -> b.loadingPercent >= bLo1 - 1e-7 && b.loadingPercent < bHi1 - 1e-7)
                .collect(Collectors.toList());
        assertEquals(1, xfmrBin1Count);
        assertEquals(xfmrBin1Count, xfmrBin1Table.size());
        assertEquals(48.75, xfmrBin1Table.get(0).loadingPercent, 1e-4);

        long xfmrBin3Count = results.transformers.stream()
                .filter(b -> b.loadingPercent >= bLo3 - 1e-7 && b.loadingPercent <= bHi3 + 1e-7)
                .count();
        List<Transformer> xfmrBin3Table = results.transformers.stream()
                .filter(b -> b.loadingPercent >= bLo3 - 1e-7 && b.loadingPercent <= bHi3 + 1e-7)
                .collect(Collectors.toList());
        assertEquals(1, xfmrBin3Count);
        assertEquals(xfmrBin3Count, xfmrBin3Table.size());
        assertEquals(102.50, xfmrBin3Table.get(0).loadingPercent, 1e-4);

        // Lines bin 2: 50% to 100%
        long lineBin2Count = results.lines.stream()
                .filter(b -> b.loadingPercent >= bLo2 - 1e-7 && b.loadingPercent < bHi2 - 1e-7)
                .count();
        List<Line> lineBin2Table = results.lines.stream()
                .filter(b -> b.loadingPercent >= bLo2 - 1e-7 && b.loadingPercent < bHi2 - 1e-7)
                .collect(Collectors.toList());
        assertEquals(1, lineBin2Count);
        assertEquals(lineBin2Count, lineBin2Table.size());
        assertEquals(65.30, lineBin2Table.get(0).loadingPercent, 1e-4);
    }
}
