package com.fde.txn.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fde.txn.domain.RawTransaction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

/** Reads a JSON array (or a single object) of transaction requests from a local file. */
public class JsonFileTransactionSource implements TransactionSource {

    private final Path path;
    private final ObjectMapper objectMapper;

    public JsonFileTransactionSource(Path path, ObjectMapper objectMapper) {
        this.path = path;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<RawTransaction> read() {
        try {
            JsonNode root = objectMapper.readTree(path.toFile());
            return RawTransactionMapper.fromJson(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read transaction input file: " + path.getFileName(), e);
        }
    }
}
