package com.realeconomy.client;

import com.realeconomy.economy.IconCache;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.util.Identifier;

/** Componente de tooltip que dibuja el icono descargado de la moneda. */
public class CoinTooltipComponent implements TooltipComponent {

    private static final int DISPLAY = 24;
    private final String currencyId;

    public CoinTooltipComponent(String currencyId) {
        this.currencyId = currencyId;
    }

    @Override
    public int getHeight() {
        return DISPLAY + 2;
    }

    @Override
    public int getWidth(TextRenderer textRenderer) {
        return DISPLAY;
    }

    @Override
    public void drawItems(TextRenderer textRenderer, int x, int y, DrawContext context) {
        Identifier tex = ClientCurrencyStore.getTexture(currencyId);
        int src = IconCache.ICON_SIZE;
        context.drawTexture(tex, x, y, DISPLAY, DISPLAY, 0.0F, 0.0F, src, src, src, src);
    }
}
