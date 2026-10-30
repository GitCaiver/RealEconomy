package com.realeconomy.auction;

import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.finance.FinanceManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.townhall.LicenseManager;
import com.realeconomy.util.TimeUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/** Lógica de subastas: colocar, pujar, cancelar, cerrar (tick) y cobrar offline. */
public final class AuctionService {

    private static long lastTick = 0L;

    private AuctionService() {
    }

    private static ServerWorld overworld(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD);
    }

    private static String currencyName(MinecraftServer server, String id) {
        Currency c = CurrencyManager.get(server).getById(id);
        return c != null ? c.getName() : id;
    }

    private static void msg(ServerPlayerEntity p, String text, Formatting color) {
        p.sendMessage(Text.literal(text).formatted(color), false);
    }

    private static void notify(MinecraftServer server, UUID uuid, String text, Formatting color) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        if (p != null) {
            p.sendMessage(Text.literal(text).formatted(color), false);
        }
    }

    /** Nombre corto que flota sobre el pedestal. */
    public static Text displayName(MinecraftServer server, Auction a) {
        long left = a.getEndTime() - System.currentTimeMillis();
        String bid = a.hasBids() ? (a.getCurrentBid() + " " + currencyName(server, a.getCurrencyId()))
                : ("sin pujas (desde " + a.getStartPrice() + " " + currencyName(server, a.getCurrencyId()) + ")");
        return Text.literal(a.getItem().getName().getString() + " · " + bid + " · " + TimeUtil.format(Math.max(0, left)))
                .formatted(Formatting.GOLD);
    }

    /** Mensaje al hacer clic derecho en un pedestal (subasta o pista para colocar). */
    public static void rightClick(MinecraftServer server, ServerPlayerEntity player, BlockPos pos) {
        AuctionManager mgr = AuctionManager.get(server);
        Auction a = mgr.auctionAt(pos.asLong());
        if (a == null) {
            msg(player, "Pedestal de subasta libre. Coloca un item con /subasta crear <precio> <moneda> <duracion>.", Formatting.GRAY);
            return;
        }
        String cur = currencyName(server, a.getCurrencyId());
        long left = Math.max(0, a.getEndTime() - System.currentTimeMillis());
        String bid = a.hasBids() ? (a.getCurrentBid() + " " + cur + " (" + a.getBidderName() + ")")
                : ("sin pujas, desde " + a.getStartPrice() + " " + cur);
        msg(player, "Subasta #" + a.getId() + ": " + a.getItem().getName().getString() + " x" + a.getItem().getCount()
                + " · " + bid + " · faltan " + TimeUtil.format(left)
                + ". Puja con /subasta pujar <cantidad> · detalles con /subasta info.", Formatting.GOLD);
    }

    // ---- Colocar ----

    public static void place(MinecraftServer server, ServerPlayerEntity player,
                             long startPrice, String currencyId, long durationMillis) {
        AuctionManager mgr = AuctionManager.get(server);
        long ped = mgr.nearestFreePedestal(player.getPos(), ModConfig.get().auctionRange);
        if (ped == -1L) {
            msg(player, "No hay ningún pedestal de subasta libre cerca.", Formatting.RED);
            return;
        }
        ItemStack hand = player.getMainHandStack();
        if (hand.isEmpty()) {
            msg(player, "Sostén en la mano el item que quieres subastar.", Formatting.RED);
            return;
        }
        long minDur = TimeUtil.parsePeriod(ModConfig.get().auctionMinDuration);
        if (minDur <= 0) {
            minDur = 30_000L;
        }
        if (durationMillis < minDur) {
            msg(player, "La duración mínima de una subasta es " + TimeUtil.format(minDur) + ".", Formatting.RED);
            return;
        }
        if (!LicenseManager.get(server).consumeAuction(player.getUuid())) {
            msg(player, "Necesitas una licencia de subasta. Cómprala en el ayuntamiento (/subasta licencia).", Formatting.RED);
            return;
        }
        ItemStack sold = hand.copy();
        hand.setCount(0);
        BlockPos pos = BlockPos.fromLong(ped);
        long end = System.currentTimeMillis() + durationMillis;
        Auction a = mgr.create(ped, player.getUuid(), player.getNameForScoreboard(), sold, startPrice, currencyId, end);
        UUID display = AuctionDisplay.spawn(overworld(server), pos, sold, displayName(server, a));
        a.setDisplayUuid(display);
        mgr.markDirty();
        msg(player, "Subasta #" + a.getId() + " creada: " + sold.getCount() + "x " + sold.getName().getString()
                + " desde " + startPrice + " " + currencyName(server, currencyId)
                + " · dura " + TimeUtil.format(durationMillis) + ".", Formatting.GREEN);
    }

    // ---- Pujar ----

    public static void bid(MinecraftServer server, ServerPlayerEntity player, long amount) {
        AuctionManager mgr = AuctionManager.get(server);
        long ped = mgr.nearestPedestal(player.getPos(), ModConfig.get().auctionRange);
        Auction a = ped == -1L ? null : mgr.auctionAt(ped);
        if (a == null) {
            msg(player, "No hay ninguna subasta activa cerca.", Formatting.RED);
            return;
        }
        if (a.getSeller().equals(player.getUuid())) {
            msg(player, "No puedes pujar en tu propia subasta.", Formatting.RED);
            return;
        }
        if (amount < a.minNextBid()) {
            msg(player, "La puja debe ser de al menos " + a.minNextBid() + " "
                    + currencyName(server, a.getCurrencyId()) + ".", Formatting.RED);
            return;
        }
        BalanceManager bm = BalanceManager.get(server);
        if (!bm.subtract(player.getUuid(), a.getCurrencyId(), amount)) {
            msg(player, "No tienes saldo suficiente (" + amount + " " + currencyName(server, a.getCurrencyId()) + ").", Formatting.RED);
            return;
        }
        // Devuelve la puja anterior al pujador anterior.
        if (a.hasBids()) {
            bm.add(a.getCurrentBidder(), a.getCurrencyId(), a.getCurrentBid());
            notify(server, a.getCurrentBidder(), "Te han superado en la subasta #" + a.getId()
                    + ". Se te devuelven " + a.getCurrentBid() + " " + currencyName(server, a.getCurrencyId()) + ".", Formatting.YELLOW);
        }
        a.setBid(amount, player.getUuid(), player.getNameForScoreboard());
        AuctionDisplay.update(overworld(server), a.getDisplayUuid(), displayName(server, a));
        mgr.markDirty();
        msg(player, "Pujaste " + amount + " " + currencyName(server, a.getCurrencyId()) + " en la subasta #" + a.getId() + ".", Formatting.GREEN);
    }

    // ---- Cancelar (vendedor, sin pujas) ----

    public static void cancel(MinecraftServer server, ServerPlayerEntity player) {
        AuctionManager mgr = AuctionManager.get(server);
        long ped = mgr.nearestPedestal(player.getPos(), ModConfig.get().auctionRange);
        Auction a = ped == -1L ? null : mgr.auctionAt(ped);
        if (a == null || !a.getSeller().equals(player.getUuid())) {
            msg(player, "Acércate a una subasta tuya.", Formatting.RED);
            return;
        }
        if (a.hasBids()) {
            msg(player, "No puedes cancelar: ya tiene pujas.", Formatting.RED);
            return;
        }
        AuctionDisplay.remove(overworld(server), a.getDisplayUuid());
        deliver(server, a.getSeller(), a.getItem());
        mgr.remove(a);
        msg(player, "Subasta #" + a.getId() + " cancelada; se te devuelve el item.", Formatting.YELLOW);
    }

    // ---- Cierre por tiempo (tick) ----

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastTick < 1000L) {
            return;
        }
        lastTick = now;
        AuctionManager mgr = AuctionManager.get(server);
        if (mgr.all().isEmpty()) {
            return;
        }
        ServerWorld world = overworld(server);
        List<Auction> expired = new java.util.ArrayList<>();
        for (Auction a : mgr.all()) {
            if (a.isExpired(now)) {
                expired.add(a);
            } else {
                AuctionDisplay.update(world, a.getDisplayUuid(), displayName(server, a));
            }
        }
        for (Auction a : expired) {
            settle(server, a);
        }
    }

    private static void settle(MinecraftServer server, Auction a) {
        AuctionManager mgr = AuctionManager.get(server);
        AuctionDisplay.remove(overworld(server), a.getDisplayUuid());
        BalanceManager bm = BalanceManager.get(server);
        if (a.hasBids()) {
            long bid = a.getCurrentBid();
            long commission = bid * Math.max(0, ModConfig.get().auctionCommissionPercent) / 100L;
            long toSeller = bid - commission;
            bm.add(a.getSeller(), a.getCurrencyId(), toSeller);
            if (commission > 0) {
                Group sellerCountry = GroupManager.get(server).getByPlayer(a.getSeller());
                if (sellerCountry != null) {
                    bm.add(FinanceManager.accountUuid(sellerCountry), a.getCurrencyId(), commission);
                } else {
                    bm.add(a.getSeller(), a.getCurrencyId(), commission); // sin país: todo al vendedor
                }
            }
            deliver(server, a.getCurrentBidder(), a.getItem());
            notify(server, a.getCurrentBidder(), "Ganaste la subasta #" + a.getId() + " ("
                    + a.getItem().getName().getString() + ") por " + bid + " " + currencyName(server, a.getCurrencyId()) + ".", Formatting.GREEN);
            notify(server, a.getSeller(), "Tu subasta #" + a.getId() + " se vendió por " + bid + " "
                    + currencyName(server, a.getCurrencyId()) + " (recibes " + toSeller + ", comisión " + commission + ").", Formatting.GREEN);
        } else {
            deliver(server, a.getSeller(), a.getItem());
            notify(server, a.getSeller(), "Tu subasta #" + a.getId() + " terminó sin pujas; se te devuelve el item.", Formatting.YELLOW);
        }
        mgr.remove(a);
    }

    /** Elimina una subasta devolviendo puja e item (para /subastaborrar o disolución). */
    public static void forceRemove(MinecraftServer server, Auction a) {
        AuctionManager mgr = AuctionManager.get(server);
        AuctionDisplay.remove(overworld(server), a.getDisplayUuid());
        if (a.hasBids()) {
            BalanceManager.get(server).add(a.getCurrentBidder(), a.getCurrencyId(), a.getCurrentBid());
            notify(server, a.getCurrentBidder(), "La subasta #" + a.getId() + " se anuló; se te devuelve la puja.", Formatting.YELLOW);
        }
        deliver(server, a.getSeller(), a.getItem());
        mgr.remove(a);
    }

    // ---- Entrega (online a inventario, offline a pendientes) ----

    public static void deliver(MinecraftServer server, UUID uuid, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        if (p != null) {
            p.getInventory().offerOrDrop(stack.copy());
        } else {
            AuctionManager.get(server).addPending(uuid, stack.copy());
        }
    }

    /** Entrega lo pendiente a un jugador (al conectarse o con /subasta recoger). */
    public static int collect(MinecraftServer server, ServerPlayerEntity player) {
        List<ItemStack> list = AuctionManager.get(server).takePending(player.getUuid());
        for (ItemStack s : list) {
            player.getInventory().offerOrDrop(s);
        }
        return list.size();
    }
}
