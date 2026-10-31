package com.realeconomy.client;

import com.realeconomy.client.ui.Theme;
import com.realeconomy.client.ui.ThemedButton;
import com.realeconomy.client.ui.ThemedScreen;
import com.realeconomy.network.CurrencyEntry;
import com.realeconomy.network.MarketActionPayload;
import com.realeconomy.network.MarketOfferView;
import com.realeconomy.network.OpenMarketPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Interfaz del mercado (parte 6, "solo vender"): lista lo que el mercado compra,
 * con el precio actual, su tendencia (▲/▼) y un botón para vender.
 */
public class MarketScreen extends ThemedScreen {

    private static final int BG_W = 238;
    private static final int BG_H = 196;
    private static final int ROW_H = 26;
    private static final int MAX_ROWS = 5;

    private OpenMarketPayload data;

    public MarketScreen(OpenMarketPayload data) {
        super(Text.literal("Mercado " + data.label()), BG_W, BG_H);
        this.data = data;
    }

    @Override
    protected void initContent() {
        int cl = contentLeft();
        int count = Math.min(MAX_ROWS, data.offers().size());
        for (int i = 0; i < count; i++) {
            MarketOfferView o = data.offers().get(i);
            int rowY = contentTop() + 12 + i * ROW_H;
            this.addDrawableChild(ThemedButton.of(cl + contentWidth() - 60, rowY + 3, 58, 19, Text.literal("Vender"), b -> {
                int amount = hasShiftDown() ? 64 : 1;
                ClientPlayNetworking.send(new MarketActionPayload(data.marketId(), o.index(), MarketActionPayload.SELL, amount));
            }));
        }
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cl = contentLeft();
        text(ctx, Text.literal("El mercado compra:").formatted(Formatting.ITALIC), cl, contentTop(), Theme.TEXT_DIM);

        int count = Math.min(MAX_ROWS, data.offers().size());
        for (int i = 0; i < count; i++) {
            MarketOfferView o = data.offers().get(i);
            int rowY = contentTop() + 12 + i * ROW_H;

            Theme.slot(ctx, cl, rowY, contentWidth(), ROW_H - 3);
            // Icono real del item de Minecraft, con su brillo de encantamiento (parte 7).
            ctx.drawItem(o.item(), cl + 4, rowY + 3);

            CurrencyEntry entry = ClientCurrencyStore.getEntry(o.currencyId());
            String money = entry != null ? entry.name() : o.currencyId();
            Formatting color = entry != null ? Formatting.byName(entry.colorName()) : null;
            if (color == null) {
                color = Formatting.GOLD;
            }

            text(ctx, Text.literal(o.item().getName().getString()), cl + 24, rowY + 3, Theme.TEXT);

            // Línea de precio: precio actual + moneda + flecha de tendencia + "(base N)".
            net.minecraft.text.MutableText line = Text.literal(o.price() + " ").append(Text.literal(money).formatted(color));
            line.append(trendText(o.trend()));
            if (o.price() != o.basePrice()) {
                line.append(Text.literal("  (base " + o.basePrice() + ")").formatted(Formatting.DARK_GRAY));
            }
            text(ctx, line, cl + 24, rowY + 14, Theme.TEXT_DIM);
        }

        if (data.offers().isEmpty()) {
            text(ctx, "Este mercado aún no compra nada.", cl, contentTop() + 16, Theme.TEXT_DIM);
        } else {
            text(ctx, Text.literal("Mantén Shift para vender x64").formatted(Formatting.ITALIC),
                    cl, contentTop() + 12 + MAX_ROWS * ROW_H + 2, Theme.TEXT_DIM);
        }
    }

    private static Text trendText(int trend) {
        return switch (trend) {
            case MarketOfferView.UP -> Text.literal("  ▲").formatted(Formatting.GREEN);
            case MarketOfferView.DOWN -> Text.literal("  ▼").formatted(Formatting.RED);
            default -> Text.literal("  =").formatted(Formatting.GRAY);
        };
    }

    @Override
    protected void renderTooltips(DrawContext ctx, int mouseX, int mouseY) {
        int cl = contentLeft();
        int count = Math.min(MAX_ROWS, data.offers().size());
        for (int i = 0; i < count; i++) {
            MarketOfferView o = data.offers().get(i);
            int rowY = contentTop() + 12 + i * ROW_H;
            itemTooltip(ctx, o.item(), cl + 4, rowY + 3, mouseX, mouseY);
        }
    }

    public void refresh(OpenMarketPayload data) {
        this.data = data;
        this.clearAndInit();
    }
}
