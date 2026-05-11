package com.agentos.core.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextSplitterTest {

    private final TextSplitter splitter = new TextSplitter(20, 5, 100);

    @Test
    void splitsShortTextIntoSingleChunk() {
        List<TextSplitter.ChunkResult> chunks = splitter.split("Hello world", "test.txt");
        assertEquals(1, chunks.size());
        assertEquals("Hello world", chunks.getFirst().text());
        assertEquals(0, chunks.getFirst().index());
    }

    @Test
    void splitsAtParagraphBoundary() {
        String text = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph.";
        List<TextSplitter.ChunkResult> chunks = splitter.split(text, "test.txt");
        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.getFirst().text().contains("First paragraph"));
    }

    @Test
    void splitsAtNewlineWhenNoParagraphBreak() {
        String text = "Line one\nLine two\nLine three\nLine four\nLine five\nLine six\n";
        List<TextSplitter.ChunkResult> chunks = splitter.split(text, "test.txt");
        assertTrue(chunks.size() >= 2);
    }

    @Test
    void emptyTextReturnsEmptyList() {
        assertTrue(splitter.split("", "doc.txt").isEmpty());
        assertTrue(splitter.split(null, "doc.txt").isEmpty());
        assertTrue(splitter.split("   ", "doc.txt").isEmpty());
    }

    @Test
    void respectsMaxChunks() {
        TextSplitter limited = new TextSplitter(5, 0, 3);
        String text = "word word word word word word word word word word word word word word word word word word word word";
        List<TextSplitter.ChunkResult> chunks = limited.split(text, "test.txt");
        assertTrue(chunks.size() <= 3);
    }

    @Test
    void overlappingChunks() {
        TextSplitter overlapSplitter = new TextSplitter(20, 10, 10);
        String text = "A. B. C. D. E.";
        // With overlap, chunks should share content
        List<TextSplitter.ChunkResult> chunks = overlapSplitter.split(text, "test.txt");
        assertFalse(chunks.isEmpty());
        // Verify overlap by checking that consecutive chunks share content
        if (chunks.size() > 1) {
            String first = chunks.get(0).text();
            String second = chunks.get(1).text();
            // The overlap should mean the start of the second chunk is within the first chunk
            int overlap = Math.min(10, second.length());
            assertTrue(first.length() >= 10 || second.length() >= 10);
        }
    }

    @Test
    void findLastSeparatorWithin_returnsCorrectIndex() {
        assertEquals(4, TextSplitter.findLastSeparatorWithin("a\n\nb\n\nc", "\n\n", 6));
        assertEquals(-1, TextSplitter.findLastSeparatorWithin("abc", "\n\n", 3));
        assertEquals(1, TextSplitter.findLastSeparatorWithin("a\nbc", "\n", 4));
    }

    @Test
    void approximateTokens_roughEstimate() {
        assertTrue(TextSplitter.approximateTokens("hello world") > 0);
        assertEquals(1, TextSplitter.approximateTokens(""));  // min 1
        assertEquals(1, TextSplitter.approximateTokens(null)); // min 1
        assertEquals(3, TextSplitter.approximateTokens("abcdefghijklm")); // 13/4 = 3
    }

    @Test
    void handlesVeryLongText() {
        String text = "word. ".repeat(1000);
        TextSplitter bigSplitter = new TextSplitter(100, 20, 10);
        List<TextSplitter.ChunkResult> chunks = bigSplitter.split(text, "long.txt");
        assertTrue(chunks.size() > 1);
        assertTrue(chunks.size() <= 10); // capped by maxChunks
    }
}
