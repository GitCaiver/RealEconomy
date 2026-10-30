package com.realeconomy.client;

import com.realeconomy.config.ModConfig;
import com.realeconomy.network.BuyerActionPayload;
import com.realeconomy.network.BuyerCategoryView;
import com.realeconomy.network.OpenBuyerPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Interfaz del comprador: precio actual por categoría y venta del inventario. */
public class BuyerScreen extends Screen {

    private static final int BG_W = 200;
    private static final int BG_H = 200;

    private OpenBuyerPayload data;
    private int x;
    private int y;

    public BuyerScreen(OpenBuyerPayload data) {
        super(Text.literal("Comprador"));
        this.data = data;
    }

    private Identifier background() {
        try {
            return Identifier.of(ModConfig.get().buyerGuiTexture);
        } catch (Exception e) {
            return Identifier.of("realeconomy", "textures/gui/shop.png");
        }
    }

    @Override
    protected void init() {
        this.x = (this.width - BG_W) / 2;
        this.y = (this.height - BG_H) / 2;
        ButtonWidget sell = ButtonWidget.builder(Text.literal("Vender inventario"),
                        b -> ClientPlayNetworking.send(new BuyerActionPayload(BuyerActionPayload.SELL_INVENTORY)))
                .dimensions(x + 20, y + BG_H - 28, BG_W - 40, 20).build();
        this.addDrawableChild(sell);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.drawTexture(background(), x, y, BG_W, BG_H, 0.0F, 0.0F, BG_W, BG_H, 256, 256);
        ctx.drawText(this.textRenderer, Text.literal(data.label()).formatted(Formatting.DARK_GRAY), x + 8, y + 8, 0x404040, false);
        ctx.drawText(this.textRenderer, Text.literal("Precio por categoría (" + data.currencyName() + "):"),
                x + 10, y + 24, 0x303030, false);

        int row = y + 40;
        for (BuyerCategoryView r : data.rows()) {
            int pct = (int) Math.round(r.multiplier() * 100);
            ctx.drawText(this.textRenderer,
                    Text.literal(r.name() + ": " + r.coinPrice() + " /ud  (" + pct + "%)"),
                    x + 14, row, 0x303030, false);
            row += 12;
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public void refresh(OpenBuyerPayload data) {
        this.data = data;
        this.clearAndInit();
    }
}
