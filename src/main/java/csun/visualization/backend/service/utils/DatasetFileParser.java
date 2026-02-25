package csun.visualization.backend.service.utils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import csun.visualization.backend.dto.DataPointDto;

@Component
public class DatasetFileParser {
    private final ObjectMapper objectMapper;
    private final CsvMapper csvMapper;
    private final CsvSchema csvSchema;

    public DatasetFileParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.csvMapper = new CsvMapper();
        this.csvSchema = CsvSchema.emptySchema().withHeader();
        this.csvMapper.enable(CsvParser.Feature.SKIP_EMPTY_LINES);
        this.csvMapper.enable(CsvParser.Feature.TRIM_SPACES);
    }

    public enum Format { JSON, CSV }

    /**
     * Checks if the given stream is in JSON or CSV format.
     * 
     * @param input the contents of the file as an input stream
     * @return the file format, either JSON or CSV
     */
    public Format checkFormat(InputStream input) throws IOException {
        if (!input.markSupported()) throw new IllegalArgumentException("InputStream must support mark/reset");
        // Mark the current position in the stream to reset later after checking the first JSON token
        input.mark(8 * 1024);
        try {
            int character;
            do {
                character = input.read();
                if(character == -1) throw new IllegalArgumentException("File is empty");
            } while (Character.isWhitespace(character));
            
            // First characcter must be [ for expected JSON dataset
            return character == '[' ? Format.JSON : Format.CSV;
        } finally {
            input.reset();
        }
    }

    /**
     * Route to the correct parser depending on file type.
     * @param input the file as input stream
     * @param format the format of the file
     * @param maxPoints maximum Java heap memory runtime limit
     * @return a list of DataPointDto objects
     */
    public List<DataPointDto> parse(InputStream input, Format format, int maxPoints) {
        if (input == null) throw new IllegalArgumentException("File is empty");
        return (format == Format.JSON)
                ? parseJson(input, maxPoints)
                : parseCsv(input, maxPoints);
    }

    /**
     * Parses the given JSON input stream and returns a list of DataPointDto objects.
     * 
     * @param input the contents of the file as an input stream
     * @param maxPoints the maximum Java heap memory runtime limit 
     * @return a list of DataPointDto objects
     */
    private List<DataPointDto> parseJson(InputStream input, int maxPoints) {
        try (JsonParser p = objectMapper.createParser(input)) {
            // Read the first token in the file
            JsonToken first = p.nextToken();
            if (first == null) {
                throw new IllegalArgumentException("File is empty");
            }
            // throw error if file is not in expected JSON format
            if (first != JsonToken.START_ARRAY) {
                throw new IllegalArgumentException("JSON must be an array of data points");
            }
            // Initialize list of DataPointDto objects
            List<DataPointDto> datapoints = new ArrayList<>();
            int count = 0;
            // Iterate through each JSON object from the array
            while (p.nextToken() != JsonToken.END_ARRAY) {
                // Read from the stream into a JsonNode
                JsonNode n = objectMapper.readTree(p);
                // Initialize the DTO and set values from row
                DataPointDto dto = new DataPointDto();
                JsonNode ts = n.get("timestamp");
                if (ts == null || ts.isNull()) throw new IllegalArgumentException("Missing timestamp");
                dto.setTimestamp(parseTimestamp(ts));
                dto.setX(requireDouble(n, "x"));
                dto.setY(requireDouble(n, "y"));
                dto.setZ(requireDouble(n, "z"));
                // Validate the values
                validatePoint(dto);
                // Add dto to the list
                datapoints.add(dto);
                // Ensure we are not exceeding limit to prevent OOM
                if (++count > maxPoints) {
                    throw new IllegalArgumentException("Too many data points");
                }
            }
            if (datapoints.isEmpty()) throw new IllegalArgumentException("No data points found");
            return datapoints;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON", e);
        }
    }

    /**
     * Parses the given CSV input stream and returns a list of DataPointDto objects.
     * 
     * @param input the contents of the file as an input stream
     * @param maxPoints the maximum Java heap memory runtime limit 
     * @return a list of DataPointDto objects
     */
    private List<DataPointDto> parseCsv(InputStream input, int maxPoints) {
        try {
            // Create iterator to read rows from CSV as Map
            // CsvMapper reads the first CSV row and treats it as header
            MappingIterator<Map<String, String>> iterator =
                    csvMapper.readerFor(Map.class)
                            .with(csvSchema)
                            .readValues(input);
            // Initialize list of DataPointDto objects
            List<DataPointDto> datapoints = new ArrayList<>();
            int count = 0;
            // Keep track of row, row 1 is header
            int row = 1;
            // Iterate through each CSV row
            while (iterator.hasNext()) {
                row++;
                // Gets the next element in the iteration
                Map<String, String> dataRow = iterator.next();
                // Convert the header names to lowercase and trim whitespace
                Map<String, String> normalizedRow = normalizeCsvMapperRow(dataRow);
                // Initialize the DTO and set values from row
                DataPointDto dto = new DataPointDto();
                dto.setTimestamp(parseCsvTimestamp(validateCsvField(normalizedRow, "timestamp", row)));
                dto.setX(parseCsvDouble(validateCsvField(normalizedRow, "x", row), "x", row));
                dto.setY(parseCsvDouble(validateCsvField(normalizedRow, "y", row), "y", row));
                dto.setZ(parseCsvDouble(validateCsvField(normalizedRow, "z", row), "z", row));
                // Validate the values
                validatePoint(dto);
                // Add dto to the list
                datapoints.add(dto);
                // Ensure we are not exceeding point limit to prevent OOM
                if (++count > maxPoints) {
                    throw new IllegalArgumentException("Too many data points");
                }
            }
            if (datapoints.isEmpty()) throw new IllegalArgumentException("No data points found");
            return datapoints;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read CSV", e);
        }
    }

    // Helper methods

    /**
     * Converts a given timestamp from a JsonNode to an Instant.
     * @param ts The timestamp as a JsonNode
     * @return The timestamp as an Instant
     */
    private Instant parseTimestamp(JsonNode ts) {
        if (ts.isNumber()) return Instant.ofEpochMilli(ts.asLong());
        if (ts.isTextual()) return Instant.parse(ts.asText());
        throw new IllegalArgumentException("timestamp must be epoch millis (number) or ISO-8601 string");
    }

    /**
     * Converts a given timestamp to an Instant.
     * @param ts The timestamp as String
     * @return The timestamp as an Instant
     */
    private Instant parseCsvTimestamp(String ts) {
        ts = ts.trim();
        try {
            long ms = Long.parseLong(ts);
            return Instant.ofEpochMilli(ms);
        } catch (NumberFormatException e) {
            return Instant.parse(ts);
        }
    }

    /**
     * Converts a given value from a JsonNode to a Double.
     * @param value The value as a JsonNode
     * @param field The field of the object node
     * @return The value as a Double
     */
    private Double requireDouble(JsonNode value, String field) {
        JsonNode v = value.get(field);
        if (v == null || v.isNull()) throw new IllegalArgumentException("Missing " + field);
        if (!v.isNumber()) throw new IllegalArgumentException(field + " must be a number");
        return v.asDouble();
    }

    /**
     * Converts a given value from a String to a Double.
     * @param value The value as a String
     * @param field The field of the object node
     * @param row 
     * @return The value as a Double
     */
    private Double parseCsvDouble(String value, String field, int row) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + field + " at row " + row);
        }
    }

    /**
     * Trims and converts the given header names to lowercase. This is in case the CSV file
     * headers are not the expected "timestamp", "x", "y", "z". CsvMapper reads the first 
     * CSV row and treats it like a header. So for each row, it produces a Map with the 
     * headers as keys mapped to the row data.
     * 
     * @param row The Map of the row given by CsvMapper
     * @return A new map of the row with normalized header keys
     */
    private Map<String, String> normalizeCsvMapperRow(Map<String, String> row) {
        Map<String, String> normalizedMap = new HashMap<>();
        row.forEach((k, v) -> normalizedMap.put(k.trim().toLowerCase(Locale.ROOT), v));
        return normalizedMap;
    }

    /**
     * Validates the CSV field and returns a String value of the field.
     * 
     * @param row The Map of the row given by CsvMapper
     * @param field the field name
     * @param r the current row number
     * @return String value of the field
     */
    private String validateCsvField(Map<String, String> row, String field, int r) {
        String v = row.get(field);

        // Missing or blank -> fail fast
        if (v == null || v.trim().isEmpty())
            throw new IllegalArgumentException("Missing " + field + " at row " + r);

        return v; // Return the raw string cell value
    }

    /**
     * Validates the given DTO to ensure timestamp exists and points are finite
     * @param dto The mapped DataPointDto
     */
    private void validatePoint(DataPointDto dto) {
        if (dto.getTimestamp() == null) throw new IllegalArgumentException("timestamp is required");
        if (dto.getX() == null || dto.getY() == null || dto.getZ() == null) {
            throw new IllegalArgumentException("x, y, z are required");
        }
        if (!Double.isFinite(dto.getX()) || !Double.isFinite(dto.getY()) || !Double.isFinite(dto.getZ())) {
            throw new IllegalArgumentException("x, y, z must be finite numbers");
        }
    }
}
