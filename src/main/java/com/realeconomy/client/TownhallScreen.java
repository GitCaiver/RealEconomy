package com.realeconomy.client;

import com.realeconomy.config.ModConfig;
import com.realeconomy.network.OpenTownhallPayload;
import com.realeconomy.network.TownhallActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Interfaz del ayuntamiento: compra de licencias de tienda. */
public class TownhallScreen extends Screen {

    private static final int BG_W = 190;
    private static final int BG_H = 120;

    private OpenTownhallPayload data;
    private int x;
    private int y;

    public TownhallScreen(OpenTownhallPayload data) {
        super(Text.literal("Ayuntamiento"));
        this.data = data;
    }

    private Identifier background() {
        try {
            return Identifier.of(ModConfig.get().townhallGuiTexture);
        } catch (Exception e) {
            return Identifier.of("realeconomy", "textures/gui/townhall.png");
        }
    }

    @Override
    protected void init() {
        this.x = (this.width - BG_W) / 2;
        this.y = (this.height - BG_H) / 2;

        ButtonWidget buy = ButtonWidget.builder(Text.literal("Comprar licencia"),
                        b -> ClientPlayNetworking.send(new TownhallActionPayload(TownhallActionPayload.BUY_LICENSE)))
                .dimensions(x + 20, y + 88, BG_W - 40, 20).build();
        buy.active = data.configured();
        this.addDrawableChild(buy);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.drawTexture(background(), x, y, BG_W, BG_H, 0.0F, 0.0F, BG_W, BG_H, 256, 256);
        ctx.drawText(this.textRenderer, Text.literal("Ayuntamiento"), x + 8, y + 9, 0x404040, false);

        if (data.configured()) {
            ctx.drawText(this.textRenderer, Text.literal("Licencia de tienda"), x + 12, y + 32, 0x303030, false);
            ctx.drawText(this.textRenderer, Text.literal("Precio: " + data.price() + " " + data.currencyName()),
                    x + 12, y + 46, 0x303030, false);
            ctx.drawText(this.textRenderer, Text.literal("Licencias disponibles: " + data.available()),
                    x + 12, y + 60, 0x303030, false);
        } else {
            ctx.drawText(this.textRenderer, Text.literal("Licencias no configuradas."), x + 12, y + 40, 0xA00000, false);
            ctx.drawText(this.textRenderer, Text.literal("Avisa a un administrador."), x + 12, y + 54, 0x707070, false);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public void refresh(OpenTownhallPayload data) {
        this.data = data;
        this.clearAndInit();
    }
}
