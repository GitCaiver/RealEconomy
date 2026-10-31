package com.realeconomy.client;

import com.realeconomy.client.ui.Theme;
import com.realeconomy.client.ui.ThemedButton;
import com.realeconomy.client.ui.ThemedScreen;
import com.realeconomy.client.ui.ThemedSlider;
import com.realeconomy.network.OpenTownhallPayload;
import com.realeconomy.network.TownhallActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Interfaz del ayuntamiento: licencias y política monetaria (imprimir + barra de impuestos). */
public class TownhallScreen extends ThemedScreen {

    private static final int BG_W = 210;
    private static final int BG_H = 262;

    private OpenTownhallPayload data;
    private TextFieldWidget printField;

    public TownhallScreen(OpenTownhallPayload data) {
        super(Text.literal("Ayuntamiento"), BG_W, BG_H);
        this.data = data;
    }

    @Override
    protected void initContent() {
        int cl = contentLeft();
        int cw = contentWidth();
        int top0 = contentTop();

        ThemedButton buy = ThemedButton.of(cl, top0 + 30, cw, 18, Text.literal("Comprar licencia"),
                b -> ClientPlayNetworking.send(new TownhallActionPayload(TownhallActionPayload.BUY_LICENSE, 0)));
        buy.active = data.configured();
        this.addDrawableChild(buy);

        // Panel de licencias personalizadas del país (parte 5): abierto a todos (compran aquí).
        this.addDrawableChild(ThemedButton.of(cl, top0 + 50, cw, 18, Text.literal("Licencias del país…"),
                b -> ClientPlayNetworking.send(new TownhallActionPayload(TownhallActionPayload.OPEN_LICENSES, 0))));

        if (data.canPrint()) {
            int py = top0 + 140;
            this.printField = new com.realeconomy.client.ui.ThemedTextField(this.textRenderer, cl, py, 74, 18, Text.literal("cantidad"));
            this.printField.setPlaceholder(Text.literal("cantidad"));
            this.addDrawableChild(this.printField);

            this.addDrawableChild(ThemedButton.of(cl + 80, py, cw - 80, 18, Text.literal("Imprimir"), b -> {
                long amount = parseAmount(this.printField.getText());
                if (amount > 0) {
                    ClientPlayNetworking.send(new TownhallActionPayload(TownhallActionPayload.PRINT, amount));
                }
            }));

            this.addDrawableChild(new TaxSlider(cl, py + 24, cw, 18, data.taxRate()));

            // Panel de impuestos por tipo (solo gobierno).
            this.addDrawableChild(ThemedButton.of(cl, py + 46, cw, 18, Text.literal("Impuestos por tipo…"),
                    b -> ClientPlayNetworking.send(new TownhallActionPayload(TownhallActionPayload.OPEN_TAXES, 0))));
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
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cl = contentLeft();
        int y = contentTop();

        if (data.configured()) {
            text(ctx, "Licencia: " + data.price() + " " + data.currencyName(), cl, y, Theme.TEXT);
            text(ctx, "Disponibles: " + data.available(), cl, y + 12, Theme.TEXT_DIM);
        } else {
            text(ctx, "Licencias no configuradas.", cl, y, Theme.TEXT_WARN);
            text(ctx, "Avisa a un administrador.", cl, y + 12, Theme.TEXT_DIM);
        }

        // Sección política monetaria.
        int my = y + 78;
        centered(ctx, Text.literal("· Política monetaria ·"), left + bgW / 2, my, Theme.TITLE);
        if (!data.hasCountry()) {
            text(ctx, "No perteneces a ningún país.", cl, my + 14, Theme.TEXT_DIM);
        } else {
            text(ctx, "País [" + data.countryTag() + "]  ·  impuesto " + data.taxRate() + "%", cl, my + 14, Theme.TEXT);
            text(ctx, "Circulación: " + data.circulation(), cl, my + 26, Theme.TEXT_DIM);
            text(ctx, "Valor: " + String.format(java.util.Locale.ROOT, "%.3f", data.value()) + " esm/moneda",
                    cl, my + 38, Theme.TEXT_DIM);
            if (!data.canPrint()) {
                text(ctx, "(solo el gobierno gestiona)", cl, my + 52, Theme.TEXT_DIM);
            }
        }
    }

    public void refresh(OpenTownhallPayload data) {
        this.data = data;
        this.clearAndInit();
    }

    /** Barra 0-100 para ajustar el impuesto general del país; envía al soltar. */
    private static class TaxSlider extends ThemedSlider {
        TaxSlider(int x, int y, int w, int h, int rate) {
            super(x, y, w, h, Text.empty(), Math.max(0, Math.min(100, rate)) / 100.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Impuestos: " + percent() + "%"));
        }

        @Override
        protected void applyValue() {
            // Se envía al soltar (onRelease) para no spamear la red durante el arrastre.
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            ClientPlayNetworking.send(new TownhallActionPayload(TownhallActionPayload.SET_TAX, percent()));
        }
    }
}
