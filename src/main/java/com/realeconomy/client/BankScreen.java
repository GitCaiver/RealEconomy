package com.realeconomy.client;

import com.realeconomy.client.ui.Theme;
import com.realeconomy.client.ui.ThemedButton;
import com.realeconomy.client.ui.ThemedScreen;
import com.realeconomy.item.ModItems;
import com.realeconomy.network.BankActionPayload;
import com.realeconomy.network.BankCurrencyInfo;
import com.realeconomy.network.CurrencyEntry;
import com.realeconomy.network.OpenBankPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Interfaz del banco: saldos, ingresar/retirar/transferir y cambio de moneda. */
public class BankScreen extends ThemedScreen {

    private static final int BG_W = 230;
    private static final int BG_H = 250;
    private static final int ROW_H = 22;
    private static final int MAX_ROWS = 3;

    private OpenBankPayload data;
    private Map<String, Long> balances;
    private List<String> rows = new ArrayList<>();
    private String selected;

    // Cambio de moneda.
    private final Map<String, BankCurrencyInfo> infos = new LinkedHashMap<>();
    private List<String> allCurrencies = new ArrayList<>();
    private int destIndex = 0;

    private TextFieldWidget amountField;
    private TextFieldWidget targetField;

    public BankScreen(OpenBankPayload data) {
        super(Text.literal("Banco"), BG_W, BG_H);
        apply(data);
    }

    private void apply(OpenBankPayload data) {
        this.data = data;
        this.balances = data.balances();
        this.infos.clear();
        this.allCurrencies = new ArrayList<>();
        for (BankCurrencyInfo info : data.currencies()) {
            this.infos.put(info.id(), info);
            this.allCurrencies.add(info.id());
        }
    }

    private int listTop() {
        return contentTop();
    }

    @Override
    protected void initContent() {
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

        int cl = contentLeft();
        int fieldsY = listTop() + MAX_ROWS * ROW_H + 6;

        this.amountField = new com.realeconomy.client.ui.ThemedTextField(this.textRenderer, cl, fieldsY, 70, 16, Text.literal("Cantidad"));
        this.amountField.setPlaceholder(Text.literal("cantidad"));
        this.addDrawableChild(this.amountField);

        this.targetField = new com.realeconomy.client.ui.ThemedTextField(this.textRenderer, cl + 78, fieldsY, contentWidth() - 78, 16, Text.literal("Jugador"));
        this.targetField.setPlaceholder(Text.literal("jugador"));
        this.addDrawableChild(this.targetField);

        int btnY = fieldsY + 22;
        int gap = 6;
        int bw = (contentWidth() - gap * 2) / 3;
        this.addDrawableChild(ThemedButton.of(cl, btnY, bw, 18, Text.literal("Ingresar"), b -> action(BankActionPayload.DEPOSIT)));
        this.addDrawableChild(ThemedButton.of(cl + bw + gap, btnY, bw, 18, Text.literal("Retirar"), b -> action(BankActionPayload.WITHDRAW)));
        this.addDrawableChild(ThemedButton.of(cl + (bw + gap) * 2, btnY, bw, 18, Text.literal("Transferir"), b -> action(BankActionPayload.TRANSFER)));

        // ---- Sección de cambio de moneda ----
        int exY = btnY + 40;
        if (!allCurrencies.isEmpty()) {
            // Selector de moneda destino (cicla por todas las monedas).
            this.addDrawableChild(ThemedButton.of(cl, exY, contentWidth() - 60, 18, destLabel(), b -> {
                destIndex = (destIndex + 1) % allCurrencies.size();
                b.setMessage(destLabel());
            }));
            this.addDrawableChild(ThemedButton.of(cl + contentWidth() - 56, exY, 56, 18, Text.literal("Cambiar"), b -> {
                String dest = allCurrencies.get(destIndex);
                long amount = parseAmount();
                if (selected != null && amount > 0) {
                    ClientPlayNetworking.send(new BankActionPayload(BankActionPayload.EXCHANGE, selected, amount, dest));
                }
            }));
        }
    }

    private Text destLabel() {
        if (allCurrencies.isEmpty()) {
            return Text.literal("—");
        }
        BankCurrencyInfo info = infos.get(allCurrencies.get(destIndex));
        if (info == null) {
            return Text.literal("→ ?");
        }
        Formatting color = Formatting.byName(info.colorName());
        return Text.literal("→ " + info.name() + " [" + info.tag() + "]")
                .formatted(color == null ? Formatting.GOLD : color);
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
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cl = contentLeft();
        Map<String, Integer> coins = coinCounts();

        for (int i = 0; i < rows.size(); i++) {
            String id = rows.get(i);
            int rowY = listTop() + i * ROW_H;
            Theme.slot(ctx, cl, rowY, contentWidth(), ROW_H - 3);
            if (id.equals(selected)) {
                ctx.fill(cl + 2, rowY + 2, cl + contentWidth() - 2, rowY + ROW_H - 5, Theme.ROW_SELECTED);
            }
            CurrencyEntry entry = ClientCurrencyStore.getEntry(id);
            String name = entry != null ? entry.name() : id;
            Formatting color = entry != null ? Formatting.byName(entry.colorName()) : null;
            if (color == null) {
                color = Formatting.GOLD;
            }
            ctx.drawTexture(ClientCurrencyStore.getTexture(id), cl + 3, rowY + 2, 15, 15, 0.0F, 0.0F, 64, 64, 64, 64);
            text(ctx, Text.literal(name).formatted(color), cl + 23, rowY + 2, Theme.TEXT);
            long bal = balances.getOrDefault(id, 0L);
            int inv = coins.getOrDefault(id, 0);
            text(ctx, "Saldo: " + bal + "   Monedas: " + inv, cl + 23, rowY + 11, Theme.TEXT_DIM);
        }

        if (rows.isEmpty()) {
            text(ctx, "No tienes saldo ni monedas.", cl + 2, listTop() + 6, Theme.TEXT_DIM);
        }

        // Cabecera y previsualización de la sección de cambio.
        int exHeaderY = listTop() + MAX_ROWS * ROW_H + 6 + 22 + 40 - 12;
        Theme.separator(ctx, cl, exHeaderY, contentWidth());
        text(ctx, Text.literal("Cambio de moneda").formatted(Formatting.AQUA), cl, exHeaderY + 4, Theme.ACCENT);

        // Previsualización: cuánto recibirá.
        int previewY = exHeaderY + 40;
        String preview = previewText();
        text(ctx, preview, cl, previewY, Theme.TEXT_OK);
    }

    /** Calcula localmente la previsualización del cambio con los valores del snapshot. */
    private String previewText() {
        if (selected == null || allCurrencies.isEmpty()) {
            return "";
        }
        String destId = allCurrencies.get(destIndex);
        BankCurrencyInfo origin = infos.get(selected);
        BankCurrencyInfo dest = infos.get(destId);
        if (origin == null || dest == null) {
            return "";
        }
        if (selected.equals(destId)) {
            return "Elige una moneda destino distinta.";
        }
        long amount = parseAmount();
        if (amount <= 0 || origin.value() <= 0 || dest.value() <= 0) {
            return "Recibirás: —";
        }
        double emeralds = amount * origin.value();
        long gross = (long) Math.floor(emeralds / dest.value());
        long commission = (long) Math.floor(gross * dest.exchangePercent() / 100.0);
        long net = gross - commission;
        return "Recibirás: " + Math.max(0, net) + " " + dest.name() + " [" + dest.tag()
                + "]  (comisión " + dest.exchangePercent() + "%)";
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cl = contentLeft();
        for (int i = 0; i < rows.size(); i++) {
            int rowY = listTop() + i * ROW_H;
            if (mx >= cl && mx <= cl + contentWidth() && my >= rowY && my <= rowY + ROW_H - 3) {
                selected = rows.get(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Actualiza los datos y reconstruye la interfaz (conserva la moneda seleccionada y el destino). */
    public void refresh(OpenBankPayload data) {
        apply(data);
        if (destIndex >= allCurrencies.size()) {
            destIndex = 0;
        }
        this.clearAndInit();
    }
}
