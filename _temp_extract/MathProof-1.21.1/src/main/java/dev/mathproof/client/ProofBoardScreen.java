package dev.mathproof.client;

import com.google.gson.*;
import dev.mathproof.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Client-side proof canvas. All mathematical acceptance decisions are delegated to ProofEngine. */
public final class ProofBoardScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ProofEngine ENGINE = new ProofEngine();
    private static final TheoremLibrary LIBRARY = new TheoremLibrary();
    private static ProofSession session = new ProofSession();
    private static String persistenceError = "";

    private EditBox premiseInput;
    private EditBox goalInput;
    private Button theoremButton;
    private Button categoryButton;
    private String selectedCategory = "すべて";
    private int selectedTheorem;
    private ProofFact dragged;
    private int dragOffsetX, dragOffsetY;
    private String draggingToken;
    private int tokenStartX, tokenStartY;
    private PlacedToken draggedPlacedToken;
    private int placedOffsetX, placedOffsetY;
    private static final List<PlacedToken> placedTokens = new ArrayList<>();
    private static final List<ResourceToken> TOKENS = List.of(
            new ResourceToken("線分等式", "AB = AC"),
            new ResourceToken("角度等式", "∠ABC = ∠BCA"),
            new ResourceToken("三角形", "△ABC"),
            new ResourceToken("平行", "AB ∥ CD"),
            new ResourceToken("一直線上", "collinear(A,B,C)"),
            new ResourceToken("等号 =", " = "),
            new ResourceToken("角 ∠", "∠"),
            new ResourceToken("三角形 △", "△"),
            new ResourceToken("平行 ∥", " ∥ "),
            new ResourceToken("否定 ¬", "¬")
    );
    private String notice = "前提を追加し、証明目標を入力してください。";
    private int noticeColor = 0xFFE0E0E0;
    private List<ProofFact> visibleFacts = List.of();

    public ProofBoardScreen() {
        super(Component.literal("数学証明黒板"));
        loadAll();
    }

    @Override
    protected void init() {
        int left = 12;
        int bottom = this.height - 34;
        premiseInput = new EditBox(font, left, bottom - 70, 205, 20, Component.literal("前提条件"));
        premiseInput.setMaxLength(120);
        premiseInput.setHint(Component.literal("例: AB = AC"));
        addRenderableWidget(premiseInput);
        goalInput = new EditBox(font, left, bottom - 25, 205, 20, Component.literal("証明したい命題"));
        goalInput.setMaxLength(120);
        goalInput.setValue(session.goalText());
        goalInput.setHint(Component.literal("例: ∠ABC = ∠BCA"));
        addRenderableWidget(goalInput);

        addRenderableWidget(Button.builder(Component.literal("＋ 前提を追加"), b -> addPremise()).bounds(left, bottom - 47, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("目標を設定"), b -> setGoal()).bounds(left + 105, bottom - 47, 112, 20).build());
        categoryButton = Button.builder(Component.literal("分類: すべて"), b -> cycleCategory()).bounds(left, bottom - 145, 205, 20).build();
        addRenderableWidget(categoryButton);
        theoremButton = Button.builder(Component.literal("定理を選択"), b -> cycleTheorem()).bounds(left, bottom - 95, 205, 20).build();
        addRenderableWidget(theoremButton);
        addRenderableWidget(Button.builder(Component.literal("定理を適用"), b -> applySelectedTheorem()).bounds(left, bottom - 120, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("推移律"), b -> applyTransitivity()).bounds(left + 105, bottom - 120, 55, 20).build());
        addRenderableWidget(Button.builder(Component.literal("検証"), b -> verify()).bounds(left + 165, bottom - 120, 52, 20).build());
        addRenderableWidget(Button.builder(Component.literal("証明法: " + modeLabel()), b -> cycleMode(b)).bounds(left, 38, 205, 20).build());
        addRenderableWidget(Button.builder(Component.literal("定理として登録"), b -> registerGoalTheorem()).bounds(left, 63, 205, 20).build());
        addRenderableWidget(Button.builder(Component.literal("保存"), b -> {
            if (saveAll()) setNotice("証明と定理ライブラリを保存しました。", 0xFF80E080);
            else setNotice("保存失敗: " + persistenceError, 0xFFFF8080);
        }).bounds(this.width - 150, 10, 65, 20).build());
        addRenderableWidget(Button.builder(Component.literal("読込"), b -> {
            loadAll(); if (goalInput != null) goalInput.setValue(session.goalText()); updateTheoremButton();
            setNotice(persistenceError.isBlank() ? "保存データを読み込みました。" : "読込エラー: " + persistenceError,
                    persistenceError.isBlank() ? 0xFF80E0FF : 0xFFFF8080);
        }).bounds(this.width - 80, 10, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("黒板を消去"), b -> clearBoard()).bounds(this.width - 150, 38, 138, 20).build());
        updateTheoremButton();
    }

    private String modeLabel() {
        return switch (session.mode()) {
            case DIRECT -> "通常証明";
            case CONTRADICTION -> "背理法";
            case INDUCTION -> "帰納法";
        };
    }

    private void cycleMode(Button button) {
        ProofSession.Mode[] modes = ProofSession.Mode.values();
        session.setMode(modes[(session.mode().ordinal() + 1) % modes.length]);
        button.setMessage(Component.literal("証明法: " + modeLabel()));
        setNotice(session.mode() == ProofSession.Mode.DIRECT ? "通常証明モードです。" : "このモードはUI選択可能ですが、完全検証はまだ実装対象です。検証成功にはなりません。", 0xFFFFC080);
    }

    private void addPremise() {
        try {
            Statement statement = StatementParser.parse(premiseInput.getValue());
            ProofFact fact = session.addPremise(statement);
            premiseInput.setValue("");
            setNotice("前提を追加しました: " + fact.statement().formula(), 0xFFB0E8FF);
            saveAll();
        } catch (IllegalArgumentException e) { setNotice(e.getMessage(), 0xFFFF8080); }
    }

    private void setGoal() {
        try {
            StatementParser.parse(goalInput.getValue());
            session.setGoalText(goalInput.getValue());
            setNotice("証明目標を設定しました: " + session.goalText(), 0xFFB0E8FF);
            saveAll();
        } catch (IllegalArgumentException e) { setNotice(e.getMessage(), 0xFFFF8080); }
    }

    private List<TheoremDefinition> filteredTheorems() {
        return LIBRARY.all().stream().filter(t -> selectedCategory.equals("すべて") || t.category().equals(selectedCategory)).toList();
    }

    private void cycleCategory() {
        List<String> categories = new ArrayList<>();
        categories.add("すべて");
        LIBRARY.all().stream().map(TheoremDefinition::category).distinct().sorted().forEach(categories::add);
        int index = categories.indexOf(selectedCategory);
        selectedCategory = categories.get((index + 1 + categories.size()) % categories.size());
        selectedTheorem = 0;
        updateTheoremButton();
    }

    private void cycleTheorem() {
        List<TheoremDefinition> all = filteredTheorems();
        if (all.isEmpty()) { setNotice("この分類には定理がありません。", 0xFFFFA060); return; }
        selectedTheorem = (selectedTheorem + 1) % all.size();
        updateTheoremButton();
    }

    private void updateTheoremButton() {
        if (categoryButton != null) categoryButton.setMessage(Component.literal("分類: " + selectedCategory + " ▶"));
        List<TheoremDefinition> all = filteredTheorems();
        if (theoremButton == null) return;
        if (all.isEmpty()) { theoremButton.setMessage(Component.literal("定理: なし")); return; }
        selectedTheorem = Math.floorMod(selectedTheorem, all.size());
        theoremButton.setMessage(Component.literal("定理: " + all.get(selectedTheorem).title() + " ▶"));
    }

    private void applySelectedTheorem() {
        List<TheoremDefinition> all = filteredTheorems();
        if (all.isEmpty()) return;
        TheoremDefinition theorem = all.get(Math.floorMod(selectedTheorem, all.size()));
        ProofEngine.ApplyResult result = ENGINE.apply(theorem, session.facts());
        if (!result.success()) { setNotice(result.message(), 0xFFFFA060); return; }
        for (ProofFact fact : session.facts()) {
            if (fact.statement().equals(result.conclusion())) {
                setNotice("この命題はすでに黒板にあります。", 0xFFFFD080);
                return;
            }
        }
        int idx = session.facts().size();
        ProofFact fact = new ProofFact("d-" + UUID.randomUUID().toString().substring(0, 8), result.conclusion(),
                ProofFact.Origin.DERIVED, "theorem:" + theorem.id(), result.usedFactIds(),
                Math.min(width - 250, 250 + (idx % 3) * 210), 120 + (idx % 6) * 56);
        session.facts().add(fact);
        session.invalidate();
        setNotice(result.message() + "（前提 " + result.usedFactIds().size() + " 件を記録）", 0xFF90E8A0);
        saveAll();
    }

    private void applyTransitivity() {
        Optional<ProofEngine.ApplyResult> result = ENGINE.applyTransitivity(session.facts());
        if (result.isEmpty()) { setNotice("推移律を適用できる等式の組がありません。", 0xFFFFA060); return; }
        ProofEngine.ApplyResult applied = result.get();
        int idx = session.facts().size();
        session.facts().add(new ProofFact("r-" + UUID.randomUUID().toString().substring(0, 8), applied.conclusion(),
                ProofFact.Origin.DERIVED, "rule:transitivity", applied.usedFactIds(),
                Math.min(width - 250, 250 + (idx % 3) * 210), 120 + (idx % 6) * 56));
        session.invalidate();
        setNotice(applied.message(), 0xFF90E8A0);
        saveAll();
    }

    private void verify() {
        setGoalFromInputIfValid();
        VerificationResult result = ENGINE.verify(session, LIBRARY);
        setNotice(result.message(), result.success() ? 0xFF80F0A0 : 0xFFFF8A8A);
        saveAll();
    }

    private void setGoalFromInputIfValid() {
        if (!goalInput.getValue().isBlank() && !goalInput.getValue().equals(session.goalText())) {
            try { StatementParser.parse(goalInput.getValue()); session.setGoalText(goalInput.getValue()); }
            catch (IllegalArgumentException ignored) { }
        }
    }

    private void registerGoalTheorem() {
        if (!session.verified()) { setNotice("先に検証を成功させてください。", 0xFFFFA060); return; }
        try {
            Statement goal = StatementParser.parse(session.goalText());
            List<Statement> premises = session.initialFacts().stream().map(ProofFact::statement).distinct().toList();
            if (premises.isEmpty()) { setNotice("前提のない定理は登録できません。", 0xFFFFA060); return; }
            String id = "user." + UUID.randomUUID().toString().replace("-", "");
            TheoremDefinition theorem = new TheoremDefinition(id, "自作定理 " + (LIBRARY.userTheorems().size() + 1),
                    "ユーザー定理/幾何学", premises, goal, true);
            LIBRARY.addUserTheorem(theorem);
            selectedCategory = theorem.category();
            selectedTheorem = Math.max(0, filteredTheorems().size() - 1);
            updateTheoremButton();
            saveAll();
            setNotice("検証済みの結論を定理ライブラリに登録しました。", 0xFF80F0A0);
        } catch (RuntimeException e) { setNotice("定理登録に失敗: " + e.getMessage(), 0xFFFF8080); }
    }

    private void clearBoard() {
        session.clear();
        goalInput.setValue("");
        saveAll();
        setNotice("黒板を消去しました。定理ライブラリは保持されています。", 0xFFB0E8FF);
    }

    private void setNotice(String message, int color) { notice = message; noticeColor = color; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xFF18212A);
        graphics.fill(0, 0, width, 30, 0xFF263744);
        graphics.drawString(font, "数学証明黒板  |  ドラッグで命題カードを移動 / Mキーで開閉", 12, 10, 0xFFFFFFFF, false);
        graphics.fill(230, 38, width - 8, height - 135, 0xFF26312B);
        graphics.fill(230, 38, width - 8, 62, 0xFF35453A);
        graphics.drawString(font, "資源パレット（入力欄または黒板へドラッグ）", 12, 95, 0xFFE6F4E8, false);
        drawResourcePalette(graphics, mouseX, mouseY);
        graphics.drawString(font, "証明キャンバス", 242, 46, 0xFFE6F4E8, false);
        graphics.drawString(font, "初期前提: " + session.initialFacts().size() + "   命題数: " + session.facts().size() + "   定理: " + LIBRARY.all().size(), 390, 46, 0xFFD0DDD2, false);
        drawDependencyLines(graphics);
        drawPlacedTokens(graphics, mouseX, mouseY);
        visibleFacts = session.facts().stream().filter(f -> f.x() >= 230 && f.y() >= 65 && f.y() < height - 135).toList();
        for (ProofFact fact : visibleFacts) drawFact(graphics, fact);
        graphics.fill(8, height - 132, width - 8, height - 128, 0xFF53635A);
        graphics.drawString(font, "状態: " + notice, 12, height - 119, noticeColor, false);
        graphics.drawString(font, "使える記法: AB = AC / ∠ABC = ∠BCA / △ABC / AB ∥ CD / collinear(A,B,C)", 12, height - 104, 0xFFD4DED8, false);
        graphics.drawString(font, "定理ボタンは押すたびに選択を切替。適用結果は前提IDを持ち、検証時に再評価されます。", 12, height - 90, 0xFFB5C4BC, false);
        if (draggingToken != null) {
            graphics.fill(mouseX - 4, mouseY - 14, mouseX + 145, mouseY + 7, 0xEE182A24);
            graphics.drawString(font, draggingToken, mouseX + 2, mouseY - 8, 0xFFFFFFFF, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawResourcePalette(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < TOKENS.size(); i++) {
            ResourceToken token = TOKENS.get(i);
            int col = i % 2, row = i / 2;
            int x = 12 + col * 105, y = 112 + row * 27;
            boolean hovered = mouseX >= x && mouseX <= x + 100 && mouseY >= y && mouseY <= y + 22;
            graphics.fill(x, y, x + 100, y + 22, hovered ? 0xFF536C5B : 0xFF35473C);
            graphics.drawString(font, token.label(), x + 5, y + 7, 0xFFEAF5EC, false);
        }
    }

    private void drawPlacedTokens(GuiGraphics graphics, int mouseX, int mouseY) {
        for (PlacedToken token : placedTokens) {
            boolean hovered = mouseX >= token.x && mouseX <= token.x + token.width && mouseY >= token.y && mouseY <= token.y + 23;
            graphics.fill(token.x, token.y, token.x + token.width, token.y + 23, hovered ? 0xFF5C6842 : 0xFF465239);
            graphics.drawString(font, token.label, token.x + 5, token.y + 7, 0xFFFFF2C8, false);
        }
    }

    private void drawFact(GuiGraphics graphics, ProofFact fact) {
        int x = fact.x(), y = fact.y(), w = 195, h = 43;
        int fill = fact.origin() == ProofFact.Origin.PREMISE ? 0xFF314B5B : 0xFF43533D;
        if (session.verified()) fill = 0xFF2F5B45;
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 2, fact.origin() == ProofFact.Origin.PREMISE ? 0xFF79C8E8 : 0xFF9DD68D);
        graphics.drawString(font, fact.origin() == ProofFact.Origin.PREMISE ? "前提" : "導出", x + 7, y + 6, 0xFFDAE9F0, false);
        String formula = fact.statement().formula();
        if (font.width(formula) > w - 14) formula = font.plainSubstrByWidth(formula, w - 20) + "…";
        graphics.drawString(font, formula, x + 7, y + 23, 0xFFFFFFFF, false);
    }

    private void drawDependencyLines(GuiGraphics graphics) {
        Map<String, ProofFact> byId = new HashMap<>();
        for (ProofFact fact : session.facts()) byId.put(fact.id(), fact);
        for (ProofFact fact : session.facts()) {
            for (String dependency : fact.dependencies()) {
                ProofFact from = byId.get(dependency);
                if (from != null) line(graphics, from.x() + 195, from.y() + 21, fact.x(), fact.y() + 21, 0xFF8EAD91);
            }
        }
    }

    private void line(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = x2 - x1, dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + dx * i / steps, y = y1 + dy * i / steps;
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < TOKENS.size(); i++) {
                int x = 12 + (i % 2) * 105, y = 112 + (i / 2) * 27;
                if (mouseX >= x && mouseX <= x + 100 && mouseY >= y && mouseY <= y + 22) {
                    draggingToken = TOKENS.get(i).insertion();
                    tokenStartX = (int) mouseX; tokenStartY = (int) mouseY;
                    return true;
                }
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            List<PlacedToken> placed = new ArrayList<>(placedTokens);
            Collections.reverse(placed);
            for (PlacedToken token : placed) {
                if (mouseX >= token.x && mouseX <= token.x + token.width && mouseY >= token.y && mouseY <= token.y + 23) {
                    draggedPlacedToken = token;
                    placedOffsetX = (int) mouseX - token.x;
                    placedOffsetY = (int) mouseY - token.y;
                    tokenStartX = (int) mouseX; tokenStartY = (int) mouseY;
                    return true;
                }
            }
            List<ProofFact> facts = new ArrayList<>(session.facts());
            Collections.reverse(facts);
            for (ProofFact fact : facts) {
                if (mouseX >= fact.x() && mouseX <= fact.x() + 195 && mouseY >= fact.y() && mouseY <= fact.y() + 43) {
                    dragged = fact;
                    dragOffsetX = (int) mouseX - fact.x();
                    dragOffsetY = (int) mouseY - fact.y();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingToken != null && button == 0) return true;
        if (draggedPlacedToken != null && button == 0) {
            draggedPlacedToken.x = Math.max(232, Math.min(width - draggedPlacedToken.width - 10, (int) mouseX - placedOffsetX));
            draggedPlacedToken.y = Math.max(66, Math.min(height - 158, (int) mouseY - placedOffsetY));
            return true;
        }
        if (dragged != null && button == 0) {
            dragged.setPosition(Math.max(232, Math.min(width - 205, (int) mouseX - dragOffsetX)),
                    Math.max(66, Math.min(height - 182, (int) mouseY - dragOffsetY)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingToken != null && button == 0) {
            boolean droppedOnInput = false;
            if (premiseInput != null && mouseX >= premiseInput.getX() && mouseX <= premiseInput.getX() + premiseInput.getWidth()
                    && mouseY >= premiseInput.getY() && mouseY <= premiseInput.getY() + premiseInput.getHeight()) {
                premiseInput.setValue(premiseInput.getValue() + draggingToken); droppedOnInput = true;
            } else if (goalInput != null && mouseX >= goalInput.getX() && mouseX <= goalInput.getX() + goalInput.getWidth()
                    && mouseY >= goalInput.getY() && mouseY <= goalInput.getY() + goalInput.getHeight()) {
                goalInput.setValue(goalInput.getValue() + draggingToken); droppedOnInput = true;
            } else if (mouseX >= 230 && mouseX < width - 8 && mouseY >= 65 && mouseY < height - 135) {
                String label = TOKENS.stream().filter(t -> t.insertion().equals(draggingToken)).map(ResourceToken::label).findFirst().orElse(draggingToken);
                placedTokens.add(new PlacedToken(label, draggingToken, (int) mouseX, (int) mouseY));
                setNotice("資源を黒板に配置しました。クリックすると式入力に追加できます。", 0xFFB0E8FF);
                droppedOnInput = true;
            } else if (Math.abs((int) mouseX - tokenStartX) < 4 && Math.abs((int) mouseY - tokenStartY) < 4) {
                premiseInput.setValue(premiseInput.getValue() + draggingToken); droppedOnInput = true;
            }
            draggingToken = null;
            if (droppedOnInput) saveAll();
            return true;
        }
        if (draggedPlacedToken != null && button == 0) {
            if (premiseInput != null && mouseX >= premiseInput.getX() && mouseX <= premiseInput.getX() + premiseInput.getWidth()
                    && mouseY >= premiseInput.getY() && mouseY <= premiseInput.getY() + premiseInput.getHeight()) {
                premiseInput.setValue(premiseInput.getValue() + draggedPlacedToken.insertion);
                placedTokens.remove(draggedPlacedToken);
            } else if (goalInput != null && mouseX >= goalInput.getX() && mouseX <= goalInput.getX() + goalInput.getWidth()
                    && mouseY >= goalInput.getY() && mouseY <= goalInput.getY() + goalInput.getHeight()) {
                goalInput.setValue(goalInput.getValue() + draggedPlacedToken.insertion);
                placedTokens.remove(draggedPlacedToken);
            } else if (Math.abs((int) mouseX - tokenStartX) < 4 && Math.abs((int) mouseY - tokenStartY) < 4) {
                premiseInput.setValue(premiseInput.getValue() + draggedPlacedToken.insertion);
            }
            draggedPlacedToken = null; saveAll(); return true;
        }
        if (dragged != null) { dragged = null; saveAll(); return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() { saveAll(); super.onClose(); }

    private record ResourceToken(String label, String insertion) {}
    private static final class PlacedToken {
        final String label;
        final String insertion;
        int x, y;
        final int width;
        PlacedToken(String label, String insertion, int x, int y) {
            this.label = label; this.insertion = insertion; this.x = x; this.y = y;
            this.width = Math.max(70, Math.min(180, label.length() * 9 + 12));
        }
    }

    private static Path dataDir() { return FMLPaths.CONFIGDIR.get().resolve("mathproof"); }

    private static boolean saveAll() {
        try {
            Files.createDirectories(dataDir());
            JsonObject root = new JsonObject();
            root.addProperty("schema_version", 1);
            root.addProperty("goal", session.goalText());
            root.addProperty("mode", session.mode().name());
            JsonArray facts = new JsonArray();
            for (ProofFact fact : session.facts()) {
                JsonObject item = new JsonObject();
                item.addProperty("id", fact.id()); item.addProperty("formula", fact.statement().formula());
                item.addProperty("origin", fact.origin().name()); item.addProperty("explanation", fact.explanation());
                item.addProperty("x", fact.x()); item.addProperty("y", fact.y());
                JsonArray deps = new JsonArray(); fact.dependencies().forEach(deps::add); item.add("dependencies", deps); facts.add(item);
            }
            root.add("facts", facts);
            JsonArray tokens = new JsonArray();
            for (PlacedToken token : placedTokens) {
                JsonObject item = new JsonObject(); item.addProperty("label", token.label); item.addProperty("insertion", token.insertion);
                item.addProperty("x", token.x); item.addProperty("y", token.y); tokens.add(item);
            }
            root.add("placed_tokens", tokens);
            Files.writeString(dataDir().resolve("board.json"), GSON.toJson(root), StandardCharsets.UTF_8);
            JsonArray user = new JsonArray();
            for (TheoremDefinition theorem : LIBRARY.userTheorems()) {
                JsonObject item = new JsonObject(); item.addProperty("id", theorem.id()); item.addProperty("title", theorem.title());
                item.addProperty("category", theorem.category()); item.addProperty("conclusion", theorem.conclusion().formula());
                JsonArray premises = new JsonArray(); theorem.premises().forEach(p -> premises.add(p.formula())); item.add("premises", premises); user.add(item);
            }
            Files.writeString(dataDir().resolve("theorems.json"), GSON.toJson(user), StandardCharsets.UTF_8);
            persistenceError = "";
            return true;
        } catch (Exception e) {
            persistenceError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return false;
        }
    }

    private static void loadAll() {
        try {
            Path dir = dataDir();
            Path board = dir.resolve("board.json");
            if (Files.exists(board)) {
                JsonObject root = JsonParser.parseString(Files.readString(board, StandardCharsets.UTF_8)).getAsJsonObject();
                ProofSession loaded = new ProofSession();
                if (root.has("goal")) loaded.setGoalText(root.get("goal").getAsString());
                if (root.has("mode")) try { loaded.setMode(ProofSession.Mode.valueOf(root.get("mode").getAsString())); } catch (Exception ignored) {}
                JsonArray facts = root.has("facts") ? root.getAsJsonArray("facts") : new JsonArray();
                for (JsonElement element : facts) {
                    JsonObject item = element.getAsJsonObject();
                    Statement statement = StatementParser.parse(item.get("formula").getAsString());
                    ProofFact.Origin origin = ProofFact.Origin.valueOf(item.get("origin").getAsString());
                    List<String> deps = new ArrayList<>(); if (item.has("dependencies")) item.getAsJsonArray("dependencies").forEach(d -> deps.add(d.getAsString()));
                    loaded.facts().add(new ProofFact(item.get("id").getAsString(), statement, origin,
                            item.has("explanation") ? item.get("explanation").getAsString() : "", deps,
                            item.get("x").getAsInt(), item.get("y").getAsInt()));
                }
                session = loaded;
                placedTokens.clear();
                if (root.has("placed_tokens")) for (JsonElement element : root.getAsJsonArray("placed_tokens")) {
                    JsonObject item = element.getAsJsonObject();
                    placedTokens.add(new PlacedToken(item.get("label").getAsString(), item.get("insertion").getAsString(),
                            item.get("x").getAsInt(), item.get("y").getAsInt()));
                }
            }
            Path theoremFile = dir.resolve("theorems.json");
            if (Files.exists(theoremFile)) {
                JsonArray list = JsonParser.parseString(Files.readString(theoremFile, StandardCharsets.UTF_8)).getAsJsonArray();
                for (JsonElement element : list) {
                    JsonObject item = element.getAsJsonObject();
                    List<Statement> premises = new ArrayList<>(); item.getAsJsonArray("premises").forEach(p -> premises.add(StatementParser.parse(p.getAsString())));
                    LIBRARY.addLoadedUserTheorem(new TheoremDefinition(item.get("id").getAsString(), item.get("title").getAsString(),
                            item.get("category").getAsString(), premises, StatementParser.parse(item.get("conclusion").getAsString()), true));
                }
            }
            persistenceError = "";
        } catch (Exception e) {
            persistenceError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }
}
