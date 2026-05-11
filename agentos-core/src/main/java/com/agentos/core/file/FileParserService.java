package com.agentos.core.file;

import com.opencsv.CSVReader;
import org.apache.poi.ss.usermodel.*;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FileParserService {

    private static final Logger log = LoggerFactory.getLogger(FileParserService.class);
    private static final Set<String> SUPPORTED = Set.of("csv", "xlsx", "pdf");

    private final int maxRows;
    private final int maxPreviewRows;

    public FileParserService(
            @Value("${agentos.upload.max-rows-per-file:5000}") int maxRows,
            @Value("${agentos.upload.max-preview-rows:5}") int maxPreviewRows) {
        this.maxRows = maxRows;
        this.maxPreviewRows = maxPreviewRows;
    }

    public UploadedFile parse(String originalFilename, InputStream inputStream) throws IOException {
        String lower = originalFilename.toLowerCase();
        String fileId = UUID.randomUUID().toString();

        try {
            if (lower.endsWith(".csv")) {
                return parseCsv(fileId, originalFilename, inputStream);
            } else if (lower.endsWith(".xlsx")) {
                return parseXlsx(fileId, originalFilename, inputStream);
            } else if (lower.endsWith(".pdf")) {
                return parsePdf(fileId, originalFilename, inputStream);
            } else {
                throw new IllegalArgumentException("Unsupported file type: " + originalFilename
                        + ". Supported: " + SUPPORTED);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse " + originalFilename + ": " + e.getMessage(), e);
        }
    }

    private UploadedFile parseCsv(String fileId, String filename, InputStream is) throws Exception {
        try (CSVReader reader = new CSVReader(new InputStreamReader(is))) {
            String[] headersArr = reader.readNext();
            if (headersArr == null) {
                throw new IllegalArgumentException("CSV file is empty: " + filename);
            }
            List<String> headers = List.of(headersArr);
            List<Map<String, String>> rows = new ArrayList<>();
            String[] line;
            while ((line = reader.readNext()) != null && rows.size() < maxRows) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < line.length ? line[i] : "");
                }
                rows.add(row);
            }
            return buildResult(fileId, filename, "csv", headers, rows);
        }
    }

    private UploadedFile parseXlsx(String fileId, String filename, InputStream is) throws Exception {
        try (Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            List<String> headers = new ArrayList<>();
            List<Map<String, String>> rows = new ArrayList<>();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    for (Cell cell : row) {
                        headers.add(getCellValue(cell));
                    }
                    continue;
                }
                if (rows.size() >= maxRows) break;
                Map<String, String> dataRow = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.getCell(i);
                    dataRow.put(headers.get(i), cell != null ? getCellValue(cell) : "");
                }
                rows.add(dataRow);
            }
            return buildResult(fileId, filename, "xlsx", headers, rows);
        }
    }

    private UploadedFile parsePdf(String fileId, String filename, InputStream is) throws Exception {
        Tika tika = new Tika();
        String text = tika.parseToString(is);

        List<String> lines = text.lines()
                .filter(l -> !l.isBlank())
                .limit(maxRows + 1)
                .collect(Collectors.toList());

        if (lines.isEmpty()) {
            return new UploadedFile(fileId, filename, "pdf",
                    List.of("content"), List.of(Map.of("content", "")),
                    0, text, List.of());
        }

        // Attempt tabular parsing: split on 2+ spaces or tab
        String[] headersArr = lines.get(0).split("\\s{2,}|\t");
        List<String> headers = List.of(headersArr);
        List<Map<String, String>> rows = new ArrayList<>();

        for (int i = 1; i < lines.size() && rows.size() < maxRows; i++) {
            String[] parts = lines.get(i).split("\\s{2,}|\t");
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                row.put(headers.get(j), j < parts.length ? parts[j] : "");
            }
            if (row.values().stream().anyMatch(v -> !v.isEmpty())) {
                rows.add(row);
            }
        }

        // Fallback: if no structured table found, store as raw text document
        if (rows.isEmpty()) {
            String preview = text.length() > 500 ? text.substring(0, 500) + "..." : text;
            return new UploadedFile(fileId, filename, "pdf",
                    List.of("content"), List.of(Map.of("content", text)),
                    1, text, List.of(Map.of("content", preview)));
        }

        return buildResult(fileId, filename, "pdf", headers, rows);
    }

    private UploadedFile buildResult(String fileId, String filename, String type,
                                     List<String> headers, List<Map<String, String>> rows) {
        int previewEnd = Math.min(maxPreviewRows, rows.size());
        return new UploadedFile(
                fileId, filename, type,
                List.copyOf(headers),
                List.copyOf(rows),
                rows.size(),
                null,
                List.copyOf(rows.subList(0, previewEnd)));
    }

    private String getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                yield v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield cell.getStringCellValue();
                }
            }
            default -> "";
        };
    }
}
