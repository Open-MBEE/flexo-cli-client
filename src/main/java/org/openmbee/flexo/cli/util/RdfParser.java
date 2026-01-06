package org.openmbee.flexo.cli.util;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Utility class for parsing and serializing RDF data
 */
public class RdfParser {
    private static final Logger logger = LoggerFactory.getLogger(RdfParser.class);

    /**
     * Parse RDF from a string
     */
    public static Model parseString(String content, String format) throws IOException {
        Model model = ModelFactory.createDefaultModel();
        try (StringReader reader = new StringReader(content)) {
            Lang lang = getLangFromFormat(format);
            RDFDataMgr.read(model, reader, null, lang);
        }
        return model;
    }

    /**
     * Parse RDF from a file
     */
    public static Model parseFile(String filePath, String format) throws IOException {
        Model model = ModelFactory.createDefaultModel();
        try (InputStream in = new FileInputStream(filePath)) {
            Lang lang = getLangFromFormat(format);
            RDFDataMgr.read(model, in, lang);
        }
        return model;
    }

    /**
     * Parse RDF from an input stream
     */
    public static Model parseStream(InputStream in, String format) throws IOException {
        Model model = ModelFactory.createDefaultModel();
        Lang lang = getLangFromFormat(format);
        RDFDataMgr.read(model, in, lang);
        return model;
    }

    /**
     * Serialize model to string
     */
    public static String toString(Model model, String format) {
        StringWriter writer = new StringWriter();
        RDFFormat rdfFormat = getRdfFormatFromFormat(format);
        RDFDataMgr.write(writer, model, rdfFormat);
        return writer.toString();
    }

    /**
     * Serialize model to file
     */
    public static void toFile(Model model, String filePath, String format) throws IOException {
        try (OutputStream out = new FileOutputStream(filePath)) {
            RDFFormat rdfFormat = getRdfFormatFromFormat(format);
            RDFDataMgr.write(out, model, rdfFormat);
        }
    }

    /**
     * Serialize model to output stream
     */
    public static void toStream(Model model, OutputStream out, String format) {
        RDFFormat rdfFormat = getRdfFormatFromFormat(format);
        RDFDataMgr.write(out, model, rdfFormat);
    }

    /**
     * Get content type for format
     */
    public static String getContentType(String format) {
        return switch (format.toLowerCase()) {
            case "turtle", "ttl" -> "text/turtle";
            case "jsonld", "json-ld" -> "application/ld+json";
            case "rdfxml", "rdf-xml", "xml" -> "application/rdf+xml";
            case "ntriples", "nt" -> "application/n-triples";
            case "nquads", "nq" -> "application/n-quads";
            case "trig" -> "application/trig";
            default -> "text/turtle"; // default to turtle
        };
    }

    /**
     * Get format from content type
     */
    public static String getFormatFromContentType(String contentType) {
        if (contentType == null) {
            return "turtle";
        }
        return switch (contentType.toLowerCase()) {
            case "text/turtle", "application/x-turtle" -> "turtle";
            case "application/ld+json", "application/json" -> "jsonld";
            case "application/rdf+xml" -> "rdfxml";
            case "application/n-triples" -> "ntriples";
            case "application/n-quads" -> "nquads";
            case "application/trig" -> "trig";
            default -> "turtle";
        };
    }

    private static Lang getLangFromFormat(String format) {
        return switch (format.toLowerCase()) {
            case "turtle", "ttl" -> Lang.TURTLE;
            case "jsonld", "json-ld" -> Lang.JSONLD;
            case "rdfxml", "rdf-xml", "xml" -> Lang.RDFXML;
            case "ntriples", "nt" -> Lang.NTRIPLES;
            case "nquads", "nq" -> Lang.NQUADS;
            case "trig" -> Lang.TRIG;
            default -> {
                logger.warn("Unknown format '{}', defaulting to Turtle", format);
                yield Lang.TURTLE;
            }
        };
    }

    private static RDFFormat getRdfFormatFromFormat(String format) {
        return switch (format.toLowerCase()) {
            case "turtle", "ttl" -> RDFFormat.TURTLE_PRETTY;
            case "jsonld", "json-ld" -> RDFFormat.JSONLD_PRETTY;
            case "rdfxml", "rdf-xml", "xml" -> RDFFormat.RDFXML_PRETTY;
            case "ntriples", "nt" -> RDFFormat.NTRIPLES;
            case "nquads", "nq" -> RDFFormat.NQUADS;
            case "trig" -> RDFFormat.TRIG_PRETTY;
            default -> {
                logger.warn("Unknown format '{}', defaulting to Turtle", format);
                yield RDFFormat.TURTLE_PRETTY;
            }
        };
    }

    /**
     * Validate RDF model
     */
    public static boolean validate(Model model) {
        // Basic validation - check if model is not null and has statements
        return model != null && model.size() > 0;
    }
}
