package dev.mathproof.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses a deliberately small, explicit geometry notation. Unsupported syntax is rejected. */
public final class StatementParser {
    private static final Pattern SEGMENT = Pattern.compile("[A-Z]{2}");
    private static final Pattern POINT = Pattern.compile("[A-Z]");
    private static final Pattern ANGLE = Pattern.compile("[A-Z]{3}");
    private static final Pattern TRIANGLE = Pattern.compile("(?:△|\\btriangle\\s*)([A-Z]{3})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREDICATE = Pattern.compile("([A-Za-z][A-Za-z0-9_]*)\\s*\\(\\s*([^()]+?)\\s*\\)");

    private StatementParser() {}

    public static Statement parse(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("式を入力してください。");
        String s = input.trim().replace("＝", "=").replace("≠", "!=").replace("∧", " and ");
        s = s.replaceAll("\\s+", " ").trim();
        String lower = s.toLowerCase(Locale.ROOT);

        if (lower.equals("false") || s.equals("⊥") || lower.equals("contradiction") || lower.equals("矛盾")) {
            return Statement.atom(Statement.Kind.FALSE);
        }
        if (s.startsWith("¬")) return Statement.not(parse(s.substring(1).trim().replaceAll("^\\(|\\)$", "")));
        if (lower.startsWith("not ")) return Statement.not(parse(s.substring(4).trim().replaceAll("^\\(|\\)$", "")));

        int implication = indexOfAny(s, "->", "=>", "→", "⇒");
        if (implication >= 0) {
            String delimiter = s.startsWith("->", implication) || s.startsWith("=>", implication) ? s.substring(implication, implication + 2) : s.substring(implication, implication + 1);
            Statement left = parse(s.substring(0, implication).trim());
            Statement right = parse(s.substring(implication + delimiter.length()).trim());
            return Statement.implies(left, right);
        }

        Matcher collinear = Pattern.compile("(?i)^collinear\\s*\\(([^)]*)\\)$").matcher(s);
        if (collinear.matches()) return makeCollinear(collinear.group(1));
        collinear = Pattern.compile("(?i)^([A-Z](?:\\s*,\\s*[A-Z]){2,})\\s+(?:are\\s+)?collinear$").matcher(s);
        if (collinear.matches()) return makeCollinear(collinear.group(1));

        Matcher tri = TRIANGLE.matcher(s);
        if (tri.find() && isWholeTriangleExpression(s, tri.group(1))) {
            String vertices = tri.group(1).toUpperCase(Locale.ROOT);
            requireDistinctPoints(vertices, "三角形の頂点は異なる3点である必要があります。");
            return Statement.atom(Statement.Kind.TRIANGLE, vertices);
        }

        Matcher congruent = Pattern.compile("(?i)^\\s*(?:△|triangle\\s*)([A-Z]{3})\\s*(?:≅|~=|congruent\\s+to)\\s*(?:△|triangle\\s*)([A-Z]{3})\\s*$").matcher(s);
        if (congruent.matches()) {
            String a = congruent.group(1).toUpperCase(Locale.ROOT);
            String b = congruent.group(2).toUpperCase(Locale.ROOT);
            requireDistinctPoints(a, "左側の三角形の頂点は異なる必要があります。");
            requireDistinctPoints(b, "右側の三角形の頂点は異なる必要があります。");
            return Statement.atom(Statement.Kind.CONGRUENT, a, b);
        }

        int parallel = indexOfAny(s, "||", "∥", " parallel ");
        if (parallel >= 0) {
            String delimiter = s.startsWith("||", parallel) ? "||" : s.startsWith("∥", parallel) ? "∥" : " parallel ";
            String[] parts = s.split(Pattern.quote(delimiter), 2);
            if (parts.length != 2) throw new IllegalArgumentException("平行の式は AB ∥ CD の形で入力してください。");
            String a = cleanSegment(parts[0]);
            String b = cleanSegment(parts[1]);
            return Statement.relation(Statement.Kind.PARALLEL, a, b);
        }

        int inequality = indexOfAny(s, "!=", " not equal ");
        if (inequality >= 0) {
            String delimiter = s.startsWith("!=", inequality) ? "!=" : " not equal ";
            String[] parts = s.split(Pattern.quote(delimiter), 2);
            return Statement.not(parse(parts[0].trim() + " = " + parts[1].trim()));
        }

        int equals = s.indexOf('=');
        if (equals >= 0) {
            if (s.indexOf('=', equals + 1) >= 0) throw new IllegalArgumentException("等号は1つずつ入力してください。");
            String a = s.substring(0, equals).trim();
            String b = s.substring(equals + 1).trim();
            boolean angleMode = a.startsWith("∠") || b.startsWith("∠") || a.toLowerCase(Locale.ROOT).startsWith("angle ") || b.toLowerCase(Locale.ROOT).startsWith("angle ");
            if (angleMode) {
                a = cleanAngle(a);
                b = cleanAngle(b);
                if (!ANGLE.matcher(a).matches() || !ANGLE.matcher(b).matches()) throw new IllegalArgumentException("角度は ∠ABC = ∠DEF の形で入力してください。");
                return Statement.relation(Statement.Kind.ANGLE_EQ, a, b);
            }
            a = a.replaceAll("\\s+", "");
            b = b.replaceAll("\\s+", "");
            if (SEGMENT.matcher(a).matches() && SEGMENT.matcher(b).matches()) {
                return Statement.relation(Statement.Kind.LENGTH_EQ, a, b);
            }
            if (POINT.matcher(a).matches() && POINT.matcher(b).matches()) {
                return Statement.relation(Statement.Kind.POINT_EQ, a, b);
            }
            throw new IllegalArgumentException("等式は AB = AC、A = B、または ∠ABC = ∠DEF の形で入力してください。");
        }

        Matcher predicate = PREDICATE.matcher(s);
        if (predicate.matches()) {
            String name = predicate.group(1);
            String arg = predicate.group(2).replaceAll("\\s+", "");
            if (!arg.matches("[A-Za-z0-9_+\\-]+")) throw new IllegalArgumentException("述語の引数が不正です。");
            return Statement.atom(Statement.Kind.PREDICATE, name, arg);
        }

        throw new IllegalArgumentException("この記法には未対応です。例: AB = AC、∠ABC = ∠BCA、△ABC、AB ∥ CD、P(n)。");
    }

    private static boolean isWholeTriangleExpression(String original, String vertices) {
        String s = original.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        return s.equals("△" + vertices.toLowerCase(Locale.ROOT)) || s.equals("triangle" + vertices.toLowerCase(Locale.ROOT));
    }

    private static String cleanSegment(String input) {
        String value = input.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!SEGMENT.matcher(value).matches()) throw new IllegalArgumentException("直線は AB のように2点で指定してください。");
        if (value.charAt(0) == value.charAt(1)) throw new IllegalArgumentException("同じ点からなる線分は指定できません。");
        return value;
    }

    private static String cleanAngle(String input) {
        String value = input.trim().replace("∠", "").replaceFirst("(?i)^angle\\s*", "").replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return value;
    }

    private static Statement makeCollinear(String raw) {
        List<String> points = Arrays.stream(raw.split(",")).map(String::trim).map(s -> s.toUpperCase(Locale.ROOT)).toList();
        if (points.size() < 3 || points.stream().anyMatch(p -> !POINT.matcher(p).matches())) {
            throw new IllegalArgumentException("一直線上の点は collinear(A,B,C) の形で3点以上指定してください。");
        }
        if (points.stream().distinct().count() != points.size()) throw new IllegalArgumentException("一直線上の点は重複できません。");
        return Statement.atom(Statement.Kind.COLLINEAR, points.toArray(String[]::new));
    }

    private static void requireDistinctPoints(String vertices, String message) {
        if (vertices.chars().distinct().count() != vertices.length()) throw new IllegalArgumentException(message);
    }

    private static int indexOfAny(String s, String... candidates) {
        int best = -1;
        for (String candidate : candidates) {
            int i = s.indexOf(candidate);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }
}
