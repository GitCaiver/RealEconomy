package com.realeconomy.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Botón con la estética del tema (crema/dorado, borde negro, texto con sombra). */
public class ThemedButton extends ButtonWidget {

    public ThemedButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
    }

    public static ThemedButton of(int x, int y, int width, int height, Text message, PressAction onPress) {
        return new ThemedButton(x, y, width, height, message, onPress);
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        Theme.button(ctx, x, y, w, h);

        if (!this.active) {
            // Oscurecer para indicar deshabilitado.
            ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x99101016);
        } else if (isHovered() || isFocused()) {
            // Resalte turquesa translúcido al pasar/enfocar (acento neón).
            ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x3335E0D8);
        }

        int color = this.active ? Theme.BUTTON_TEXT : Theme.BUTTON_TEXT_OFF;
        var tr = MinecraftClient.getInstance().textRenderer;
        // Texto claro sobre botón oscuro: con sombra para destacar.
        int tx = x + (w - tr.getWidth(getMessage())) / 2;
        ctx.drawText(tr, getMessage(), tx, y + (h - 8) / 2, color, true);
    }
}
