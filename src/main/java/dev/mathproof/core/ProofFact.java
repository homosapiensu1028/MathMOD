package dev.mathproof.core;

import java.util.List;
import java.util.Objects;

public final class ProofFact {
    public enum Origin { PREMISE, DERIVED, ASSUMPTION }
    private final String id;
    private final Statement statement;
    private final Origin origin;
    private final String explanation;
    private final List<String> dependencies;
    private int x;
    private int y;

    public ProofFact(String id, Statement statement, Origin origin, String explanation,
                     List<String> dependencies, int x, int y) {
        this.id = Objects.requireNonNull(id);
        this.statement = Objects.requireNonNull(statement);
        this.origin = Objects.requireNonNull(origin);
        this.explanation = explanation == null ? "" : explanation;
        this.dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
        this.x = x;
        this.y = y;
    }
    public String id() { return id; }
    public Statement statement() { return statement; }
    public Origin origin() { return origin; }
    public String explanation() { return explanation; }
    public List<String> dependencies() { return dependencies; }
    public int x() { return x; }
    public int y() { return y; }
    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public boolean isInitial() { return origin == Origin.PREMISE; }
}
