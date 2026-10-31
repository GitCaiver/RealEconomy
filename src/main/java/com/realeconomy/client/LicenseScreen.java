package com.realeconomy.client;

import com.realeconomy.client.ui.Theme;
import com.realeconomy.client.ui.ThemedButton;
import com.realeconomy.client.ui.ThemedScreen;
import com.realeconomy.network.LicenseActionPayload;
import com.realeconomy.network.LicenseView;
import com.realeconomy.network.OpenLicensesPayload;
import com.realeconomy.util.TimeUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Panel de licencias personalizadas del ayuntamiento (parte 5). Lista las licencias que ofrece
 * el país con un botón "Comprar" en cada una. La creación/edición se hace por comando (/licencia).
 */
public class LicenseScreen extends ThemedScreen {

    private static final int BG_W = 248;
    private static final int ROW_H = 30;
    private static final int MAX_ROWS = 6;

    private OpenLicensesPayload data;

    public LicenseScreen(OpenLicensesPayload data) {
        super(Text.literal("Licencias del país"), BG_W, computeHeight(data));
        this.data = data;
    }

    private static int computeHeight(OpenLicensesPayload data) {
        int rows = Math.min(MAX_ROWS, Math.max(1, data.licenses().size()));
        return 56 + rows * ROW_H + 14;
    }

    @Override
    protected void initContent() {
        int cl = contentLeft();
        int cw = contentWidth();
        int y0 = contentTop() + 24;
        var licenses = data.licenses();
        int shown = Math.min(MAX_ROWS, licenses.size());
        for (int i = 0; i < shown; i++) {
            LicenseView l = licenses.get(i);
            int ry = y0 + i * ROW_H;
            boolean permanentHeld = l.heldExpiry() == 0;
            String label = permanentHeld ? "En posesión" : "Comprar";
            ThemedButton buy = ThemedButton.of(cl + cw - 64, ry + 4, 60, 18, Text.literal(label),
                    b -> ClientPlayNetworking.send(new LicenseActionPayload(l.id())));
            buy.active = !permanentHeld; // una licencia permanente ya poseída no se recompra
            this.addDrawableChild(buy);
        }
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cl = contentLeft();
        int cw = contentWidth();
        Formatting color = Formatting.byName(data.countryColor());
        centered(ctx, Text.literal("[" + data.countryTag() + "]").formatted(color == null ? Formatting.GOLD : color),
                left + bgW / 2, contentTop(), Theme.TITLE);

        var licenses = data.licenses();
        if (licenses.isEmpty()) {
            text(ctx, "Tu país no ofrece licencias.", cl, contentTop() + 24, Theme.TEXT_DIM);
            if (data.government()) {
                text(ctx, "Crea una con /licencia crear …", cl, contentTop() + 36, Theme.TEXT_DIM);
            }
            return;
        }

        long now = System.currentTimeMillis();
        int y0 = contentTop() + 24;
        int shown = Math.min(MAX_ROWS, licenses.size());
        for (int i = 0; i < shown; i++) {
            LicenseView l = licenses.get(i);
            int ry = y0 + i * ROW_H;
            Theme.slot(ctx, cl, ry, cw, ROW_H - 4);
            text(ctx, l.name(), cl + 4, ry + 3, Theme.TEXT);
            // Línea de detalle: precio + duración + permiso.
            String dur = l.durationMillis() <= 0 ? "permanente" : TimeUtil.format(l.durationMillis());
            text(ctx, l.price() + " " + l.currencyName() + " · " + dur + " · " + l.permission(),
                    cl + 4, ry + 14, Theme.TEXT_DIM);
            // Estado de posesión.
            String state = statusLabel(l, now);
            if (state != null) {
                text(ctx, state, cl + cw - 64 - 92, ry + 9, Theme.TEXT_OK);
            }
        }
        if (licenses.size() > MAX_ROWS) {
            text(ctx, "… y " + (licenses.size() - MAX_ROWS) + " más (usa /licencia lista).",
                    cl, y0 + shown * ROW_H, Theme.TEXT_DIM);
        }
    }

    private static String statusLabel(LicenseView l, long now) {
        long exp = l.heldExpiry();
        if (exp < 0) {
            return null; // no la tiene
        }
        if (exp == 0) {
            return "tienes ∞";
        }
        if (exp > now) {
            return "tienes " + TimeUtil.format(exp - now);
        }
        return "caducada";
    }

    public void refresh(OpenLicensesPayload data) {
        this.data = data;
        this.clearAndInit();
    }
}
