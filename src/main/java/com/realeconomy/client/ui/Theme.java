package com.realeconomy.client.ui;

import com.realeconomy.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Tema visual único de RealEconomy: marco de madera oscura, panel marrón,
 * botones crema/dorado con borde negro y detalles dorados.
 *
 * <p>Todas las pantallas del mod reutilizan este tema. Las texturas (panel, botón,
 * casilla) son sustituibles por resource pack (mismo identificador) o cambiando
 * {@code themePanelTexture} / {@code themeButtonTexture} / {@code themeSlotTexture}
 * en {@code config/realeconomy.json}.
 */
public final class Theme {

    private Theme() {
    }

    // ---- Paleta (ARGB) ----
    /** Dorado para títulos y acentos. */
    public static final int TITLE = 0xFFE7C878;
    /** Texto claro (crema) sobre el panel marrón. */
    public static final int TEXT = 0xFFF2E6CC;
    /** Texto secundario (crema apagado). */
    public static final int TEXT_DIM = 0xFFC7B18A;
    /** Texto de aviso/alerta. */
    public static final int TEXT_WARN = 0xFFE8945A;
    /** Verde suave para valores positivos. */
    public static final int TEXT_OK = 0xFFA8D08A;
    /** Texto oscuro sobre los botones crema. */
    public static final int BUTTON_TEXT = 0xFF2A1D0E;
    /** Texto de un botón deshabilitado. */
    public static final int BUTTON_TEXT_OFF = 0xFF6B5A3F;
    /** Resalte de fila seleccionada. */
    public static final int ROW_SELECTED = 0x55E7C878;

    // ---- Métricas del marco (coinciden con las texturas generadas) ----
    private static final int PANEL_TEX = 72;
    private static final int PANEL_BORDER = 14;
    private static final int BTN_TEX_W = 32;
    private static final int BTN_TEX_H = 20;
    private static final int BTN_BORDER = 6;
    private static final int SLOT_TEX = 26;
    private static final int SLOT_BORDER = 6;

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
