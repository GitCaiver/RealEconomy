package com.realeconomy.client;

import com.realeconomy.config.ModConfig;
import com.realeconomy.network.CurrencyEntry;
import com.realeconomy.network.OfferView;
import com.realeconomy.network.OpenShopPayload;
import com.realeconomy.network.ShopActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Interfaz de compra/venta de una tienda (para clientes). */
public class ShopScreen extends Screen {

    private static final int BG_W = 230;
    private static final int BG_H = 186;
    private static final int ROW_H = 26;
    private static final int MAX_ROWS = 5;
    private static final int LIST_TOP = 28;

    private OpenShopPayload data;
    private int x;
    private int y;

    public ShopScreen(OpenShopPayload data) {
        super(Text.literal("Tienda"));
        this.data = data;
    }

    private Identifier background() {
        try {
            return Identifier.of(ModConfig.get().shopGuiTexture);
        } catch (Exception e) {
            return Identifier.of("realeconomy", "textures/gui/shop.png");
        }
    }

    @Override
    protected void init() {
        this.x = (this.width - BG_W) / 2;
        this.y = (this.height - BG_H) / 2;

        int count = Math.min(MAX_ROWS, data.offers().size());
        for (int i = 0; i < count; i++) {
            OfferView o = data.offers().get(i);
            int rowY = y + LIST_TOP + i * ROW_H;
            boolean sell = o.type() == OfferView.SELL;
            Text label = Text.literal(sell ? "Comprar" : "Vender");
            ButtonWidget btn = ButtonWidget.builder(label, b -> {
                int action = sell ? ShopActionPayload.BUY : ShopActionPayload.SELL;
                int amount = hasShiftDown() ? 64 : 1;
                ClientPlayNetworking.send(new ShopActionPayload(data.shopId(), o.index(), action, amount));
            }).dimensions(x + BG_W - 72, rowY + 2, 60, 20).build();
            this.addDrawableChild(btn);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.drawTexture(background(), x, y, BG_W, BG_H, 0.0F, 0.0F, BG_W, BG_H, 256, 256);
        ctx.drawText(this.textRenderer, Text.literal("Tienda de " + data.ownerName()), x + 8, y + 9, 0x404040, false);

        int count = Math.min(MAX_ROWS, data.offers().size());
        for (int i = 0; i < count; i++) {
            OfferView o = data.offers().get(i);
            int rowY = y + LIST_TOP + i * ROW_H;
            boolean sell = o.type() == OfferView.SELL;

            ctx.drawItem(o.item(), x + 12, rowY + 4);

            CurrencyEntry entry = ClientCurrencyStore.getEntry(o.currencyId());
            String money = entry != null ? entry.name() : o.currencyId();
            Formatting color = entry != null ? Formatting.byName(entry.colorName()) : null;
            if (color == null) {
                color = Formatting.GOLD;
            }

            String title = (sell ? "Vende: " : "Compra: ") + o.item().getName().getString();
            ctx.drawText(this.textRenderer, Text.literal(title), x + 34, rowY + 2, 0x202020, false);
            ctx.drawText(this.textRenderer, Text.literal(o.price() + " ").append(Text.literal(money).formatted(color))
                            .append(Text.literal(sell ? ("  (stock: " + o.available() + ")") : ("  (quedan: " + o.available() + ")"))),
                    x + 34, rowY + 13, 0x404040, false);
        }

        if (data.offers().isEmpty()) {
            ctx.drawText(this.textRenderer, Text.literal("Esta tienda no tiene ofertas."), x + 14, y + LIST_TOP + 6, 0x707070, false);
        } else {
            ctx.drawText(this.textRenderer, Text.literal("Shift: comprar/vender x64").formatted(Formatting.DARK_GRAY),
                    x + 8, y + BG_H - 11, 0x606060, false);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public void refresh(OpenShopPayload data) {
        this.data = data;
        this.clearAndInit();
    }
}
