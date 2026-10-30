package com.realeconomy.client;

import com.realeconomy.config.ModConfig;
import com.realeconomy.item.ModItems;
import com.realeconomy.network.BankActionPayload;
import com.realeconomy.network.CurrencyEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Interfaz del banco (estética vanilla). Muestra saldos y permite ingresar, retirar y transferir. */
public class BankScreen extends Screen {

    private static final int BG_W = 210;
    private static final int BG_H = 190;
    private static final int ROW_H = 20;
    private static final int MAX_ROWS = 5;
    private static final int LIST_TOP = 30;

    private Map<String, Long> balances;
    private List<String> rows = new ArrayList<>();
    private String selected;

    private TextFieldWidget amountField;
    private TextFieldWidget targetField;

    private int x;
    private int y;

    public BankScreen(Map<String, Long> balances) {
        super(Text.literal("Banco"));
        this.balances = balances;
    }

    private Identifier background() {
        try {
            return Identifier.of(ModConfig.get().bankGuiTexture);
        } catch (Exception e) {
            return Identifier.of("realeconomy", "textures/gui/bank.png");
        }
    }

    @Override
    protected void init() {
        this.x = (this.width - BG_W) / 2;
        this.y = (this.height - BG_H) / 2;

        // Filas: unión de monedas con saldo y monedas que el jugador lleva encima.
        Map<String, Integer> coins = coinCounts();
        LinkedHashMap<String, Long> union = new LinkedHashMap<>(balances);
        for (String id : coins.keySet()) {
            union.putIfAbsent(id, 0L);
        }
        rows = new ArrayList<>(union.keySet());
        if (rows.size() > MAX_ROWS) {
            rows = rows.subList(0, MAX_ROWS);
        }
        if (selected == null && !rows.isEmpty()) {
            selected = rows.get(0);
        }

        int fieldsY = y + LIST_TOP + MAX_ROWS * ROW_H + 6;

        this.amountField = new TextFieldWidget(this.textRenderer, x + 8, fieldsY, 60, 16, Text.literal("Cantidad"));
        this.amountField.setPlaceholder(Text.literal("cantidad"));
        this.addDrawableChild(this.amountField);

        this.targetField = new TextFieldWidget(this.textRenderer, x + 74, fieldsY, 80, 16, Text.literal("Jugador"));
        this.targetField.setPlaceholder(Text.literal("jugador"));
        this.addDrawableChild(this.targetField);

        int btnY = fieldsY + 22;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Ingresar"), b -> action(BankActionPayload.DEPOSIT))
                .dimensions(x + 8, btnY, 62, 18).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Retirar"), b -> action(BankActionPayload.WITHDRAW))
                .dimensions(x + 73, btnY, 62, 18).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Transferir"), b -> action(BankActionPayload.TRANSFER))
                .dimensions(x + 138, btnY, 64, 18).build());
    }

    private void action(int type) {
        if (selected == null) {
            return;
        }
        long amount = parseAmount();
        String target = targetField.getText().trim();
        ClientPlayNetworking.send(new BankActionPayload(type, selected, amount, target));
    }

    private long parseAmount() {
        try {
            String t = amountField.getText().trim();
            return t.isEmpty() ? 0L : Math.max(0L, Long.parseLong(t));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private Map<String, Integer> coinCounts() {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (this.client == null || this.client.player == null) {
            return map;
        }
        var inv = this.client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            String id = ModItems.currencyOf(stack);
            if (id != null) {
                map.merge(id, stack.getCount(), Integer::sum);
            }
        }
        return map;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.drawTexture(background(), x, y, BG_W, BG_H, 0.0F, 0.0F, BG_W, BG_H, 256, 256);
        ctx.drawText(this.textRenderer, Text.literal("Banco"), x + 8, y + 10, 0x404040, false);

        Map<String, Integer> coins = coinCounts();
        for (int i = 0; i < rows.size(); i++) {
            String id = rows.get(i);
            int rowY = y + LIST_TOP + i * ROW_H;
            if (id.equals(selected)) {
                ctx.fill(x + 8, rowY - 2, x + BG_W - 9, rowY + ROW_H - 4, 0x804CA0FF);
            }
            CurrencyEntry entry = ClientCurrencyStore.getEntry(id);
            String name = entry != null ? entry.name() : id;
            Formatting color = entry != null ? Formatting.byName(entry.colorName()) : null;
            if (color == null) {
                color = Formatting.GOLD;
            }
            ctx.drawTexture(ClientCurrencyStore.getTexture(id), x + 10, rowY, 16, 16, 0.0F, 0.0F, 64, 64, 64, 64);
            ctx.drawText(this.textRenderer, Text.literal(name).formatted(color), x + 30, rowY, 0xFFFFFF, false);
            long bal = balances.getOrDefault(id, 0L);
            int inv = coins.getOrDefault(id, 0);
            ctx.drawText(this.textRenderer, Text.literal("Saldo: " + bal + "   Monedas: " + inv),
                    x + 30, rowY + 10, 0x303030, false);
        }

        if (rows.isEmpty()) {
            ctx.drawText(this.textRenderer, Text.literal("No tienes saldo ni monedas."), x + 12, y + LIST_TOP + 4, 0x707070, false);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < rows.size(); i++) {
            int rowY = y + LIST_TOP + i * ROW_H;
            if (mx >= x + 8 && mx <= x + BG_W - 9 && my >= rowY - 2 && my <= rowY + ROW_H - 4) {
                selected = rows.get(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public void setBalances(Map<String, Long> balances) {
        this.balances = balances;
    }

    /** Actualiza los saldos y reconstruye la interfaz (conserva la moneda seleccionada). */
    public void refresh(Map<String, Long> balances) {
        this.balances = balances;
        this.clearAndInit();
    }
}
