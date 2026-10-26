package dev.mathproof.core;

import java.util.List;

public record VerificationResult(
        boolean success,
        String code,
        String message,
        List<ProofFact> facts,
        List<String> diagnostics
) {
    public VerificationResult {
        facts = List.copyOf(facts);
        diagnostics = List.copyOf(diagnostics);
    }
}
