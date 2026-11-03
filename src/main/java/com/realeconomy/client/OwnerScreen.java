package com.realeconomy.client;

import com.realeconomy.attitude.AttitudeMode;
import com.realeconomy.client.ui.Theme;
import com.realeconomy.client.ui.ThemedButton;
import com.realeconomy.client.ui.ThemedScreen;
import com.realeconomy.network.OpenOwnerPayload;
import com.realeconomy.network.OwnerActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** GUI de dueño de un aldeano de MCA: ver/quitar equipo y fijar el modo de actitud. */
public class OwnerScreen extends ThemedScreen {

    private static final int BG_W = 220;
    private static final int BG_H = 200;

    private OpenOwnerPayload data;
    private int slotX;
    private int slotY;

    public OwnerScreen(OpenOwnerPayload data) {
        super(Text.literal("Aldeano: equipo y modo"), BG_W, BG_H);
        this.data = data;
    }

    @Override
    protected void initContent() {
        int cl = contentLeft();
        slotX = cl;
        slotY = contentTop() + 26;

        int by = top + BG_H - 52;
        int bw = (contentWidth() - 8) / 3;
        AttitudeMode[] modes = AttitudeMode.values();
        for (int i = 0; i < modes.length; i++) {
            final AttitudeMode m = modes[i];
            boolean current = m.id() == data.mode();
            this.addDrawableChild(ThemedButton.of(cl + i * (bw + 4), by, bw, 20,
                    Text.literal((current ? "▸ " : "") + m.display()),
                    b -> ClientPlayNetworking.send(new OwnerActionPayload(data.villager(), OwnerActionPayload.SET_MODE, m.id()))));
        }
        this.addDrawableChild(ThemedButton.of(cl, top + BG_H - 28, contentWidth(), 18,
                Text.literal("Cerrar"), b -> this.close()));
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cl = contentLeft();
        text(ctx, Text.literal(data.name()), cl, contentTop(), Theme.TITLE);
        text(ctx, "Equipo (clic en un slot para quitarlo):", cl, contentTop() + 12, Theme.TEXT_DIM);

        for (int i = 0; i < data.equipment().size(); i++) {
            int x = slotX + i * 22;
            Theme.slot(ctx, x, slotY, 20, 20);
            ItemStack s = data.equipment().get(i);
            if (!s.isEmpty()) {
                ctx.drawItem(s, x + 2, slotY + 2);
            }
        }
        text(ctx, "Equipa objetos con clic derecho sobre el aldeano llevándolos en la mano.",
                cl, slotY + 26, Theme.TEXT_DIM);

        AttitudeMode cur = AttitudeMode.byId(data.mode());
        text(ctx, "Modo actual: " + cur.display() + " — " + describe(cur), cl, slotY + 42, Theme.TEXT);
    }

    private static String describe(AttitudeMode m) {
        return switch (m) {
            case PASIVO -> "no ataca nunca";
            case NEUTRAL -> "se defiende si le pegan";
            case AGRESIVO -> "ataca a enemigos a la vista";
        };
    }

    @Override
    protected void renderTooltips(DrawContext ctx, int mouseX, int mouseY) {
        for (int i = 0; i < data.equipment().size(); i++) {
            int x = slotX + i * 22;
            itemTooltip(ctx, data.equipment().get(i), x + 2, slotY + 2, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < data.equipment().size(); i++) {
            int x = slotX + i * 22;
            if (mouseX >= x && mouseX < x + 20 && mouseY >= slotY && mouseY < slotY + 20
                    && !data.equipment().get(i).isEmpty()) {
                ClientPlayNetworking.send(new OwnerActionPayload(data.villager(), OwnerActionPayload.UNEQUIP, i));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void refresh(OpenOwnerPayload data) {
        this.data = data;
        this.clearAndInit();
    }
}
