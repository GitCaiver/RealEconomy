package com.realeconomy.client;

import com.realeconomy.client.ui.Theme;
import com.realeconomy.client.ui.ThemedButton;
import com.realeconomy.client.ui.ThemedScreen;
import com.realeconomy.network.BookRow;
import com.realeconomy.network.LibrarianActionPayload;
import com.realeconomy.network.OpenLibrarianPayload;
import com.realeconomy.util.TimeUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;

/** Interfaz del librero: compra de libros encantados con icono real, brillo y tooltip. */
public class LibrarianScreen extends ThemedScreen {

    private static final int BG_W = 230;
    private static final int BG_H = 210;
    private static final int ROW_H = 26;

    private OpenLibrarianPayload data;

    public LibrarianScreen(OpenLibrarianPayload data) {
        super(Text.literal("Libros encantados"), BG_W, BG_H);
        this.data = data;
    }

    @Override
    protected void initContent() {
        int cl = contentLeft();
        int y = contentTop() + 18;
        for (BookRow r : data.rows()) {
            final int index = r.index();
            boolean soldOut = r.units() <= 0;
            ThemedButton buy = ThemedButton.of(cl + contentWidth() - 56, y + 3, 52, 18,
                    Text.literal(soldOut ? "Agotado" : "Comprar"),
                    b -> ClientPlayNetworking.send(new LibrarianActionPayload(data.villager(), index)));
            buy.active = !soldOut;
            this.addDrawableChild(buy);
            y += ROW_H;
        }
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cl = contentLeft();
        text(ctx, "El librero vende (" + data.currencyName() + "):", cl, contentTop(), Theme.TEXT);
        if (data.secondsToRotation() > 0) {
            String t = "Cambia en " + TimeUtil.format(data.secondsToRotation() * 1000L);
            text(ctx, Text.literal(t), cl + contentWidth() - this.textRenderer.getWidth(t), contentTop(), Theme.TEXT_DIM);
        }

        int y = contentTop() + 18;
        for (BookRow r : data.rows()) {
            Theme.slot(ctx, cl, y, contentWidth(), ROW_H - 2);
            ctx.drawItem(r.book(), cl + 4, y + 3);
            text(ctx, bookName(r), cl + 26, y + 2, Theme.TEXT);
            text(ctx, r.price() + " · " + (r.units() > 0 ? r.units() + " ud" : "agotado"),
                    cl + 26, y + 13, r.units() > 0 ? Theme.TEXT_DIM : Theme.TEXT_WARN);
            y += ROW_H;
        }
    }

    @Override
    protected void renderTooltips(DrawContext ctx, int mouseX, int mouseY) {
        int cl = contentLeft();
        int y = contentTop() + 18;
        for (BookRow r : data.rows()) {
            itemTooltip(ctx, r.book(), cl + 4, y + 3, mouseX, mouseY);
            y += ROW_H;
        }
    }

    private String bookName(BookRow r) {
        try {
            ItemEnchantmentsComponent comp = r.book().get(DataComponentTypes.STORED_ENCHANTMENTS);
            if (comp != null) {
                for (RegistryEntry<Enchantment> e : comp.getEnchantments()) {
                    return Enchantment.getName(e, comp.getLevel(e)).getString();
                }
            }
        } catch (Throwable ignored) {
            // Cae al nombre del item.
        }
        return r.book().getName().getString();
    }

    public void refresh(OpenLibrarianPayload data) {
        this.data = data;
        this.clearAndInit();
    }
}
