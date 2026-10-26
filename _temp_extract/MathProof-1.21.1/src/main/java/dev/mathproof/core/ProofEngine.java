package dev.mathproof.core;

import java.util.*;

/** Deterministic proof checker for the explicitly supported geometry fragment. */
public final class ProofEngine {
    public record ApplyResult(boolean success, Statement conclusion, Map<String, String> substitution,
                              List<String> usedFactIds, String message) {}

    public VerificationResult verify(ProofSession session, TheoremLibrary library) {
        if (session.mode() != ProofSession.Mode.DIRECT) {
            String message = session.mode() == ProofSession.Mode.CONTRADICTION
                    ? "背理法モードの完全検証はまだ有効化されていません。局所仮定の導入・解除が未実装のため、定理登録を拒否しました。"
                    : "帰納法モードの完全検証はまだ有効化されていません。基底ケースと帰納ステップの証明オブジェクトが未実装のため、定理登録を拒否しました。";
            return new VerificationResult(false, "UNSUPPORTED_PROOF_MODE", message, session.facts(), List.of());
        }
        final Statement goal;
        try { goal = StatementParser.parse(session.goalText()); }
        catch (RuntimeException e) {
            return new VerificationResult(false, "INVALID_GOAL", "証明目標を解析できません: " + e.getMessage(), session.facts(), List.of());
        }
        Map<String, ProofFact> byId = new LinkedHashMap<>();
        Map<String, ProofFact> byKey = new LinkedHashMap<>();
        for (ProofFact fact : session.facts()) {
            if (byId.put(fact.id(), fact) != null) return fail("DUPLICATE_FACT_ID", "命題IDが重複しています。", session, List.of(fact.id()));
            if (fact.origin() != ProofFact.Origin.ASSUMPTION) byKey.putIfAbsent(fact.statement().key(), fact);
        }
        Set<String> verified = new HashSet<>(), visiting = new HashSet<>();
        for (ProofFact fact : session.facts()) {
            if (fact.origin() == ProofFact.Origin.ASSUMPTION) return fail("UNRESOLVED_ASSUMPTION", "解除されていない局所仮定があります。", session, List.of(fact.id()));
            if (!verifyFact(fact, byId, library, verified, visiting)) {
                return fail("UNJUSTIFIED_FACT", "命題「" + fact.statement().formula() + "」の根拠を検証できません。", session,
                        List.of(fact.id(), fact.explanation()));
            }
        }
        ProofFact goalFact = byKey.get(goal.key());
        if (goalFact == null) return fail("GOAL_NOT_PROVED", "証明目標が導出されていません。定理を適用して目標を導いてください。", session,
                List.of("目標: " + goal.formula()));
        session.setVerified(true);
        return new VerificationResult(true, "OK", "検証成功：目標命題までの依存関係と各定理適用を再検証しました。", session.facts(), List.of());
    }

    private boolean verifyFact(ProofFact fact, Map<String, ProofFact> byId, TheoremLibrary library,
                               Set<String> verified, Set<String> visiting) {
        if (fact.origin() == ProofFact.Origin.PREMISE) return fact.dependencies().isEmpty();
        if (verified.contains(fact.id())) return true;
        if (!visiting.add(fact.id())) return false;
        if (fact.origin() != ProofFact.Origin.DERIVED) return false;
        List<ProofFact> dependencyFacts = new ArrayList<>();
        for (String depId : fact.dependencies()) {
            ProofFact dep = byId.get(depId);
            if (dep == null || !verifyFact(dep, byId, library, verified, visiting)) return false;
            dependencyFacts.add(dep);
        }
        boolean valid;
        if (fact.explanation().startsWith("theorem:")) {
            String theoremId = fact.explanation().substring("theorem:".length());
            TheoremDefinition theorem = library.byId(theoremId);
            if (theorem == null || fact.dependencies().isEmpty()) return false;
            ApplyResult result = apply(theorem, dependencyFacts);
            valid = result.success() && result.conclusion().equals(fact.statement());
        } else if (fact.explanation().equals("rule:transitivity")) {
            valid = dependencyFacts.size() == 2 && transitiveConclusion(dependencyFacts.get(0).statement(), dependencyFacts.get(1).statement())
                    .map(fact.statement()::equals).orElse(false);
        } else return false;
        if (!valid) return false;
        visiting.remove(fact.id());
        verified.add(fact.id());
        return true;
    }


    public Optional<ApplyResult> applyTransitivity(List<ProofFact> facts) {
        for (int i = 0; i < facts.size(); i++) {
            ProofFact first = facts.get(i);
            if (first.origin() == ProofFact.Origin.ASSUMPTION) continue;
            for (int j = i + 1; j < facts.size(); j++) {
                ProofFact second = facts.get(j);
                if (second.origin() == ProofFact.Origin.ASSUMPTION) continue;
                Optional<Statement> conclusion = transitiveConclusion(first.statement(), second.statement());
                if (conclusion.isPresent() && facts.stream().noneMatch(f -> f.statement().equals(conclusion.get()))) {
                    return Optional.of(new ApplyResult(true, conclusion.get(), Map.of(), List.of(first.id(), second.id()),
                            "等式の推移律を適用しました: " + conclusion.get().formula()));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Statement> transitiveConclusion(Statement first, Statement second) {
        if (first.kind() != second.kind()) return Optional.empty();
        if (first.kind() != Statement.Kind.LENGTH_EQ && first.kind() != Statement.Kind.ANGLE_EQ
                && first.kind() != Statement.Kind.POINT_EQ) return Optional.empty();
        List<String> a = first.terms(), b = second.terms();
        if (a.size() != 2 || b.size() != 2) return Optional.empty();
        String left = null, right = null;
        if (a.get(0).equals(b.get(0))) { left = a.get(1); right = b.get(1); }
        else if (a.get(0).equals(b.get(1))) { left = a.get(1); right = b.get(0); }
        else if (a.get(1).equals(b.get(0))) { left = a.get(0); right = b.get(1); }
        else if (a.get(1).equals(b.get(1))) { left = a.get(0); right = b.get(0); }
        if (left == null || left.equals(right)) return Optional.empty();
        return Optional.of(Statement.relation(first.kind(), left, right));
    }

    public ApplyResult apply(TheoremDefinition theorem, List<ProofFact> facts) {
        List<ProofFact> usable = facts.stream().filter(f -> f.origin() != ProofFact.Origin.ASSUMPTION).toList();
        if (theorem.premises().isEmpty()) return new ApplyResult(false, null, Map.of(), List.of(), "前提のない定理はこの画面から適用できません。");
        SearchResult result = searchPremises(theorem, usable, 0, new LinkedHashMap<>(), new ArrayList<>());
        if (result == null) return new ApplyResult(false, null, Map.of(), List.of(), "定理の前提条件が不足しています。黒板上の事実と記号対応を確認してください。");
        Statement conclusion = theorem.conclusion().substitute(result.mapping);
        return new ApplyResult(true, conclusion, Map.copyOf(result.mapping), List.copyOf(result.factIds), "定理を適用しました：" + conclusion.formula());
    }

    private SearchResult searchPremises(TheoremDefinition theorem, List<ProofFact> facts, int index,
                                        Map<String, String> mapping, List<String> used) {
        if (index >= theorem.premises().size()) return new SearchResult(new LinkedHashMap<>(mapping), new ArrayList<>(used));
        Statement pattern = theorem.premises().get(index);
        for (ProofFact fact : facts) {
            if (used.contains(fact.id())) continue;
            Map<String, String> candidate = new LinkedHashMap<>(mapping);
            if (!unify(pattern, fact.statement(), candidate)) continue;
            List<String> nextUsed = new ArrayList<>(used);
            nextUsed.add(fact.id());
            SearchResult found = searchPremises(theorem, facts, index + 1, candidate, nextUsed);
            if (found != null) return found;
        }
        return null;
    }

    private boolean unify(Statement pattern, Statement target, Map<String, String> mapping) {
        if (pattern.kind() != target.kind()) return false;
        if (pattern.kind() == Statement.Kind.NOT) return unify(pattern.left(), target.left(), mapping);
        if (pattern.kind() == Statement.Kind.IMPLIES) return unify(pattern.left(), target.left(), mapping) && unify(pattern.right(), target.right(), mapping);
        if (pattern.terms().size() != target.terms().size()) return false;
        boolean geometric = switch (pattern.kind()) {
            case LENGTH_EQ, POINT_EQ, ANGLE_EQ, TRIANGLE, PARALLEL, COLLINEAR, CONGRUENT -> true;
            default -> false;
        };
        for (int i = 0; i < pattern.terms().size(); i++) {
            String a = pattern.terms().get(i), b = target.terms().get(i);
            if (geometric && a.length() == b.length()) {
                for (int j = 0; j < a.length(); j++) {
                    String from = String.valueOf(a.charAt(j)), to = String.valueOf(b.charAt(j));
                    String prior = mapping.get(from);
                    if (prior != null && !prior.equals(to)) return false;
                    mapping.put(from, to);
                }
            } else if (!a.equals(b)) return false;
        }
        return true;
    }

    private VerificationResult fail(String code, String message, ProofSession session, List<String> diagnostics) {
        session.setVerified(false);
        return new VerificationResult(false, code, message, session.facts(), diagnostics);
    }
    private record SearchResult(Map<String, String> mapping, List<String> factIds) {}
}
