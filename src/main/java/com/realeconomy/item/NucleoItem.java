package com.realeconomy.item;

import com.realeconomy.territory.TerritoryCoreActions;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Item "núcleo territorial": al colocarlo (clic derecho sobre una cara de bloque), el
 * gobierno de un país funda una provincia y reclama el área alrededor del núcleo. El bloque
 * resultante es indestructible salvo para el bando atacante durante una guerra por esa provincia.
 */
public class NucleoItem extends Item {

    public NucleoItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayerEntity player)
                || !(context.getWorld() instanceof ServerWorld world)) {
            return ActionResult.PASS;
        }
        BlockPos placePos = context.getBlockPos().offset(context.getSide());
        return TerritoryCoreActions.place(player, world, placePos, context.getStack());
    }

    @Override
    public void appendTooltip(net.minecraft.item.ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Colócalo (siendo gobierno) para reclamar un área.")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Es de piedra base: en guerra, el atacante lo rompe golpeándolo.")
                .formatted(Formatting.DARK_GRAY));
    }
}
