package dev.mathproof.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProofEngineTest {
    @Test void parserNormalizesReversedSegments() {
        assertEquals(StatementParser.parse("AB = AC"), StatementParser.parse("BA = CA"));
    }

    @Test void theoremAppliesWithDifferentPointNames() {
        TheoremLibrary library = new TheoremLibrary();
        ProofSession session = new ProofSession();
        session.addPremise(StatementParser.parse("△PQR"));
        session.addPremise(StatementParser.parse("PQ = PR"));
        TheoremDefinition theorem = library.byId("geometry.isosceles.base_angles");
        ProofEngine.ApplyResult result = new ProofEngine().apply(theorem, session.facts());
        assertTrue(result.success(), result.message());
        assertEquals(StatementParser.parse("∠PQR = ∠QRP"), result.conclusion());
    }

    @Test void missingPremiseDoesNotApplyTheorem() {
        ProofSession session = new ProofSession();
        session.addPremise(StatementParser.parse("△PQR"));
        ProofEngine.ApplyResult result = new ProofEngine().apply(
                new TheoremLibrary().byId("geometry.isosceles.base_angles"), session.facts());
        assertFalse(result.success());
    }

    @Test void proofMustContainGoal() {
        ProofSession session = new ProofSession();
        session.addPremise(StatementParser.parse("AB = AC"));
        session.setGoalText("∠ABC = ∠BCA");
        VerificationResult result = new ProofEngine().verify(session, new TheoremLibrary());
        assertFalse(result.success());
        assertEquals("GOAL_NOT_PROVED", result.code());
    }

    @Test void theoremApplicationHasVerifiableDependencyChain() {
        TheoremLibrary library = new TheoremLibrary();
        ProofSession session = new ProofSession();
        session.addPremise(StatementParser.parse("△PQR"));
        session.addPremise(StatementParser.parse("PQ = PR"));
        TheoremDefinition theorem = library.byId("geometry.isosceles.base_angles");
        ProofEngine.ApplyResult applied = new ProofEngine().apply(theorem, session.facts());
        session.facts().add(new ProofFact("derived-1", applied.conclusion(), ProofFact.Origin.DERIVED,
                "theorem:" + theorem.id(), applied.usedFactIds(), 300, 150));
        session.setGoalText("∠PQR = ∠QRP");
        VerificationResult result = new ProofEngine().verify(session, library);
        assertTrue(result.success(), result.message());
    }

    @Test void transitivityDerivesEqualityAndCanBeVerified() {
        TheoremLibrary library = new TheoremLibrary();
        ProofSession session = new ProofSession();
        session.addPremise(StatementParser.parse("AB = AC"));
        session.addPremise(StatementParser.parse("AC = AD"));
        ProofEngine engine = new ProofEngine();
        var applied = engine.applyTransitivity(session.facts());
        assertTrue(applied.isPresent());
        session.facts().add(new ProofFact("r1", applied.get().conclusion(), ProofFact.Origin.DERIVED,
                "rule:transitivity", applied.get().usedFactIds(), 300, 150));
        session.setGoalText("AB = AD");
        VerificationResult result = engine.verify(session, library);
        assertTrue(result.success(), result.message());
    }

    @Test void collinearFormulaRoundTrips() {
        Statement original = StatementParser.parse("collinear(A,B,C)");
        assertEquals(original, StatementParser.parse(original.formula()));
    }

    @Test void unimplementedProofModesNeverPassVerification() {
        ProofSession session = new ProofSession();
        session.setMode(ProofSession.Mode.CONTRADICTION);
        session.setGoalText("A = B");
        VerificationResult result = new ProofEngine().verify(session, new TheoremLibrary());
        assertFalse(result.success());
        assertEquals("UNSUPPORTED_PROOF_MODE", result.code());
    }
}
