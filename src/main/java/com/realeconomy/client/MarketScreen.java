package com.realeconomy.client;

import com.realeconomy.client.ui.Theme;
import com.realeconomy.client.ui.ThemedButton;
import com.realeconomy.client.ui.ThemedScreen;
import com.realeconomy.network.CurrencyEntry;
import com.realeconomy.network.MarketActionPayload;
import com.realeconomy.network.OfferView;
import com.realeconomy.network.OpenMarketPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Interfaz de compra/venta de un mercado de país (para clientes), con el tema de madera. */
public class MarketScreen extends ThemedScreen {

    private static final int BG_W = 238;
    private static final int BG_H = 184;
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
            OfferView o = data.offers().get(i);
            int rowY = contentTop() + i * ROW_H;
            boolean sell = o.type() == OfferView.SELL;
            Text label = Text.literal(sell ? "Comprar" : "Vender");
            this.addDrawableChild(ThemedButton.of(cl + contentWidth() - 60, rowY + 3, 58, 19, label, b -> {
                int action = sell ? MarketActionPayload.BUY : MarketActionPayload.SELL;
                int amount = hasShiftDown() ? 64 : 1;
                ClientPlayNetworking.send(new MarketActionPayload(data.marketId(), o.index(), action, amount));
            }));
        }
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cl = contentLeft();
        int count = Math.min(MAX_ROWS, data.offers().size());
        for (int i = 0; i < count; i++) {
            OfferView o = data.offers().get(i);
            int rowY = contentTop() + i * ROW_H;
            boolean sell = o.type() == OfferView.SELL;

            Theme.slot(ctx, cl, rowY, contentWidth(), ROW_H - 3);
            ctx.drawItem(o.item(), cl + 4, rowY + 3);

            CurrencyEntry entry = ClientCurrencyStore.getEntry(o.currencyId());
            String money = entry != null ? entry.name() : o.currencyId();
            Formatting color = entry != null ? Formatting.byName(entry.colorName()) : null;
            if (color == null) {
                color = Formatting.GOLD;
            }

            String title = (sell ? "Vende: " : "Compra: ") + o.item().getName().getString();
            text(ctx, Text.literal(title), cl + 24, rowY + 3, Theme.TEXT);
            text(ctx, Text.literal(o.price() + " ").append(Text.literal(money).formatted(color))
                            .append(Text.literal(sell ? ("  (stock " + o.available() + ")") : ("  (puede " + o.available() + ")"))),
                    cl + 24, rowY + 14, Theme.TEXT_DIM);
        }

        if (data.offers().isEmpty()) {
            text(ctx, "Este mercado no tiene ofertas.", cl, contentTop() + 6, Theme.TEXT_DIM);
        } else {
            text(ctx, Text.literal("Mantén Shift para x64").formatted(Formatting.ITALIC),
                    cl, contentTop() + MAX_ROWS * ROW_H + 2, Theme.TEXT_DIM);
        }
    }

    public void refresh(OpenMarketPayload data) {
        this.data = data;
        this.clearAndInit();
    }
}
