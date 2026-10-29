package com.lol.analyzer.analysis;

import java.util.List;

/**
 * Input or lookup problems shown above the form (unknown champion, player not found, duplicate lane, ...).
 */
public class AnalysisException extends RuntimeException {

    private final List<String> errors;

    public AnalysisException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public AnalysisException(String error) {
        this(List.of(error));
    }

    public List<String> getErrors() {
        return errors;
    }
}
