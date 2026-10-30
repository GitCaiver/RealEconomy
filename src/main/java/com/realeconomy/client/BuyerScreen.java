package com.realeconomy.client;

import com.realeconomy.client.ui.Theme;
import com.realeconomy.client.ui.ThemedButton;
import com.realeconomy.client.ui.ThemedScreen;
import com.realeconomy.network.BuyerActionPayload;
import com.realeconomy.network.BuyerCategoryView;
import com.realeconomy.network.OpenBuyerPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Interfaz del comprador: precio actual por categoría y venta del inventario, con el tema de madera. */
public class BuyerScreen extends ThemedScreen {

    private static final int BG_W = 210;
    private static final int BG_H = 200;
    private static final int ROW_H = 15;

    private OpenBuyerPayload data;

    public BuyerScreen(OpenBuyerPayload data) {
        super(Text.literal(stripIcon(data.label())), BG_W, BG_H);
        this.data = data;
    }

    private static String stripIcon(String label) {
        // El título va centrado en la cabecera; quitamos el emoji inicial si lo hubiera.
        return label == null ? "Comprador" : label.replace("🛒", "").trim();
    }

    @Override
    protected void initContent() {
        int cl = contentLeft();
        this.addDrawableChild(ThemedButton.of(cl, top + BG_H - 36, contentWidth(), 20,
                Text.literal("Vender inventario"),
                b -> ClientPlayNetworking.send(new BuyerActionPayload(BuyerActionPayload.SELL_INVENTORY))));
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cl = contentLeft();
        text(ctx, "Precio por categoría (" + data.currencyName() + "):", cl, contentTop(), Theme.TEXT);

        int row = contentTop() + 16;
        for (BuyerCategoryView r : data.rows()) {
            int pct = (int) Math.round(r.multiplier() * 100);
            Theme.slot(ctx, cl, row - 2, contentWidth(), ROW_H - 1);
            text(ctx, r.name() + ": " + r.coinPrice() + " /ud", cl + 5, row + 1, Theme.TEXT);
            // Porcentaje a la derecha, coloreado según oferta/demanda.
            int color = pct >= 80 ? Theme.TEXT_OK : (pct <= 40 ? Theme.TEXT_WARN : Theme.TEXT_DIM);
            String p = pct + "%";
            text(ctx, Text.literal(p), cl + contentWidth() - 5 - this.textRenderer.getWidth(p), row + 1, color);
            row += ROW_H;
        }
    }

    public void refresh(OpenBuyerPayload data) {
        this.data = data;
        this.clearAndInit();
    }
}
