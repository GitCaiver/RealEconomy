package com.realeconomy;

import com.realeconomy.auction.AuctionService;
import com.realeconomy.bank.BankActions;
import com.realeconomy.bank.BankEntities;
import com.realeconomy.buyer.BuyerActions;
import com.realeconomy.buyer.BuyerEntities;
import com.realeconomy.command.AuctionCommands;
import com.realeconomy.command.BankCommands;
import com.realeconomy.command.BuyerCommands;
import com.realeconomy.command.CurrencyCommands;
import com.realeconomy.command.EcoAdminCommands;
import com.realeconomy.command.GovernmentCommands;
import com.realeconomy.command.GroupCommands;
import com.realeconomy.command.JobCommands;
import com.realeconomy.command.LicenseCommands;
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
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
        com.realeconomy.politics.PoliticalForms.load();
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
            AuctionCommands.register(dispatcher);
            LicenseCommands.register(dispatcher);
            com.realeconomy.command.ElectionCommands.register(dispatcher);
            com.realeconomy.command.TerritoryCommands.register(dispatcher);
            com.realeconomy.command.ProvinceCommands.register(dispatcher);
            com.realeconomy.command.WarCommands.register(dispatcher);
            com.realeconomy.command.EmploymentCommands.register(dispatcher);
            com.realeconomy.command.OwnerCommands.register(dispatcher);
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

        // Cierra subastas vencidas y refresca sus displays (autolimitado a 1/seg).
        ServerTickEvents.END_SERVER_TICK.register(AuctionService::tick);

        // Abre/cierra elecciones según el día configurado (autolimitado a 1/seg).
        ServerTickEvents.END_SERVER_TICK.register(server ->
                com.realeconomy.election.ElectionManager.get(server).tick(server));

        // Cierra guerras cuyo plazo venció (autolimitado a 1/seg).
        ServerTickEvents.END_SERVER_TICK.register(server ->
                com.realeconomy.war.WarManager.get(server).tick(server));

        // Paga los sueldos de los aldeanos de MCA empleados (autolimitado a 1/seg).
        ServerTickEvents.END_SERVER_TICK.register(server ->
                com.realeconomy.employment.EmploymentManager.get(server).tick(server));

        // Trabajo físico de los aldeanos de MCA (minar/talar/cosechar/guardia); por lotes y solo cerca de jugadores.
        ServerTickEvents.END_SERVER_TICK.register(com.realeconomy.employment.VillagerWorker::tick);

        // Combate según el modo de actitud de los aldeanos de MCA (agresivo/neutral); por lotes y cerca de jugadores.
        ServerTickEvents.END_SERVER_TICK.register(com.realeconomy.attitude.AttitudeCombat::tick);

        // Muerte de un jugador en una zona de guerra -> queda eliminado de esa guerra.
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity dead && dead.getServer() != null) {
                com.realeconomy.war.WarManager.get(dead.getServer()).onPlayerDeath(dead.getServer(), dead);
                return;
            }
            // Muerte de un aldeano de MCA: suelta su equipo (si la config lo permite) y limpia sus registros.
            if (com.realeconomy.compat.mca.Mca.isMcaVillager(entity) && entity.getServer() != null) {
                var server = entity.getServer();
                if (ModConfig.get().villagerDropsEquipmentOnDeath) {
                    com.realeconomy.attitude.OwnerActions.dropEquipment(entity);
                }
                com.realeconomy.attitude.AttitudeManager.get(server).remove(entity.getUuid());
                com.realeconomy.employment.EmploymentManager.get(server).remove(entity.getUuid());
                com.realeconomy.employment.LibrarianManager.get(server).remove(entity.getUuid());
                com.realeconomy.war.WarManager.get(server).onVillagerDeath(entity.getUuid());
            }
        });

        // Trabajos: minería/tala/agricultura al romper bloques (la banca se cobra en BankActions).
        JobEvents.register();

        // Protección opcional del terreno en zonas de guerra (warBreakStructures=false).
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!world.isClient && world instanceof net.minecraft.server.world.ServerWorld sw
                    && com.realeconomy.war.WarProtection.isProtected(sw.getServer(), sw, pos)) {
                if (player instanceof ServerPlayerEntity sp) {
                    sp.sendMessage(net.minecraft.text.Text.literal(
                            "Zona de guerra protegida: aquí no se puede romper el terreno.").formatted(
                            net.minecraft.util.Formatting.RED), true);
                }
                return false;
            }
            return true;
        });

        // Clic derecho en los NPC del banco / ayuntamiento -> abre su GUI.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }
            if (entity instanceof VillagerEntity villager && player instanceof ServerPlayerEntity serverPlayer) {
                try {
                    // Dueño (familia) de un aldeano de MCA con equipo en la mano -> se lo equipa.
                    if (com.realeconomy.compat.mca.Mca.isMcaVillager(villager)
                            && com.realeconomy.attitude.OwnerActions.isGear(serverPlayer.getMainHandStack())
                            && com.realeconomy.compat.mca.Mca.isOwner(villager, serverPlayer.getUuid())) {
                        com.realeconomy.attitude.OwnerActions.equipHeld(serverPlayer, villager);
                        return ActionResult.SUCCESS;
                    }
                    // Aldeano de MCA + agachado -> GUI de empleo de RealEconomy (clic normal = GUI propia de MCA).
                    if (serverPlayer.isSneaking() && com.realeconomy.compat.mca.Mca.isMcaVillager(villager)) {
                        com.realeconomy.employment.EmploymentActions.openFor(serverPlayer, villager);
                        return ActionResult.SUCCESS;
                    }
                    // Clic normal sobre un librero de MCA empleado -> tienda de libros encantados.
                    if (com.realeconomy.employment.LibrarianActions.isLibrarian(serverPlayer.getServer(), villager)) {
                        com.realeconomy.employment.LibrarianActions.openFor(serverPlayer, villager);
                        return ActionResult.SUCCESS;
                    }
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
                    if (com.realeconomy.election.UrnEntities.isUrn(villager)) {
                        com.realeconomy.election.ElectionActions.openFor(serverPlayer);
                        return ActionResult.SUCCESS;
                    }
                } catch (Exception e) {
                    // Si algo falla al abrir la GUI, lo registramos en vez de tragarlo en silencio.
                    LOGGER.error("Error al abrir la interfaz del NPC de RealEconomy", e);
                    serverPlayer.sendMessage(net.minecraft.text.Text.literal(
                            "Error al abrir la interfaz (revisa el log del servidor)."), false);
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });

        // Clic derecho en un pedestal de subasta: muestra la info (o la pista para colocar).
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }
            if (player instanceof ServerPlayerEntity serverPlayer
                    && com.realeconomy.auction.AuctionManager.get(serverPlayer.getServer()).isPedestal(hitResult.getBlockPos())) {
                AuctionService.rightClick(serverPlayer.getServer(), serverPlayer, hitResult.getBlockPos());
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        // Al conectarse un jugador: sincroniza su equipo de grupo y recibe las monedas.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            GroupManager.get(server).onPlayerJoin(handler.player);
            ModNetworking.sendAllTo(server, handler.player);
            // Entrega lo pendiente de subastas (ganado o devuelto estando desconectado).
            int collected = AuctionService.collect(server, handler.player);
            if (collected > 0) {
                handler.player.sendMessage(net.minecraft.text.Text.literal(
                        "Recogiste " + collected + " objeto(s) de subastas."), false);
            }
        });

        LOGGER.info("RealEconomy inicializado (países/partidos, monedas, banco, ayuntamiento, tiendas, impuestos/tesorería, mercados, trabajos, préstamos, política monetaria/obras públicas, compradores, subastas).");
    }
}
