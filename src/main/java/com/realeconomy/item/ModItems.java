package com.realeconomy.item;

import com.mojang.serialization.Codec;
import com.realeconomy.RealEconomy;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ModItems {

    /** Componente que guarda el id de la moneda en el ItemStack. */
    public static ComponentType<String> CURRENCY_ID;

    /** Item de moneda (único para todas las divisas). */
    public static Item COIN;

    /** Item "núcleo territorial": al colocarlo reclama un área para el país del gobierno que lo pone. */
    public static Item TERRITORY_CORE;

    /** Grupo creativo del mod. */
    public static ItemGroup ITEM_GROUP;
    public static RegistryKey<ItemGroup> ITEM_GROUP_KEY;

    private ModItems() {
    }

    public static void register() {
        CURRENCY_ID = Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of(RealEconomy.MOD_ID, "currency_id"),
                ComponentType.<String>builder()
                        .codec(Codec.STRING)
                        .packetCodec(PacketCodecs.STRING)
                        .build()
        );

        COIN = Registry.register(
                Registries.ITEM,
                Identifier.of(RealEconomy.MOD_ID, "coin"),
                new CoinItem(new Item.Settings().maxCount(64))
        );

        TERRITORY_CORE = Registry.register(
                Registries.ITEM,
                Identifier.of(RealEconomy.MOD_ID, "nucleo"),
                new NucleoItem(new Item.Settings().maxCount(16))
        );

        ITEM_GROUP_KEY = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(RealEconomy.MOD_ID, "general"));
        ITEM_GROUP = FabricItemGroup.builder()
                .icon(() -> new ItemStack(COIN))
                .displayName(Text.translatable("itemGroup.realeconomy.general"))
                .entries((displayContext, entries) -> {
                    entries.add(new ItemStack(COIN));
                    entries.add(new ItemStack(TERRITORY_CORE));
                })
                .build();
        Registry.register(Registries.ITEM_GROUP, ITEM_GROUP_KEY, ITEM_GROUP);
    }

    /** Crea un stack de monedas de la divisa indicada. */
    public static ItemStack createCoin(String currencyId, int count) {
        ItemStack stack = new ItemStack(COIN, count);
        stack.set(CURRENCY_ID, currencyId);
        return stack;
    }

    /** Devuelve el id de divisa de un stack, o null si no es una moneda nuestra. */
    public static String currencyOf(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(COIN)) {
            return null;
        }
        return stack.get(CURRENCY_ID);
    }
}
