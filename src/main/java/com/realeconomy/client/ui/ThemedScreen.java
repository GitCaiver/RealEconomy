package com.realeconomy.client.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Base de todas las pantallas del mod: centra un panel de madera, pinta la cabecera
 * dorada con el título y ofrece utilidades comunes de dibujo con el {@link Theme}.
 *
 * <p>Las subclases definen el tamaño del panel y pintan su contenido en
 * {@link #renderContent(DrawContext, int, int, float)}; los widgets se añaden en
 * {@link #initContent()}.
 */
public abstract class ThemedScreen extends Screen {

    protected final int bgW;
    protected final int bgH;
    /** Esquina superior izquierda del panel (calculada al centrar). */
    protected int left;
    protected int top;

    protected ThemedScreen(Text title, int width, int height) {
        super(title);
        this.bgW = width;
        this.bgH = height;
    }

    @Override
    protected final void init() {
        this.left = (this.width - bgW) / 2;
        this.top = (this.height - bgH) / 2;
        initContent();
    }

    /** Añade los widgets de la pantalla (botones, campos...). */
    protected abstract void initContent();

    /** Pinta el contenido propio de la pantalla sobre el panel ya dibujado. */
    protected abstract void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta);

    @Override
    public final void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        Theme.panel(ctx, left, top, bgW, bgH);
        drawHeader(ctx);
        renderContent(ctx, mouseX, mouseY, delta);
        // Dibuja los widgets (botones, campos) por encima del contenido.
        super.render(ctx, mouseX, mouseY, delta);
    }

    /** Cabecera: título centrado en dorado con una línea divisoria dorada. */
    protected void drawHeader(DrawContext ctx) {
        int cx = left + bgW / 2;
        ctx.drawCenteredTextWithShadow(this.textRenderer, getTitle(), cx, top + 8, Theme.TITLE);
        int ly = top + Theme.HEADER - 2;
        int lx0 = left + Theme.INSET;
        int lx1 = left + bgW - Theme.INSET;
        ctx.fill(lx0, ly, lx1, ly + 1, 0xFF8A6B2E);      // sombra
        ctx.fill(lx0, ly - 1, lx1, ly, 0xAACBA14B);      // línea dorada
    }

    // ---- Utilidades de dibujo para las subclases ----

    protected int contentLeft() {
        return left + Theme.INSET;
    }

    protected int contentTop() {
        return top + Theme.HEADER + 4;
    }

    protected int contentWidth() {
        return bgW - Theme.INSET * 2;
    }

    protected void text(DrawContext ctx, Text t, int x, int y, int color) {
        ctx.drawText(this.textRenderer, t, x, y, color, false);
    }

    protected void text(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawText(this.textRenderer, Text.literal(s), x, y, color, false);
    }

    protected void centered(DrawContext ctx, Text t, int cx, int y, int color) {
        ctx.drawCenteredTextWithShadow(this.textRenderer, t, cx, y, color);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
