package dev.mathproof.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class TheoremLibrary {
    private final List<TheoremDefinition> definitions = new ArrayList<>();

    public TheoremLibrary() {
        definitions.addAll(defaultTheorems());
    }

    public List<TheoremDefinition> all() { return List.copyOf(definitions); }
    public List<TheoremDefinition> builtIns() { return definitions.stream().filter(t -> !t.userCreated()).toList(); }
    public List<TheoremDefinition> userTheorems() { return definitions.stream().filter(TheoremDefinition::userCreated).toList(); }
    public TheoremDefinition byId(String id) { return definitions.stream().filter(t -> t.id().equals(id)).findFirst().orElse(null); }

    public void addUserTheorem(TheoremDefinition theorem) {
        if (!theorem.userCreated()) throw new IllegalArgumentException("ユーザー定理として登録してください。");
        definitions.removeIf(old -> old.id().equals(theorem.id()));
        definitions.add(theorem);
    }

    public void addLoadedUserTheorem(TheoremDefinition theorem) {
        if (theorem.userCreated()) addUserTheorem(theorem);
    }

    public static List<TheoremDefinition> defaultTheorems() {
        List<TheoremDefinition> items = new ArrayList<>();
        add(items, "geometry.isosceles.base_angles", "二等辺三角形の底角定理", "幾何学/三角形/二等辺", 
                List.of("△ABC", "AB = AC"), "∠ABC = ∠BCA");
        add(items, "geometry.isosceles.converse", "底角が等しい三角形は二等辺三角形", "幾何学/三角形/二等辺",
                List.of("△ABC", "∠ABC = ∠BCA"), "AB = AC");
        add(items, "geometry.sss.congruence", "三辺相等による三角形の合同", "幾何学/三角形/合同",
                List.of("△ABC", "△DEF", "AB = DE", "BC = EF", "AC = DF"), "△ABC ≅ △DEF");
        add(items, "geometry.sas.congruence", "二辺夾角による三角形の合同", "幾何学/三角形/合同",
                List.of("△ABC", "△DEF", "AB = DE", "AC = DF", "∠BAC = ∠EDF"), "△ABC ≅ △DEF");
        add(items, "geometry.asa.congruence", "一辺とその両端の角による三角形の合同", "幾何学/三角形/合同",
                List.of("△ABC", "△DEF", "∠ABC = ∠DEF", "AB = DE", "∠BAC = ∠EDF"), "△ABC ≅ △DEF");
        add(items, "geometry.congruence.side_ab", "合同な三角形の対応する辺 AB = DE", "幾何学/三角形/合同",
                List.of("△ABC", "△DEF", "△ABC ≅ △DEF"), "AB = DE");
        add(items, "geometry.congruence.side_bc", "合同な三角形の対応する辺 BC = EF", "幾何学/三角形/合同",
                List.of("△ABC", "△DEF", "△ABC ≅ △DEF"), "BC = EF");
        add(items, "geometry.congruence.side_ac", "合同な三角形の対応する辺 AC = DF", "幾何学/三角形/合同",
                List.of("△ABC", "△DEF", "△ABC ≅ △DEF"), "AC = DF");
        add(items, "geometry.congruence.angle_a", "合同な三角形の対応する角 A = D", "幾何学/三角形/合同",
                List.of("△ABC", "△DEF", "△ABC ≅ △DEF"), "∠BAC = ∠EDF");
        add(items, "geometry.congruence.angle_b", "合同な三角形の対応する角 B = E", "幾何学/三角形/合同",
                List.of("△ABC", "△DEF", "△ABC ≅ △DEF"), "∠ABC = ∠DEF");
        add(items, "geometry.congruence.angle_c", "合同な三角形の対応する角 C = F", "幾何学/三角形/合同",
                List.of("△ABC", "△DEF", "△ABC ≅ △DEF"), "∠BCA = ∠EFD");
        add(items, "logic.modus_ponens", "含意の適用（modus ponens）", "論理/基本推論",
                List.of("P(n)", "P(n) -> Q(n)"), "Q(n)");
        return List.copyOf(items);
    }

    private static void add(Collection<TheoremDefinition> target, String id, String title, String category,
                            List<String> premiseTexts, String conclusionText) {
        List<Statement> premises = premiseTexts.stream().map(StatementParser::parse).toList();
        Statement conclusion = StatementParser.parse(conclusionText);
        target.add(new TheoremDefinition(id, title, category, premises, conclusion, false));
    }
}
