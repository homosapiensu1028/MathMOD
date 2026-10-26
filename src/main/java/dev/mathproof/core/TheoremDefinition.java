package dev.mathproof.core;

import java.util.List;

public record TheoremDefinition(
        String id,
        String title,
        String category,
        List<Statement> premises,
        Statement conclusion,
        boolean userCreated
) {
    public TheoremDefinition {
        premises = List.copyOf(premises);
        if (id == null || id.isBlank()) throw new IllegalArgumentException("定理IDが空です。");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("定理名が空です。");
        if (category == null || category.isBlank()) category = "ユーザー定理";
    }
}
