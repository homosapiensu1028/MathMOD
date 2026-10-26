package dev.mathproof.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class ProofSession {
    public enum Mode { DIRECT, CONTRADICTION, INDUCTION }

    private final List<ProofFact> facts = new ArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private String goalText = "";
    private Mode mode = Mode.DIRECT;
    private String status = "前提条件を追加し、証明したい命題を設定してください。";
    private boolean verified;

    public List<ProofFact> facts() { return facts; }
    public List<ProofFact> initialFacts() { return facts.stream().filter(ProofFact::isInitial).toList(); }
    public String goalText() { return goalText; }
    public void setGoalText(String goalText) { this.goalText = goalText == null ? "" : goalText.trim(); invalidate(); }
    public Mode mode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; invalidate(); }
    public String status() { return status; }
    public void setStatus(String status) { this.status = status == null ? "" : status; }
    public boolean verified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public ProofFact addPremise(Statement statement) {
        for (ProofFact fact : initialFacts()) if (fact.statement().equals(statement)) return fact;
        int idx = initialFacts().size();
        ProofFact fact = new ProofFact("p-" + nextId.getAndIncrement(), statement, ProofFact.Origin.PREMISE,
                "初期前提", List.of(), 250 + (idx % 2) * 210, 120 + (idx / 2) * 55);
        facts.add(fact);
        invalidate();
        return fact;
    }

    public void replaceDerived(List<ProofFact> results) {
        facts.removeIf(f -> f.origin() != ProofFact.Origin.PREMISE);
        for (ProofFact fact : results) {
            if (fact.origin() == ProofFact.Origin.PREMISE) continue;
            facts.add(fact);
        }
    }

    public void clear() {
        facts.clear();
        goalText = "";
        status = "黒板を消去しました。";
        verified = false;
        nextId.set(1);
    }

    public void invalidate() {
        verified = false;
        if (status.startsWith("検証成功")) status = "内容が変更されたため、再検証が必要です。";
    }

    public String newDerivedId(String prefix, String key) {
        return prefix + Long.toUnsignedString((key + nextId.getAndIncrement()).hashCode(), 36);
    }

    public void normalizePositions(int minX, int minY) {
        facts.sort(Comparator.comparing((ProofFact f) -> f.origin().ordinal()).thenComparing(ProofFact::id));
        int i = 0;
        for (ProofFact fact : facts) {
            if (fact.x() < minX || fact.y() < minY) fact.setPosition(minX + (i % 2) * 205, minY + (i / 2) * 55);
            i++;
        }
    }
}
