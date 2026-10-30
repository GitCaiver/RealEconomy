package com.realeconomy.client;

import com.realeconomy.config.ModConfig;
import com.realeconomy.network.OpenTownhallPayload;
import com.realeconomy.network.TownhallActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Interfaz del ayuntamiento: compra de licencias y política monetaria del país. */
public class TownhallScreen extends Screen {

    private static final int BG_W = 190;
    private static final int BG_H = 180;

    private OpenTownhallPayload data;
    private TextFieldWidget printField;
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
                        b -> ClientPlayNetworking.send(new TownhallActionPayload(TownhallActionPayload.BUY_LICENSE, 0)))
                .dimensions(x + 20, y + 70, BG_W - 40, 20).build();
        buy.active = data.configured();
        this.addDrawableChild(buy);

        if (data.canPrint()) {
            this.printField = new TextFieldWidget(this.textRenderer, x + 20, y + 140, 70, 18, Text.literal("cantidad"));
            this.printField.setPlaceholder(Text.literal("cantidad"));
            this.addDrawableChild(this.printField);

            ButtonWidget print = ButtonWidget.builder(Text.literal("Imprimir"), b -> {
                long amount = parseAmount(this.printField.getText());
                if (amount > 0) {
                    ClientPlayNetworking.send(new TownhallActionPayload(TownhallActionPayload.PRINT, amount));
                }
            }).dimensions(x + 98, y + 140, BG_W - 98 - 20, 18).build();
            this.addDrawableChild(print);
        }
    }

    private static long parseAmount(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.drawTexture(background(), x, y, BG_W, BG_H, 0.0F, 0.0F, BG_W, BG_H, 256, 256);
        ctx.drawText(this.textRenderer, Text.literal("Ayuntamiento"), x + 8, y + 9, 0x404040, false);

        if (data.configured()) {
            ctx.drawText(this.textRenderer, Text.literal("Licencia: " + data.price() + " " + data.currencyName()),
                    x + 12, y + 30, 0x303030, false);
            ctx.drawText(this.textRenderer, Text.literal("Disponibles: " + data.available()),
                    x + 12, y + 44, 0x303030, false);
        } else {
            ctx.drawText(this.textRenderer, Text.literal("Licencias no configuradas."), x + 12, y + 30, 0xA00000, false);
            ctx.drawText(this.textRenderer, Text.literal("Avisa a un administrador."), x + 12, y + 44, 0x707070, false);
        }

        // Panel de política monetaria del país del jugador.
        int my = y + 98;
        ctx.drawText(this.textRenderer, Text.literal("Política monetaria").formatted(Formatting.DARK_GRAY),
                x + 12, my, 0x303030, false);
        if (!data.hasCountry()) {
            ctx.drawText(this.textRenderer, Text.literal("No perteneces a ningún país."), x + 12, my + 14, 0x707070, false);
        } else {
            ctx.drawText(this.textRenderer, Text.literal("País [" + data.countryTag() + "]"), x + 12, my + 14, 0x303030, false);
            ctx.drawText(this.textRenderer, Text.literal("Circulación: " + data.circulation()), x + 12, my + 26, 0x303030, false);
            ctx.drawText(this.textRenderer,
                    Text.literal("Valor: " + String.format(java.util.Locale.ROOT, "%.3f", data.value()) + " esm/moneda"),
                    x + 12, my + 38, 0x303030, false);
            if (!data.canPrint()) {
                ctx.drawText(this.textRenderer, Text.literal("(solo el líder imprime)"), x + 12, my + 50, 0x909090, false);
            }
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
