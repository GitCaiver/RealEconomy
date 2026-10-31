package com.realeconomy.client;

import com.realeconomy.client.ui.Theme;
import com.realeconomy.client.ui.ThemedButton;
import com.realeconomy.client.ui.ThemedScreen;
import com.realeconomy.network.OpenTaxPolicyPayload;
import com.realeconomy.network.TaxPolicyActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Panel de impuestos por tipo del ayuntamiento (parte 4). El gobierno ve botones −/+ para editar
 * cada tipo; el resto de jugadores solo ve los valores.
 */
public class TaxScreen extends ThemedScreen {

    private static final int BG_W = 248;
    private static final int BG_H = 214;
    private static final int ROW_H = 20;

    private OpenTaxPolicyPayload data;

    /** Filas: clave de tipo + etiqueta visible. */
    private static final String[][] ROWS = {
            {"renta", "Renta (miembros)"},
            {"ventatienda", "Venta de tienda"},
            {"cuotatienda", "Cuota de tienda"},
            {"mercado", "Venta a NPC mercado"},
            {"transferencia", "Transferencia"},
            {"cambio", "Cambio de moneda"},
            {"subasta", "Subasta"},
    };

    public TaxScreen(OpenTaxPolicyPayload data) {
        super(Text.literal("Impuestos del país"), BG_W, BG_H);
        this.data = data;
    }

    @Override
    protected void initContent() {
        if (!data.government()) {
            return; // sin controles: solo lectura
        }
        int cl = contentLeft();
        int cw = contentWidth();
        int y0 = contentTop() + 10;
        for (int i = 0; i < ROWS.length; i++) {
            String type = ROWS[i][0];
            int ry = y0 + i * ROW_H;
            boolean fee = type.equals("cuotatienda");
            int bigStep = fee ? 10 : 5;
            // Botones: −grande −1 +1 +grande, alineados a la derecha.
            int bx = cl + cw - 4 * 22;
            this.addDrawableChild(ThemedButton.of(bx, ry, 20, 16, Text.literal(fee ? "-10" : "-5"), b -> send(type, -bigStep)));
            this.addDrawableChild(ThemedButton.of(bx + 22, ry, 20, 16, Text.literal("-1"), b -> send(type, -1)));
            this.addDrawableChild(ThemedButton.of(bx + 44, ry, 20, 16, Text.literal("+1"), b -> send(type, 1)));
            this.addDrawableChild(ThemedButton.of(bx + 66, ry, 20, 16, Text.literal(fee ? "+10" : "+5"), b -> send(type, bigStep)));
        }
    }

    private void send(String type, int delta) {
        ClientPlayNetworking.send(new TaxPolicyActionPayload(type, delta));
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cl = contentLeft();
        Formatting color = Formatting.byName(data.countryColor());
        centered(ctx, Text.literal("[" + data.countryTag() + "]").formatted(color == null ? Formatting.GOLD : color),
                left + bgW / 2, contentTop(), Theme.TITLE);
        if (!data.government()) {
            text(ctx, "Solo lectura (no eres del gobierno).", cl, contentTop(), Theme.TEXT_DIM);
        }

        int y0 = contentTop() + 10;
        for (int i = 0; i < ROWS.length; i++) {
            int ry = y0 + i * ROW_H;
            Theme.slot(ctx, cl, ry - 2, contentWidth(), ROW_H - 2);
            text(ctx, ROWS[i][1], cl + 4, ry + 2, Theme.TEXT);
            text(ctx, valueLabel(ROWS[i][0]), cl + 108, ry + 2, Theme.ACCENT);
        }
    }

    private String valueLabel(String type) {
        return switch (type) {
            case "renta" -> data.rent() + "%";
            case "ventatienda" -> data.shopSale() + "%";
            case "cuotatienda" -> data.shopFee() + " /" + data.shopFeePeriod();
            case "mercado" -> data.marketSale() + "%";
            case "transferencia" -> data.transfer() + "%";
            case "cambio" -> data.exchange() + "%" + (data.exchangeDefault() ? " (def)" : "");
            case "subasta" -> data.auction() + "%" + (data.auctionDefault() ? " (def)" : "");
            default -> "";
        };
    }

    public void refresh(OpenTaxPolicyPayload data) {
        this.data = data;
        this.clearAndInit();
    }
}
