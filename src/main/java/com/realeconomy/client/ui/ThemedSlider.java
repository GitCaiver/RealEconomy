package com.realeconomy.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/** Deslizador con la estética del tema: carril hundido y tirador crema/dorado. */
public abstract class ThemedSlider extends SliderWidget {

    private static final int HANDLE_W = 10;

    protected ThemedSlider(int x, int y, int width, int height, Text text, double value) {
        super(x, y, width, height, text, value);
    }

    /** Valor actual 0-100. */
    public int percent() {
        return (int) Math.round(this.value * 100);
    }

    @Override
    public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // Carril hundido.
        Theme.slot(ctx, x, y, w, h);
        // Relleno dorado hasta el tirador (progreso).
        int fill = (int) (this.value * (w - HANDLE_W));
        if (fill > 0) {
            ctx.fill(x + 3, y + 3, x + 3 + fill, y + h - 3, 0x66CBA14B);
        }
        // Tirador.
        int hx = x + (int) (this.value * (w - HANDLE_W));
        Theme.button(ctx, hx, y, HANDLE_W, h);

        var tr = MinecraftClient.getInstance().textRenderer;
        int tx = x + (w - tr.getWidth(getMessage())) / 2;
        ctx.drawText(tr, getMessage(), tx, y + (h - 8) / 2, Theme.BUTTON_TEXT, false);
    }
}
