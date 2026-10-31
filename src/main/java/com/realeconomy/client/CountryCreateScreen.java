package com.realeconomy.client;

import com.realeconomy.client.ui.Theme;
import com.realeconomy.client.ui.ThemedButton;
import com.realeconomy.client.ui.ThemedScreen;
import com.realeconomy.client.ui.ThemedTextField;
import com.realeconomy.network.CountryCreateActionPayload;
import com.realeconomy.politics.FormSubtype;
import com.realeconomy.politics.FormType;
import com.realeconomy.util.ColorUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Interfaz de creación de país (tema Art Déco): siglas, nombre, color y forma
 * política (con subtipo cuando toca). Al confirmar envía {@link CountryCreateActionPayload}.
 */
public class CountryCreateScreen extends ThemedScreen {

    private static final int BG_W = 230;
    private static final int BG_H = 250;

    private static final List<String> COLORS = ColorUtil.suggestions();
    private static final FormType[] FORMS = FormType.values();

    private int colorIndex = 0;
    private int formIndex = 0;
    private int subtypeIndex = 0;

    private ThemedTextField tagField;
    private ThemedTextField nameField;
    // Se conservan al reconstruir (cambio de forma muestra/oculta el subtipo).
    private String tagText = "";
    private String nameText = "";

    public CountryCreateScreen() {
        super(Text.literal("Fundar país"), BG_W, BG_H);
    }

    private FormType form() {
        return FORMS[formIndex];
    }

    private FormSubtype subtype() {
        FormType f = form();
        if (!f.requiresSubtype()) {
            return FormSubtype.NONE;
        }
        return f.subtypes().get(Math.min(subtypeIndex, f.subtypes().size() - 1));
    }

    @Override
    protected void initContent() {
        int cl = contentLeft();
        int cw = contentWidth();
        int y = contentTop() + 10;

        this.tagField = new ThemedTextField(this.textRenderer, cl + 60, y, cw - 60, 16, Text.literal("SIGLAS"));
        this.tagField.setMaxLength(4);
        this.tagField.setText(tagText);
        this.tagField.setChangedListener(s -> tagText = s);
        this.addDrawableChild(this.tagField);

        y += 22;
        this.nameField = new ThemedTextField(this.textRenderer, cl + 60, y, cw - 60, 16, Text.literal("Nombre"));
        this.nameField.setMaxLength(32);
        this.nameField.setText(nameText);
        this.nameField.setChangedListener(s -> nameText = s);
        this.addDrawableChild(this.nameField);

        y += 24;
        this.addDrawableChild(ThemedButton.of(cl + 60, y, cw - 60, 18,
                Text.literal(COLORS.get(colorIndex)).formatted(colorFormatting()),
                b -> {
                    colorIndex = (colorIndex + 1) % COLORS.size();
                    this.clearAndInit();
                }));

        y += 24;
        this.addDrawableChild(ThemedButton.of(cl + 60, y, cw - 60, 18,
                Text.literal(form().displayName()),
                b -> {
                    formIndex = (formIndex + 1) % FORMS.length;
                    subtypeIndex = 0;
                    this.clearAndInit();
                }));

        y += 24;
        if (form().requiresSubtype()) {
            this.addDrawableChild(ThemedButton.of(cl + 60, y, cw - 60, 18,
                    Text.literal(subtype().displayName()),
                    b -> {
                        subtypeIndex = (subtypeIndex + 1) % form().subtypes().size();
                        this.clearAndInit();
                    }));
        }

        // Botón de confirmar, abajo.
        this.addDrawableChild(ThemedButton.of(cl, top + bgH - 26, cw, 18, Text.literal("Fundar país"), b -> confirm()));
    }

    private Formatting colorFormatting() {
        Formatting f = ColorUtil.parse(COLORS.get(colorIndex));
        return f == null ? Formatting.WHITE : f;
    }

    private void confirm() {
        String tag = tagField.getText().trim();
        if (tag.length() < 2) {
            return;
        }
        ClientPlayNetworking.send(new CountryCreateActionPayload(
                tag, nameField.getText().trim(), COLORS.get(colorIndex),
                form().name(), subtype().name()));
        this.close();
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cl = contentLeft();
        int y = contentTop() + 10;

        text(ctx, "Siglas:", cl, y + 4, Theme.TEXT);
        y += 22;
        text(ctx, "Nombre:", cl, y + 4, Theme.TEXT);
        y += 24;
        text(ctx, "Color:", cl, y + 5, Theme.TEXT);
        y += 24;
        text(ctx, "Forma:", cl, y + 5, Theme.TEXT);
        y += 24;
        if (form().requiresSubtype()) {
            text(ctx, "Subtipo:", cl, y + 5, Theme.TEXT);
            y += 24;
        }

        // Descripción de la forma (y subtipo) seleccionada, ajustada al ancho.
        String desc = form().requiresSubtype() ? subtype().description() : form().description();
        if (desc == null || desc.isBlank()) {
            desc = form().description();
        }
        List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(desc), contentWidth());
        int dy = y + 4;
        for (OrderedText line : lines) {
            ctx.drawText(this.textRenderer, line, cl, dy, Theme.TEXT_DIM, false);
            dy += 10;
        }
    }
}
