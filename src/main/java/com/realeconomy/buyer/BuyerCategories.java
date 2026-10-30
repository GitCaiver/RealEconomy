package com.realeconomy.buyer;

import com.realeconomy.config.ModConfig;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.Map;

/** Resuelve la categoría de un item y su precio base (esmeraldas) según la config. */
public final class BuyerCategories {

    private BuyerCategories() {
    }

    /** Categoría configurada a la que pertenece el item (la primera que casa), o null. */
    public static String categoryOf(Item item) {
        for (Map.Entry<String, String> e : ModConfig.get().buyerCategories.entrySet()) {
            Identifier tagId = Identifier.tryParse(e.getValue());
            if (tagId == null) {
                continue;
            }
            TagKey<Item> key = TagKey.of(RegistryKeys.ITEM, tagId);
            if (Registries.ITEM.getEntry(item).isIn(key)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Precio base en esmeraldas de un item: override por item, luego por categoría, luego global. */
    public static double basePrice(Item item, String category) {
        ModConfig cfg = ModConfig.get();
        String id = Registries.ITEM.getId(item).toString();
        Double byItem = cfg.buyerItemPrices.get(id);
        if (byItem != null) {
            return byItem;
        }
        if (category != null) {
            Double byCat = cfg.buyerCategoryPrices.get(category);
            if (byCat != null) {
                return byCat;
            }
        }
        return cfg.buyerBasePrice;
    }

    /** Precio base en esmeraldas de una categoría (override de categoría o base global). */
    public static double categoryBasePrice(String category) {
        ModConfig cfg = ModConfig.get();
        return cfg.buyerCategoryPrices.getOrDefault(category, cfg.buyerBasePrice);
    }
}
