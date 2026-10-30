package com.realeconomy.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;

import java.util.Optional;

/**
 * Item físico de moneda. Todas las monedas comparten este item; cada una se
 * distingue por el componente {@link ModItems#CURRENCY_ID}. Así las monedas de
 * la misma divisa se apilan y las de divisas distintas no.
 */
public class CoinItem extends Item {

    public CoinItem(Settings settings) {
        super(settings);
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        String id = stack.get(ModItems.CURRENCY_ID);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.of(new CoinTooltipData(id));
    }
}
