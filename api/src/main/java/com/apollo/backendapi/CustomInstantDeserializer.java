package com.apollo.backendapi;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class CustomInstantDeserializer extends JsonDeserializer<Instant> {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String timestamp = p.getText();
        try {
            // Parse using OffsetDateTime, which handles variable fractional seconds
            OffsetDateTime odt = OffsetDateTime.parse(timestamp, formatter);
            return odt.toInstant();
        } catch (DateTimeParseException e) {
            throw new IOException("Failed to parse Instant from value: " + timestamp, e);
        }
    }
}
