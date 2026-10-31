package com.realeconomy.client.ui;

import com.realeconomy.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Tema visual único de RealEconomy: <b>Art Déco + steampunk elegante</b>. Mármol negro,
 * oro y latón, líneas geométricas, abanicos Art Déco y remaches, con un filo de
 * <b>cian/turquesa neón</b> como acento neotecnológico. Bordes biselados.
 *
 * <p>Todas las pantallas del mod reutilizan este tema. Las texturas (panel, botón,
 * casilla, campo de texto, barra) son sustituibles por resource pack (mismo identificador)
 * o cambiando {@code themePanelTexture} / {@code themeButtonTexture} / {@code themeSlotTexture}
 * / {@code themeFieldTexture} / {@code themeBarTexture} en {@code config/realeconomy.json}.
 */
public final class Theme {

    private Theme() {
    }

    // ---- Paleta (ARGB) ----
    /** Dorado brillante para títulos y acentos. */
    public static final int TITLE = 0xFFF2D785;
    /** Texto claro (marfil) sobre el mármol oscuro. */
    public static final int TEXT = 0xFFEAE4D2;
    /** Texto secundario (apagado). */
    public static final int TEXT_DIM = 0xFF9A927E;
    /** Texto de aviso/alerta (ámbar). */
    public static final int TEXT_WARN = 0xFFE8945A;
    /** Verde-turquesa suave para valores positivos. */
    public static final int TEXT_OK = 0xFF7FE0C0;
    /** Acento neón turquesa (detalles tecnológicos, tendencias, resaltes). */
    public static final int ACCENT = 0xFF35E0D8;
    /** Latón para acentos cálidos y subtítulos. */
    public static final int BRASS = 0xFFB08A3E;
    /** Texto claro sobre los botones oscuros de latón. */
    public static final int BUTTON_TEXT = 0xFFF2E6C0;
    /** Texto de un botón deshabilitado. */
    public static final int BUTTON_TEXT_OFF = 0xFF706650;
    /** Resalte de fila seleccionada (velo turquesa). */
    public static final int ROW_SELECTED = 0x4435E0D8;
    /** Relleno dorado para barras/sliders. */
    public static final int BAR_FILL = 0xCCD4AF37;
    /** Relleno turquesa para barras de tendencia. */
    public static final int BAR_FILL_ACCENT = 0xCC35E0D8;

    // ---- Métricas del marco (coinciden con las texturas generadas) ----
    private static final int PANEL_TEX = 72;
    private static final int PANEL_BORDER = 14;
    private static final int BTN_TEX_W = 32;
    private static final int BTN_TEX_H = 20;
    private static final int BTN_BORDER = 6;
    private static final int SLOT_TEX = 26;
    private static final int SLOT_BORDER = 6;
    private static final int FIELD_TEX = 24;
    private static final int FIELD_BORDER = 6;
    private static final int BAR_TEX_W = 24;
    private static final int BAR_TEX_H = 12;
    private static final int BAR_BORDER = 6;

    /** Margen interior útil del panel desde el borde del marco. */
    public static final int INSET = 16;
    /** Alto reservado para la cabecera (título). */
    public static final int HEADER = 22;

    private static Identifier id(String configured, String fallback) {
        try {
            return Identifier.of(configured);
        } catch (Exception e) {
            return Identifier.of("realeconomy", fallback);
        }
    }

    public static Identifier panelTexture() {
        return id(ModConfig.get().themePanelTexture, "textures/gui/theme/panel.png");
    }

    public static Identifier buttonTexture() {
        return id(ModConfig.get().themeButtonTexture, "textures/gui/theme/button.png");
    }

    public static Identifier slotTexture() {
        return id(ModConfig.get().themeSlotTexture, "textures/gui/theme/slot.png");
    }

    public static Identifier fieldTexture() {
        return id(ModConfig.get().themeFieldTexture, "textures/gui/theme/field.png");
    }

    public static Identifier barTexture() {
        return id(ModConfig.get().themeBarTexture, "textures/gui/theme/bar.png");
    }

    /** Dibuja el panel/marco de madera en el rectángulo dado (nine-slice). */
    public static void panel(DrawContext ctx, int x, int y, int w, int h) {
        nineSlice(ctx, panelTexture(), x, y, w, h, PANEL_TEX, PANEL_TEX, PANEL_BORDER);
    }

    /** Dibuja un botón crema/dorado en el rectángulo dado (nine-slice). */
    public static void button(DrawContext ctx, int x, int y, int w, int h) {
        nineSlice(ctx, buttonTexture(), x, y, w, h, BTN_TEX_W, BTN_TEX_H, BTN_BORDER);
    }

    /** Dibuja una casilla hundida (fondo de fila/listado). */
    public static void slot(DrawContext ctx, int x, int y, int w, int h) {
        nineSlice(ctx, slotTexture(), x, y, w, h, SLOT_TEX, SLOT_TEX, SLOT_BORDER);
    }

    /** Dibuja el fondo de un campo de texto (mármol oscuro, marco de latón, filo turquesa). */
    public static void field(DrawContext ctx, int x, int y, int w, int h) {
        nineSlice(ctx, fieldTexture(), x, y, w, h, FIELD_TEX, FIELD_TEX, FIELD_BORDER);
    }

    /** Dibuja el carril hundido de una barra/slider. */
    public static void bar(DrawContext ctx, int x, int y, int w, int h) {
        nineSlice(ctx, barTexture(), x, y, w, h, BAR_TEX_W, BAR_TEX_H, BAR_BORDER);
    }

    /**
     * Dibuja una barra con relleno proporcional (0..1) sobre su carril.
     * @param fillColor color ARGB del relleno (p. ej. {@link #BAR_FILL} o {@link #BAR_FILL_ACCENT}).
     */
    public static void barFilled(DrawContext ctx, int x, int y, int w, int h, double fraction, int fillColor) {
        bar(ctx, x, y, w, h);
        int inner = Math.max(0, (int) Math.round((w - 4) * Math.max(0, Math.min(1, fraction))));
        if (inner > 0) {
            ctx.fill(x + 2, y + 2, x + 2 + inner, y + h - 2, fillColor);
        }
    }

    /** Separador decorativo Art Déco: línea dorada con dos rombos de latón centrados. */
    public static void separator(DrawContext ctx, int x, int y, int w) {
        ctx.fill(x, y + 1, x + w, y + 2, 0x55070707);          // sombra
        ctx.fill(x, y, x + w, y + 1, 0x99D4AF37);              // línea dorada
        int cx = x + w / 2;
        ctx.fill(cx - 2, y - 1, cx + 2, y + 2, 0xFFB08A3E);    // rombo central de latón
        ctx.fill(cx - 1, y - 2, cx + 1, y + 3, 0xFFF2D785);    // brillo del rombo
    }

    /** Nine-slice con borde uniforme. */
    public static void nineSlice(DrawContext ctx, Identifier tex, int x, int y, int w, int h,
                                 int texW, int texH, int border) {
        nineSlice(ctx, tex, x, y, w, h, texW, texH, border, border, border, border);
    }

    /**
     * Dibuja una textura escalable por nine-slice: esquinas fijas, bordes estirados en una
     * dirección y centro estirado en ambas. Funciona con la sobrecarga de {@code drawTexture}
     * que toma una región fuente y un rectángulo destino.
     */
    public static void nineSlice(DrawContext ctx, Identifier tex, int x, int y, int w, int h,
                                 int texW, int texH, int left, int top, int right, int bottom) {
        int cw = Math.max(0, w - left - right);
        int ch = Math.max(0, h - top - bottom);
        int sCw = texW - left - right;
        int sCh = texH - top - bottom;

        // Esquinas
        piece(ctx, tex, x, y, left, top, 0, 0, left, top, texW, texH);
        piece(ctx, tex, x + w - right, y, right, top, texW - right, 0, right, top, texW, texH);
        piece(ctx, tex, x, y + h - bottom, left, bottom, 0, texH - bottom, left, bottom, texW, texH);
        piece(ctx, tex, x + w - right, y + h - bottom, right, bottom, texW - right, texH - bottom, right, bottom, texW, texH);

        // Bordes
        if (cw > 0) {
            piece(ctx, tex, x + left, y, cw, top, left, 0, sCw, top, texW, texH);
            piece(ctx, tex, x + left, y + h - bottom, cw, bottom, left, texH - bottom, sCw, bottom, texW, texH);
        }
        if (ch > 0) {
            piece(ctx, tex, x, y + top, left, ch, 0, top, left, sCh, texW, texH);
            piece(ctx, tex, x + w - right, y + top, right, ch, texW - right, top, right, sCh, texW, texH);
        }
        // Centro
        if (cw > 0 && ch > 0) {
            piece(ctx, tex, x + left, y + top, cw, ch, left, top, sCw, sCh, texW, texH);
        }
    }

    private static void piece(DrawContext ctx, Identifier tex, int dx, int dy, int dw, int dh,
                              int u, int v, int rw, int rh, int texW, int texH) {
        if (dw <= 0 || dh <= 0 || rw <= 0 || rh <= 0) {
            return;
        }
        ctx.drawTexture(tex, dx, dy, dw, dh, (float) u, (float) v, rw, rh, texW, texH);
    }
}
