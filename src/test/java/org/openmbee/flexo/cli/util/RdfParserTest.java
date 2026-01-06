package org.openmbee.flexo.cli.util;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RdfParserTest {

    private static final String TURTLE_DATA = "@prefix ex: <http://example.org/> .\n" +
            "ex:subject ex:predicate \"object\" .";

    @Test
    void testParseStringTurtle() throws IOException {
        Model model = RdfParser.parseString(TURTLE_DATA, "turtle");
        assertNotNull(model);
        assertTrue(model.size() > 0);
    }

    @Test
    void testParseStringTtl() throws IOException {
        Model model = RdfParser.parseString(TURTLE_DATA, "ttl");
        assertNotNull(model);
        assertTrue(model.size() > 0);
    }

    @Test
    void testParseStringJsonLd() throws IOException {
        String jsonld = "{\n" +
                "  \"@context\": {\"ex\": \"http://example.org/\"},\n" +
                "  \"@id\": \"ex:subject\",\n" +
                "  \"ex:predicate\": \"object\"\n" +
                "}";
        Model model = RdfParser.parseString(jsonld, "jsonld");
        assertNotNull(model);
        assertTrue(model.size() > 0);
    }

    @Test
    void testParseStringRdfXml() throws IOException {
        String rdfxml = "<?xml version=\"1.0\"?>\n" +
                "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n" +
                "         xmlns:ex=\"http://example.org/\">\n" +
                "  <rdf:Description rdf:about=\"http://example.org/subject\">\n" +
                "    <ex:predicate>object</ex:predicate>\n" +
                "  </rdf:Description>\n" +
                "</rdf:RDF>";
        Model model = RdfParser.parseString(rdfxml, "rdfxml");
        assertNotNull(model);
        assertTrue(model.size() > 0);
    }

    @Test
    void testParseStringNTriples() throws IOException {
        String ntriples = "<http://example.org/subject> <http://example.org/predicate> \"object\" .";
        Model model = RdfParser.parseString(ntriples, "ntriples");
        assertNotNull(model);
        assertEquals(1, model.size());
    }

    @Test
    void testParseFile(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("test.ttl");
        Files.writeString(testFile, TURTLE_DATA);

        Model model = RdfParser.parseFile(testFile.toString(), "turtle");
        assertNotNull(model);
        assertTrue(model.size() > 0);
    }

    @Test
    void testParseStream() throws IOException {
        try (InputStream in = new ByteArrayInputStream(TURTLE_DATA.getBytes())) {
            Model model = RdfParser.parseStream(in, "turtle");
            assertNotNull(model);
            assertTrue(model.size() > 0);
        }
    }

    @Test
    void testToString() {
        Model model = ModelFactory.createDefaultModel();
        model.read(new ByteArrayInputStream(TURTLE_DATA.getBytes()), null, "TURTLE");

        String result = RdfParser.toString(model, "turtle");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("example.org"));
    }

    @Test
    void testToFile(@TempDir Path tempDir) throws IOException {
        Model model = ModelFactory.createDefaultModel();
        model.read(new ByteArrayInputStream(TURTLE_DATA.getBytes()), null, "TURTLE");

        Path outputFile = tempDir.resolve("output.ttl");
        RdfParser.toFile(model, outputFile.toString(), "turtle");

        assertTrue(Files.exists(outputFile));
        String content = Files.readString(outputFile);
        assertFalse(content.isEmpty());
    }

    @Test
    void testToStream() throws IOException {
        Model model = ModelFactory.createDefaultModel();
        model.read(new ByteArrayInputStream(TURTLE_DATA.getBytes()), null, "TURTLE");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RdfParser.toStream(model, out, "turtle");

        String result = out.toString();
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetContentTypeTurtle() {
        assertEquals("text/turtle", RdfParser.getContentType("turtle"));
        assertEquals("text/turtle", RdfParser.getContentType("ttl"));
    }

    @Test
    void testGetContentTypeJsonLd() {
        assertEquals("application/ld+json", RdfParser.getContentType("jsonld"));
        assertEquals("application/ld+json", RdfParser.getContentType("json-ld"));
    }

    @Test
    void testGetContentTypeRdfXml() {
        assertEquals("application/rdf+xml", RdfParser.getContentType("rdfxml"));
        assertEquals("application/rdf+xml", RdfParser.getContentType("rdf-xml"));
        assertEquals("application/rdf+xml", RdfParser.getContentType("xml"));
    }

    @Test
    void testGetContentTypeNTriples() {
        assertEquals("application/n-triples", RdfParser.getContentType("ntriples"));
        assertEquals("application/n-triples", RdfParser.getContentType("nt"));
    }

    @Test
    void testGetContentTypeNQuads() {
        assertEquals("application/n-quads", RdfParser.getContentType("nquads"));
        assertEquals("application/n-quads", RdfParser.getContentType("nq"));
    }

    @Test
    void testGetContentTypeTrig() {
        assertEquals("application/trig", RdfParser.getContentType("trig"));
    }

    @Test
    void testGetContentTypeDefault() {
        assertEquals("text/turtle", RdfParser.getContentType("unknown"));
        assertEquals("text/turtle", RdfParser.getContentType(""));
    }

    @Test
    void testGetContentTypeCaseInsensitive() {
        assertEquals("text/turtle", RdfParser.getContentType("TURTLE"));
        assertEquals("application/ld+json", RdfParser.getContentType("JSONLD"));
    }

    @Test
    void testGetFormatFromContentTypeTurtle() {
        assertEquals("turtle", RdfParser.getFormatFromContentType("text/turtle"));
        assertEquals("turtle", RdfParser.getFormatFromContentType("application/x-turtle"));
    }

    @Test
    void testGetFormatFromContentTypeJsonLd() {
        assertEquals("jsonld", RdfParser.getFormatFromContentType("application/ld+json"));
        assertEquals("jsonld", RdfParser.getFormatFromContentType("application/json"));
    }

    @Test
    void testGetFormatFromContentTypeRdfXml() {
        assertEquals("rdfxml", RdfParser.getFormatFromContentType("application/rdf+xml"));
    }

    @Test
    void testGetFormatFromContentTypeNTriples() {
        assertEquals("ntriples", RdfParser.getFormatFromContentType("application/n-triples"));
    }

    @Test
    void testGetFormatFromContentTypeNQuads() {
        assertEquals("nquads", RdfParser.getFormatFromContentType("application/n-quads"));
    }

    @Test
    void testGetFormatFromContentTypeTrig() {
        assertEquals("trig", RdfParser.getFormatFromContentType("application/trig"));
    }

    @Test
    void testGetFormatFromContentTypeDefault() {
        assertEquals("turtle", RdfParser.getFormatFromContentType("unknown/type"));
        assertEquals("turtle", RdfParser.getFormatFromContentType(null));
    }

    @Test
    void testGetFormatFromContentTypeCaseInsensitive() {
        assertEquals("turtle", RdfParser.getFormatFromContentType("TEXT/TURTLE"));
        assertEquals("jsonld", RdfParser.getFormatFromContentType("APPLICATION/LD+JSON"));
    }

    @Test
    void testValidateWithValidModel() {
        Model model = ModelFactory.createDefaultModel();
        model.read(new ByteArrayInputStream(TURTLE_DATA.getBytes()), null, "TURTLE");

        assertTrue(RdfParser.validate(model));
    }

    @Test
    void testValidateWithEmptyModel() {
        Model model = ModelFactory.createDefaultModel();
        assertFalse(RdfParser.validate(model));
    }

    @Test
    void testValidateWithNullModel() {
        assertFalse(RdfParser.validate(null));
    }

    @Test
    void testParseStringWithUnknownFormat() throws IOException {
        // Should default to turtle and log a warning
        Model model = RdfParser.parseString(TURTLE_DATA, "unknown-format");
        assertNotNull(model);
        assertTrue(model.size() > 0);
    }

    @Test
    void testToStringWithUnknownFormat() {
        Model model = ModelFactory.createDefaultModel();
        model.read(new ByteArrayInputStream(TURTLE_DATA.getBytes()), null, "TURTLE");

        // Should default to turtle and log a warning
        String result = RdfParser.toString(model, "unknown-format");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testRoundTripTurtle() throws IOException {
        Model original = RdfParser.parseString(TURTLE_DATA, "turtle");
        String serialized = RdfParser.toString(original, "turtle");
        Model deserialized = RdfParser.parseString(serialized, "turtle");

        assertEquals(original.size(), deserialized.size());
        assertTrue(original.isIsomorphicWith(deserialized));
    }

    @Test
    void testParseFileNonExistent() {
        assertThrows(IOException.class, () -> {
            RdfParser.parseFile("/non/existent/file.ttl", "turtle");
        });
    }

    @Test
    void testParseStringInvalidRdf() {
        assertThrows(Exception.class, () -> {
            RdfParser.parseString("This is not valid RDF", "turtle");
        });
    }
}
