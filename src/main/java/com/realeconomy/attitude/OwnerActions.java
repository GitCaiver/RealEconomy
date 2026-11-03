package com.realeconomy.attitude;

import com.realeconomy.compat.mca.Mca;
import com.realeconomy.network.OpenOwnerPayload;
import com.realeconomy.network.OwnerActionPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Lógica de servidor para el dueño de un aldeano de MCA (familia jugadora): equipar/desequipar
 * armadura y armas y cambiar su modo de actitud. Solo el dueño puede hacerlo.
 */
public final class OwnerActions {

    /** Orden de slots que viaja a la GUI. */
    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    private OwnerActions() {
    }

    /** ¿Es el item "equipo" que tiene sentido ponerle a un aldeano (armadura o arma)? */
    public static boolean isGear(ItemStack stack) {
        net.minecraft.item.Item it = stack.getItem();
        return it instanceof net.minecraft.item.ArmorItem
                || it instanceof net.minecraft.item.ElytraItem
                || it instanceof net.minecraft.item.SwordItem
                || it instanceof net.minecraft.item.RangedWeaponItem
                || it instanceof net.minecraft.item.TridentItem
                || it instanceof net.minecraft.item.MaceItem
                || it instanceof net.minecraft.item.ShieldItem
                || it instanceof net.minecraft.item.AxeItem;
    }

    public static void openFor(ServerPlayerEntity player, Entity villager) {
        MinecraftServer server = player.getServer();
        if (server == null || !Mca.isMcaVillager(villager)) {
            return;
        }
        if (!Mca.isOwner(villager, player.getUuid())) {
            player.sendMessage(Text.literal("Solo su familia (pareja/padre/madre) puede equiparlo o cambiarle el modo.")
                    .formatted(Formatting.RED), false);
            return;
        }
        ServerPlayNetworking.send(player, build(server, (LivingEntity) villager));
    }

    private static OpenOwnerPayload build(MinecraftServer server, LivingEntity villager) {
        AttitudeMode mode = AttitudeManager.get(server).get(villager.getUuid());
        List<ItemStack> eq = new ArrayList<>(SLOTS.length);
        for (EquipmentSlot s : SLOTS) {
            eq.add(villager.getEquippedStack(s).copy());
        }
        return new OpenOwnerPayload(villager.getUuid(), villager.getName().getString(), mode.id(), eq);
    }

    /** Equipa el objeto que el dueño lleva en la mano (clic derecho con el objeto sobre el aldeano). */
    public static void equipHeld(ServerPlayerEntity player, Entity villager) {
        MinecraftServer server = player.getServer();
        if (server == null || !Mca.isMcaVillager(villager) || !Mca.isOwner(villager, player.getUuid())) {
            return;
        }
        LivingEntity v = (LivingEntity) villager;
        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) {
            return;
        }
        EquipmentSlot slot = v.getPreferredEquipmentSlot(held);
        ItemStack previous = v.getEquippedStack(slot);
        v.equipStack(slot, held.copyWithCount(1));
        if (!player.isCreative()) {
            held.decrement(1);
        }
        if (!previous.isEmpty()) {
            player.getInventory().offerOrDrop(previous);
        }
        player.sendMessage(Text.literal("Equipado en " + slot.getName() + ".").formatted(Formatting.GREEN), true);
        ServerPlayNetworking.send(player, build(server, v));
    }

    /** Vista de ejemplo de la GUI de dueño para /ecoadmin debug aldeano (sin aldeano real). */
    public static void debugOpen(ServerPlayerEntity player) {
        List<ItemStack> eq = new ArrayList<>();
        for (int i = 0; i < SLOTS.length; i++) {
            eq.add(ItemStack.EMPTY);
        }
        ServerPlayNetworking.send(player, new OpenOwnerPayload(player.getUuid(),
                "Aldeano de ejemplo", AttitudeMode.PASIVO.id(), eq));
    }

    public static void handle(ServerPlayerEntity player, OwnerActionPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Entity entity = player.getServerWorld().getEntity(payload.villager());
        if (entity == null || !Mca.isMcaVillager(entity) || !Mca.isOwner(entity, player.getUuid())) {
            player.sendMessage(Text.literal("No puedes gestionar a este aldeano.").formatted(Formatting.RED), false);
            return;
        }
        LivingEntity v = (LivingEntity) entity;
        if (payload.action() == OwnerActionPayload.SET_MODE) {
            AttitudeMode mode = AttitudeMode.byId(payload.value());
            AttitudeManager.get(server).set(v.getUuid(), mode);
            player.sendMessage(Text.literal("Modo: " + mode.display() + ".").formatted(Formatting.GOLD), true);
        } else if (payload.action() == OwnerActionPayload.UNEQUIP) {
            int idx = payload.value();
            if (idx >= 0 && idx < SLOTS.length) {
                EquipmentSlot slot = SLOTS[idx];
                ItemStack cur = v.getEquippedStack(slot);
                if (!cur.isEmpty()) {
                    player.getInventory().offerOrDrop(cur.copy());
                    v.equipStack(slot, ItemStack.EMPTY);
                }
            }
        }
        ServerPlayNetworking.send(player, build(server, v));
    }

    /** Suelta el equipo del aldeano (al morir, si la config lo permite). */
    public static void dropEquipment(LivingEntity villager) {
        for (EquipmentSlot s : SLOTS) {
            ItemStack stack = villager.getEquippedStack(s);
            if (!stack.isEmpty()) {
                villager.dropStack(stack.copy());
                villager.equipStack(s, ItemStack.EMPTY);
            }
        }
    }
}
