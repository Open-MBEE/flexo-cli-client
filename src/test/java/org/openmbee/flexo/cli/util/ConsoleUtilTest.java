package org.openmbee.flexo.cli.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleUtilTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
        ConsoleUtil.setColorEnabled(true); // Reset to default
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void testInfo() {
        ConsoleUtil.info("Test message");
        assertEquals("Test message\n", outContent.toString());
    }

    @Test
    void testSuccessWithColorEnabled() {
        ConsoleUtil.setColorEnabled(true);
        ConsoleUtil.success("Success message");
        String output = outContent.toString();
        assertTrue(output.contains("Success message"));
        assertTrue(output.contains(ConsoleUtil.GREEN));
        assertTrue(output.contains(ConsoleUtil.RESET));
    }

    @Test
    void testSuccessWithColorDisabled() {
        ConsoleUtil.setColorEnabled(false);
        ConsoleUtil.success("Success message");
        assertEquals("Success message\n", outContent.toString());
        assertFalse(outContent.toString().contains(ConsoleUtil.GREEN));
    }

    @Test
    void testErrorWithColorEnabled() {
        ConsoleUtil.setColorEnabled(true);
        ConsoleUtil.error("Error message");
        String output = errContent.toString();
        assertTrue(output.contains("Error: Error message"));
        assertTrue(output.contains(ConsoleUtil.RED));
        assertTrue(output.contains(ConsoleUtil.RESET));
    }

    @Test
    void testErrorWithColorDisabled() {
        ConsoleUtil.setColorEnabled(false);
        ConsoleUtil.error("Error message");
        assertEquals("Error: Error message\n", errContent.toString());
        assertFalse(errContent.toString().contains(ConsoleUtil.RED));
    }

    @Test
    void testWarnWithColorEnabled() {
        ConsoleUtil.setColorEnabled(true);
        ConsoleUtil.warn("Warning message");
        String output = outContent.toString();
        assertTrue(output.contains("Warning: Warning message"));
        assertTrue(output.contains(ConsoleUtil.YELLOW));
        assertTrue(output.contains(ConsoleUtil.RESET));
    }

    @Test
    void testWarnWithColorDisabled() {
        ConsoleUtil.setColorEnabled(false);
        ConsoleUtil.warn("Warning message");
        assertEquals("Warning: Warning message\n", outContent.toString());
        assertFalse(outContent.toString().contains(ConsoleUtil.YELLOW));
    }

    @Test
    void testDebugWithColorEnabled() {
        ConsoleUtil.setColorEnabled(true);
        ConsoleUtil.debug("Debug message");
        String output = outContent.toString();
        assertTrue(output.contains("Debug message"));
        assertTrue(output.contains(ConsoleUtil.CYAN));
        assertTrue(output.contains(ConsoleUtil.RESET));
    }

    @Test
    void testDebugWithColorDisabled() {
        ConsoleUtil.setColorEnabled(false);
        ConsoleUtil.debug("Debug message");
        assertEquals("Debug message\n", outContent.toString());
        assertFalse(outContent.toString().contains(ConsoleUtil.CYAN));
    }

    @Test
    void testHeaderWithColorEnabled() {
        ConsoleUtil.setColorEnabled(true);
        ConsoleUtil.header("Header message");
        String output = outContent.toString();
        assertTrue(output.contains("Header message"));
        assertTrue(output.contains(ConsoleUtil.BOLD));
        assertTrue(output.contains(ConsoleUtil.RESET));
    }

    @Test
    void testHeaderWithColorDisabled() {
        ConsoleUtil.setColorEnabled(false);
        ConsoleUtil.header("Header message");
        assertEquals("Header message\n", outContent.toString());
        assertFalse(outContent.toString().contains(ConsoleUtil.BOLD));
    }

    @Test
    void testPrintTableWithData() {
        String[] headers = {"Name", "Age", "City"};
        String[][] rows = {
                {"Alice", "30", "New York"},
                {"Bob", "25", "London"},
                {"Charlie", "35", "Paris"}
        };

        ConsoleUtil.printTable(headers, rows);

        String output = outContent.toString();
        assertTrue(output.contains("Name"));
        assertTrue(output.contains("Age"));
        assertTrue(output.contains("City"));
        assertTrue(output.contains("Alice"));
        assertTrue(output.contains("Bob"));
        assertTrue(output.contains("Charlie"));
        assertTrue(output.contains("---")); // Separator line
    }

    @Test
    void testPrintTableWithEmptyRows() {
        String[] headers = {"Name", "Age"};
        String[][] rows = {};

        ConsoleUtil.printTable(headers, rows);

        String output = outContent.toString();
        assertEquals("", output); // Should print nothing for empty rows
    }

    @Test
    void testPrintTableWithNullCells() {
        String[] headers = {"Col1", "Col2", "Col3"};
        String[][] rows = {
                {"Value1", null, "Value3"},
                {null, "Value2", null}
        };

        ConsoleUtil.printTable(headers, rows);

        String output = outContent.toString();
        assertTrue(output.contains("Col1"));
        assertTrue(output.contains("Value1"));
        assertTrue(output.contains("Value2"));
        assertTrue(output.contains("Value3"));
        assertFalse(output.contains("null")); // Nulls should be replaced with empty strings
    }

    @Test
    void testPrintTableWithDifferentWidths() {
        String[] headers = {"Short", "Medium Length", "X"};
        String[][] rows = {
                {"A", "B", "Very Long Content Here"},
                {"Short", "This is longer", "C"}
        };

        ConsoleUtil.printTable(headers, rows);

        String output = outContent.toString();
        assertTrue(output.contains("Short"));
        assertTrue(output.contains("Medium Length"));
        assertTrue(output.contains("Very Long Content Here"));
        // Check that columns are properly padded
        String[] lines = output.split("\n");
        assertTrue(lines.length >= 4); // Header, separator, 2 rows
    }

    @Test
    void testPrintTableWithUnevenRows() {
        String[] headers = {"A", "B", "C"};
        String[][] rows = {
                {"1", "2"}, // Missing third column
                {"3", "4", "5", "6"} // Extra column (should be ignored)
        };

        ConsoleUtil.printTable(headers, rows);

        String output = outContent.toString();
        assertTrue(output.contains("A"));
        assertTrue(output.contains("B"));
        assertTrue(output.contains("C"));
        assertTrue(output.contains("1"));
        assertTrue(output.contains("2"));
        assertTrue(output.contains("3"));
        assertTrue(output.contains("4"));
        assertTrue(output.contains("5"));
    }

    @Test
    void testSetColorEnabled() {
        ConsoleUtil.setColorEnabled(false);
        ConsoleUtil.success("Test");
        assertFalse(outContent.toString().contains(ConsoleUtil.GREEN));

        outContent.reset();

        ConsoleUtil.setColorEnabled(true);
        ConsoleUtil.success("Test");
        assertTrue(outContent.toString().contains(ConsoleUtil.GREEN));
    }
}
