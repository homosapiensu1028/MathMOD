package dev.mathproof.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Immutable, normalized mathematical statement. UI text is never used as a proof key. */
public final class Statement {
    public enum Kind {
        LENGTH_EQ, POINT_EQ, ANGLE_EQ, TRIANGLE, PARALLEL, COLLINEAR,
        CONGRUENT, NOT, IMPLIES, PREDICATE, FALSE
    }

    private final Kind kind;
    private final List<String> terms;
    private final Statement left;
    private final Statement right;
    private final String key;

    private Statement(Kind kind, List<String> terms, Statement left, Statement right) {
        this.kind = Objects.requireNonNull(kind);
        this.terms = List.copyOf(terms == null ? List.of() : terms);
        this.left = left;
        this.right = right;
        this.key = makeKey();
    }

    public static Statement atom(Kind kind, String... terms) {
        List<String> values = new ArrayList<>();
        Collections.addAll(values, terms);
        return new Statement(kind, values, null, null);
    }

    public static Statement relation(Kind kind, String a, String b) {
        String first = normalizeOperand(kind, a);
        String second = normalizeOperand(kind, b);
        if (first.compareTo(second) > 0) {
            String tmp = first;
            first = second;
            second = tmp;
        }
        return atom(kind, first, second);
    }

    public static Statement not(Statement statement) {
        if (statement.kind == Kind.NOT) return statement.left;
        return new Statement(Kind.NOT, List.of(), statement, null);
    }

    public static Statement implies(Statement antecedent, Statement consequent) {
        return new Statement(Kind.IMPLIES, List.of(), antecedent, consequent);
    }

    private static String normalizeOperand(Kind kind, String value) {
        if (kind == Kind.LENGTH_EQ || kind == Kind.POINT_EQ || kind == Kind.PARALLEL) {
            if (value.length() == 2 && Character.isUpperCase(value.charAt(0)) && Character.isUpperCase(value.charAt(1))) {
                char a = value.charAt(0), b = value.charAt(1);
                return a <= b ? "" + a + b : "" + b + a;
            }
        }
        if (kind == Kind.ANGLE_EQ && value.length() == 3) {
            char a = value.charAt(0), vertex = value.charAt(1), c = value.charAt(2);
            return a <= c ? "" + a + vertex + c : "" + c + vertex + a;
        }
        return value;
    }

    private String makeKey() {
        return switch (kind) {
            case NOT -> "NOT(" + left.key + ")";
            case IMPLIES -> "IMPLIES(" + left.key + "," + right.key + ")";
            case FALSE -> "FALSE";
            case TRIANGLE -> "TRIANGLE(" + terms.get(0).chars().mapToObj(c -> String.valueOf((char) c)).sorted().collect(Collectors.joining()) + ")";
            case COLLINEAR -> "COLLINEAR(" + terms.stream().sorted().collect(Collectors.joining()) + ")";
            case LENGTH_EQ, POINT_EQ, ANGLE_EQ, PARALLEL -> kind + "(" + terms.get(0) + "," + terms.get(1) + ")";
            case CONGRUENT -> "CONGRUENT(" + terms.get(0) + "," + terms.get(1) + ")";
            case PREDICATE -> "PREDICATE(" + String.join(",", terms) + ")";
        };
    }

    public Kind kind() { return kind; }
    public List<String> terms() { return terms; }
    public Statement left() { return left; }
    public Statement right() { return right; }
    public String key() { return key; }


    public Statement substitute(java.util.Map<String, String> substitution) {
        if (kind == Kind.NOT) return Statement.not(left.substitute(substitution));
        if (kind == Kind.IMPLIES) return Statement.implies(left.substitute(substitution), right.substitute(substitution));
        if (kind == Kind.FALSE) return this;
        List<String> replaced = new ArrayList<>();
        for (String term : terms) {
            if (isGeometricTerm(kind, term)) {
                StringBuilder out = new StringBuilder();
                for (int i = 0; i < term.length(); i++) {
                    String symbol = String.valueOf(term.charAt(i));
                    out.append(substitution.getOrDefault(symbol, symbol));
                }
                replaced.add(out.toString());
            } else {
                replaced.add(term);
            }
        }
        if (kind == Kind.LENGTH_EQ || kind == Kind.POINT_EQ || kind == Kind.PARALLEL || kind == Kind.ANGLE_EQ) {
            return Statement.relation(kind, replaced.get(0), replaced.get(1));
        }
        return Statement.atom(kind, replaced.toArray(String[]::new));
    }

    private static boolean isGeometricTerm(Kind kind, String term) {
        return switch (kind) {
            case LENGTH_EQ, POINT_EQ, ANGLE_EQ, TRIANGLE, PARALLEL, COLLINEAR, CONGRUENT -> true;
            default -> false;
        };
    }

    public java.util.Set<String> geometricSymbols() {
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        if (kind == Kind.NOT) result.addAll(left.geometricSymbols());
        else if (kind == Kind.IMPLIES) { result.addAll(left.geometricSymbols()); result.addAll(right.geometricSymbols()); }
        else if (isGeometricTerm(kind, "")) {
            for (String term : terms) for (int i = 0; i < term.length(); i++) {
                char c = term.charAt(i);
                if (c >= 'A' && c <= 'Z') result.add(String.valueOf(c));
            }
        }
        return result;
    }

    public Statement withPredicateArgument(String argument) {
        if (kind != Kind.PREDICATE || terms.size() != 2) throw new IllegalStateException("Not a unary indexed predicate");
        return atom(Kind.PREDICATE, terms.get(0), argument);
    }

    public String formula() {
        return switch (kind) {
            case LENGTH_EQ -> terms.get(0) + " = " + terms.get(1);
            case POINT_EQ -> terms.get(0) + " = " + terms.get(1);
            case ANGLE_EQ -> "∠" + terms.get(0) + " = ∠" + terms.get(1);
            case TRIANGLE -> "△" + terms.get(0);
            case PARALLEL -> terms.get(0) + " ∥ " + terms.get(1);
            case COLLINEAR -> "collinear(" + String.join(",", terms) + ")";
            case CONGRUENT -> "△" + terms.get(0) + " ≅ △" + terms.get(1);
            case NOT -> "¬(" + left.formula() + ")";
            case IMPLIES -> left.formula() + " → " + right.formula();
            case PREDICATE -> terms.get(0) + "(" + terms.get(1) + ")";
            case FALSE -> "矛盾 (⊥)";
        };
    }

    @Override public boolean equals(Object other) {
        return other instanceof Statement statement && key.equals(statement.key);
    }
    @Override public int hashCode() { return key.hashCode(); }
    @Override public String toString() { return formula(); }
}
