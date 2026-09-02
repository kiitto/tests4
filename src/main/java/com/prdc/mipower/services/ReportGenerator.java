package com.prdc.mipower.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import com.prdc.mipower.models.ChangeRecord;
import com.prdc.mipower.utils.FileUtils;

/**
 * Generates a change report in plain text, CSV, or PDF -- Section,
 * Condition, Field, Old Value, New Value, Timestamp -- for audit purposes.
 * Port of the Python project's {@code report.py} (TXT), extended with CSV
 * (Apache Commons CSV) and PDF (Apache PDFBox) as requested.
 */
public class ReportGenerator {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] HEADERS =
            {"Section", "Condition", "Field", "Old Value", "New Value", "Timestamp"};
    private static final int[] COLUMN_WIDTHS = {28, 34, 22, 18, 18, 20};

    private static String conditionString(ChangeRecord c) {
        return c.formatConditionStr();
    }

    private static String[] rowFor(ChangeRecord c) {
        return new String[]{
                c.section,
                conditionString(c),
                (c.field != null) ? c.field : "(value)",
                c.oldValue,
                c.newValue,
                c.timestamp,
        };
    }

    // --------------------------------------------------------------------- //
    // TXT
    // --------------------------------------------------------------------- //
    private static String padRight(String s, int width) {
        if (s == null) {
            s = "";
        }
        if (s.length() >= width) {
            return s;
        }
        return s + " ".repeat(width - s.length());
    }

    public String generateTxt(List<ChangeRecord> changes, String outputPath,
                               String inputFile, String outputFile) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("PRDC Dynamic MiPower Input File Editor -- Change Report\n");
        sb.append("Generated: ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT)).append("\n");
        if (inputFile != null && !inputFile.isBlank()) {
            sb.append("Input File : ").append(inputFile).append("\n");
        }
        if (outputFile != null && !outputFile.isBlank()) {
            sb.append("Output File: ").append(outputFile).append("\n");
        }
        sb.append("Total Changes: ").append(changes.size()).append("\n\n");

        StringBuilder headerRow = new StringBuilder();
        for (int i = 0; i < HEADERS.length; i++) {
            headerRow.append(padRight(HEADERS[i], COLUMN_WIDTHS[i]));
        }
        sb.append(headerRow).append("\n");
        int totalWidth = 0;
        for (int w : COLUMN_WIDTHS) {
            totalWidth += w;
        }
        sb.append("-".repeat(totalWidth)).append("\n");

        for (ChangeRecord c : changes) {
            String[] row = rowFor(c);
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < row.length; i++) {
                line.append(padRight(row[i], COLUMN_WIDTHS[i]));
            }
            sb.append(line).append("\n");
        }

        FileUtils.ensureParentDirs(outputPath);
        Files.writeString(Path.of(outputPath), sb.toString(), StandardCharsets.UTF_8);
        return outputPath;
    }

    // --------------------------------------------------------------------- //
    // CSV
    // --------------------------------------------------------------------- //
    public String generateCsv(List<ChangeRecord> changes, String outputPath) throws IOException {
        FileUtils.ensureParentDirs(outputPath);
        try (var writer = Files.newBufferedWriter(Path.of(outputPath), StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader(HEADERS)
                     .build())) {
            for (ChangeRecord c : changes) {
                printer.printRecord((Object[]) rowFor(c));
            }
        }
        return outputPath;
    }

    // --------------------------------------------------------------------- //
    // PDF
    // --------------------------------------------------------------------- //
    private static final float PDF_MARGIN = 40f;
    private static final float PDF_ROW_HEIGHT = 16f;
    private static final float PDF_FONT_SIZE = 9f;
    private static final float[] PDF_COLUMN_WIDTHS = {90f, 110f, 75f, 65f, 65f, 90f};

    public String generatePdf(List<ChangeRecord> changes, String outputPath,
                               String inputFile, String outputFile) throws IOException {
        FileUtils.ensureParentDirs(outputPath);

        try (PDDocument document = new PDDocument()) {
            PDFont regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            List<String[]> rows = new ArrayList<>();
            for (ChangeRecord c : changes) {
                rows.add(rowFor(c));
            }

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            float pageHeight = page.getMediaBox().getHeight();
            float pageWidth = page.getMediaBox().getWidth();
            float y = pageHeight - PDF_MARGIN;

            y = writePdfHeader(stream, boldFont, regularFont, y, pageWidth, inputFile, outputFile, changes.size());
            y = writePdfTableHeader(stream, boldFont, y);

            for (String[] row : rows) {
                if (y < PDF_MARGIN + PDF_ROW_HEIGHT) {
                    stream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    y = pageHeight - PDF_MARGIN;
                    y = writePdfTableHeader(stream, boldFont, y);
                }
                y = writePdfRow(stream, regularFont, y, row);
            }
            stream.close();

            document.save(outputPath);
        }
        return outputPath;
    }

    private float writePdfHeader(PDPageContentStream stream, PDFont boldFont, PDFont regularFont,
                                  float y, float pageWidth, String inputFile, String outputFile,
                                  int totalChanges) throws IOException {
        stream.beginText();
        stream.setFont(boldFont, 14);
        stream.newLineAtOffset(PDF_MARGIN, y);
        stream.showText("PRDC Dynamic MiPower Input File Editor -- Change Report");
        stream.endText();
        y -= 22;

        stream.beginText();
        stream.setFont(regularFont, 9);
        stream.newLineAtOffset(PDF_MARGIN, y);
        stream.showText("Generated: " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
        stream.endText();
        y -= 14;

        if (inputFile != null && !inputFile.isBlank()) {
            y = writePdfPlainLine(stream, regularFont, y, "Input File: " + truncate(inputFile, 100));
        }
        if (outputFile != null && !outputFile.isBlank()) {
            y = writePdfPlainLine(stream, regularFont, y, "Output File: " + truncate(outputFile, 100));
        }
        y = writePdfPlainLine(stream, regularFont, y, "Total Changes: " + totalChanges);
        y -= 8;
        return y;
    }

    private float writePdfPlainLine(PDPageContentStream stream, PDFont font, float y, String text)
            throws IOException {
        stream.beginText();
        stream.setFont(font, 9);
        stream.newLineAtOffset(PDF_MARGIN, y);
        stream.showText(text);
        stream.endText();
        return y - 14;
    }

    private float writePdfTableHeader(PDPageContentStream stream, PDFont boldFont, float y) throws IOException {
        float x = PDF_MARGIN;
        for (int i = 0; i < HEADERS.length; i++) {
            stream.beginText();
            stream.setFont(boldFont, PDF_FONT_SIZE);
            stream.newLineAtOffset(x, y);
            stream.showText(HEADERS[i]);
            stream.endText();
            x += PDF_COLUMN_WIDTHS[i];
        }
        return y - PDF_ROW_HEIGHT;
    }

    private float writePdfRow(PDPageContentStream stream, PDFont font, float y, String[] row) throws IOException {
        float x = PDF_MARGIN;
        for (int i = 0; i < row.length; i++) {
            String cell = truncate(row[i] != null ? row[i] : "", 40);
            stream.beginText();
            stream.setFont(font, PDF_FONT_SIZE);
            stream.newLineAtOffset(x, y);
            stream.showText(cell);
            stream.endText();
            x += PDF_COLUMN_WIDTHS[i];
        }
        return y - PDF_ROW_HEIGHT;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        return (s.length() <= maxLen) ? s : s.substring(0, maxLen - 1) + "\u2026";
    }
}
