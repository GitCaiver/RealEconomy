package com.realeconomy;

import com.realeconomy.bank.BankActions;
import com.realeconomy.bank.BankEntities;
import com.realeconomy.buyer.BuyerActions;
import com.realeconomy.buyer.BuyerEntities;
import com.realeconomy.command.BankCommands;
import com.realeconomy.command.BuyerCommands;
import com.realeconomy.command.CurrencyCommands;
import com.realeconomy.command.EcoAdminCommands;
import com.realeconomy.command.GovernmentCommands;
import com.realeconomy.command.GroupCommands;
import com.realeconomy.command.JobCommands;
import com.realeconomy.command.LoanCommands;
import com.realeconomy.command.MarketCommands;
import com.realeconomy.command.PartyCommands;
import com.realeconomy.command.PublicWorksCommands;
import com.realeconomy.command.ShopCommands;
import com.realeconomy.command.TaxCommands;
import com.realeconomy.command.TownhallCommands;
import com.realeconomy.command.TreasuryCommands;
import com.realeconomy.config.ModConfig;
import com.realeconomy.finance.FinanceManager;
import com.realeconomy.market.MarketActions;
import com.realeconomy.market.MarketEntities;
import com.realeconomy.shop.ShopActions;
import com.realeconomy.shop.ShopEntities;
import com.realeconomy.townhall.TownhallActions;
import com.realeconomy.townhall.TownhallEntities;
import com.realeconomy.group.GroupManager;
import com.realeconomy.item.ModItems;
import com.realeconomy.jobs.JobEvents;
import com.realeconomy.network.ModNetworking;
import net.fabricmc.api.ModInitializer;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.group.Group;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RealEconomy implements ModInitializer {

    public static final String MOD_ID = "realeconomy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModConfig.load();
        ModItems.register();
        ModNetworking.registerPayloadTypes();
        ModNetworking.registerServerReceivers();

        // Registro de comandos.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            GroupCommands.register(dispatcher);
            CurrencyCommands.register(dispatcher);
            EcoAdminCommands.register(dispatcher);
            BankCommands.register(dispatcher);
            TownhallCommands.register(dispatcher);
            ShopCommands.register(dispatcher);
            TaxCommands.register(dispatcher);
            TreasuryCommands.register(dispatcher);
            MarketCommands.register(dispatcher);
            JobCommands.register(dispatcher);
            LoanCommands.register(dispatcher);
            PartyCommands.register(dispatcher);
            GovernmentCommands.register(dispatcher);
            PublicWorksCommands.register(dispatcher);
            BuyerCommands.register(dispatcher);
        });

        // Al arrancar: garantiza que cada país tenga su moneda (migra países antiguos sin divisa).
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            GroupManager gm = GroupManager.get(server);
            CurrencyManager cm = CurrencyManager.get(server);
            for (Group g : gm.getGroups()) {
                if (!cm.groupHasCurrency(g.getId())) {
                    cm.createForGroup(g, g.getName(), null);
                }
            }
        });

        // Procesa impuestos y sueldos en tiempo real (la propia clase se autolimita).
        ServerTickEvents.END_SERVER_TICK.register(server -> FinanceManager.get(server).tick(server));

        // Trabajos: minería/tala/agricultura al romper bloques (la banca se cobra en BankActions).
        JobEvents.register();

        // Clic derecho en los NPC del banco / ayuntamiento -> abre su GUI.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }
            if (entity instanceof VillagerEntity villager && player instanceof ServerPlayerEntity serverPlayer) {
                if (BankEntities.isBank(villager)) {
                    BankActions.openFor(serverPlayer);
                    return ActionResult.SUCCESS;
                }
                if (TownhallEntities.isTownhall(villager)) {
                    TownhallActions.openFor(serverPlayer);
                    return ActionResult.SUCCESS;
                }
                if (ShopEntities.isShop(villager)) {
                    ShopActions.openForEntity(serverPlayer, villager);
                    return ActionResult.SUCCESS;
                }
                if (MarketEntities.isMarket(villager)) {
                    MarketActions.openForEntity(serverPlayer, villager);
                    return ActionResult.SUCCESS;
                }
                if (BuyerEntities.isBuyer(villager)) {
                    BuyerActions.openForEntity(serverPlayer, villager);
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });

        // Al conectarse un jugador: sincroniza su equipo de grupo y recibe las monedas.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            GroupManager.get(server).onPlayerJoin(handler.player);
            ModNetworking.sendAllTo(server, handler.player);
        });

        LOGGER.info("RealEconomy inicializado (países/partidos, monedas, banco, ayuntamiento, tiendas, impuestos/tesorería, mercados, trabajos, préstamos, política monetaria/obras públicas, compradores).");
    }
}
