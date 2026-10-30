package com.realeconomy.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.function.Function;

/**
 * Item físico de moneda. Todas las monedas comparten este item; cada una se
 * distingue por el componente {@link ModItems#CURRENCY_ID}. Así las monedas de
 * la misma divisa se apilan y las de divisas distintas no.
 */
public class CoinItem extends Item {

    /**
     * Resuelve el nombre a mostrar de una moneda (id -&gt; texto con color y siglas),
     * o null si no se conoce. Lo instala el cliente a partir de sus metadatos de
     * moneda; en el servidor puede quedar sin instalar (ahí no se pinta el nombre).
     */
    public static Function<String, Text> NAME_RESOLVER;

    public CoinItem(Settings settings) {
        super(settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        String id = stack.get(ModItems.CURRENCY_ID);
        if (id != null && NAME_RESOLVER != null) {
            Text name = NAME_RESOLVER.apply(id);
            if (name != null) {
                return name;
            }
        }
        return super.getName(stack);
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
