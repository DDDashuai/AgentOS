package com.agentos.core.file;

import java.util.List;
import java.util.Map;

/**
 * Holds the parsed representation of an uploaded file.
 * Immutable after creation.
 */
public record UploadedFile(
        String fileId,
        String originalFilename,
        String fileType,
        List<String> headers,
        List<Map<String, String>> rows,
        int totalRowCount,
        String rawText,
        List<Map<String, String>> previewRows
) {
    public String tableDescription() {
        return "  File: " + originalFilename + " (id: " + fileId + ")"
                + "\n    Type: " + fileType
                + "\n    Columns: " + String.join(", ", headers)
                + "\n    Rows: " + totalRowCount;
    }
}
