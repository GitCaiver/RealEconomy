package com.realeconomy.employment;

import com.realeconomy.compat.mca.Mca;
import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.license.CustomLicenseManager;
import com.realeconomy.license.LicensePermission;
import com.realeconomy.network.EmploymentActionPayload;
import com.realeconomy.network.OpenEmploymentPayload;
import com.realeconomy.politics.Governance;
import com.realeconomy.util.TimeUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * Lógica de servidor del empleo de aldeanos de MCA: resuelve quién puede emplear
 * (gobierno = empleo público pagado por el país; o un miembro con licencia = empleo
 * privado pagado por él), abre/actualiza la GUI y procesa contratar/despedir.
 */
public final class EmploymentActions {

    private EmploymentActions() {
    }

    /** Modo de empleo del jugador según su país y permisos. */
    public static int employerMode(MinecraftServer server, ServerPlayerEntity player) {
        Group g = GroupManager.get(server).getByPlayer(player.getUuid());
        if (g == null || CurrencyManager.get(server).getByGroup(g.getId()) == null) {
            return OpenEmploymentPayload.MODE_NONE;
        }
        if (Governance.isGovernment(server, g, player.getUuid())) {
            return OpenEmploymentPayload.MODE_PUBLIC;
        }
        // Empleo privado: permitido si el país no exige licencia de "trabajo" o el jugador la tiene.
        if (CustomLicenseManager.get(server).permits(g.getId(), player.getUuid(), LicensePermission.TRABAJO)) {
            return OpenEmploymentPayload.MODE_PRIVATE;
        }
        return OpenEmploymentPayload.MODE_NONE;
    }

    /** Abre la GUI de empleo para el aldeano de MCA indicado. */
    public static void openFor(ServerPlayerEntity player, Entity villager) {
        MinecraftServer server = player.getServer();
        if (server == null || !Mca.isMcaVillager(villager)) {
            return;
        }
        ServerPlayNetworking.send(player, buildPayload(server, player, villager.getUuid(),
                villager.getName().getString()));
    }

    private static OpenEmploymentPayload buildPayload(MinecraftServer server, ServerPlayerEntity player,
                                                      UUID villagerId, String name) {
        int mode = employerMode(server, player);
        Group g = GroupManager.get(server).getByPlayer(player.getUuid());
        Currency currency = g == null ? null : CurrencyManager.get(server).getByGroup(g.getId());
        String currencyName = currency == null ? "moneda" : currency.getName();

        EmployedVillager ev = EmploymentManager.get(server).get(villagerId);
        if (ev != null) {
            // Solo el empleador actual o el gobierno de su país pueden gestionarlo.
            boolean canManage = ev.employer().equals(player.getUuid())
                    || (g != null && g.getId().equals(ev.countryId())
                        && Governance.isGovernment(server, g, player.getUuid()));
            return new OpenEmploymentPayload(villagerId, name, true, ev.job().id(), ev.wage(),
                    TimeUtil.format(ev.periodMs()), ev.wallet(), ev.active(),
                    canManage ? mode : OpenEmploymentPayload.MODE_NONE, currencyName, ev.hasZone(), ev.chest() != null);
        }
        return new OpenEmploymentPayload(villagerId, name, false, VillagerJob.MINERO.id(), 10L,
                ModConfig.get().villagerWageDefaultPeriod, 0L, true, mode, currencyName, false, false);
    }

    /** Payload de ejemplo para /ecoadmin debug empleo (sin aldeano real). */
    public static void debugOpen(ServerPlayerEntity player) {
        int mode = employerMode(player.getServer(), player);
        if (mode == OpenEmploymentPayload.MODE_NONE) {
            mode = OpenEmploymentPayload.MODE_PUBLIC;
        }
        ServerPlayNetworking.send(player, new OpenEmploymentPayload(player.getUuid(),
                "Aldeano de ejemplo", false, VillagerJob.MINERO.id(), 10L, "1h", 0L, true, mode, "demo", false, false));
    }

    public static void handle(ServerPlayerEntity player, EmploymentActionPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Entity entity = player.getServerWorld().getEntity(payload.villager());
        if (entity == null || !Mca.isMcaVillager(entity)) {
            player.sendMessage(Text.literal("Ese aldeano ya no está disponible.").formatted(Formatting.RED), false);
            return;
        }
        EmploymentManager mgr = EmploymentManager.get(server);
        EmployedVillager existing = mgr.get(payload.villager());

        if (payload.action() == EmploymentActionPayload.FIRE) {
            if (existing == null) {
                return;
            }
            if (!canManage(server, player, existing)) {
                player.sendMessage(Text.literal("No puedes gestionar a este aldeano.").formatted(Formatting.RED), false);
                return;
            }
            mgr.remove(payload.villager());
            LibrarianManager.get(server).remove(payload.villager());
            player.sendMessage(Text.literal("Has despedido al aldeano.").formatted(Formatting.YELLOW), false);
            openFor(player, entity);
            return;
        }

        // HIRE / UPDATE
        int mode = employerMode(server, player);
        if (mode == OpenEmploymentPayload.MODE_NONE) {
            player.sendMessage(Text.literal("Necesitas ser gobierno de tu país o tener licencia de trabajo para emplear.")
                    .formatted(Formatting.RED), false);
            return;
        }
        if (existing != null && !canManage(server, player, existing)) {
            player.sendMessage(Text.literal("Otro empleador gestiona a este aldeano.").formatted(Formatting.RED), false);
            return;
        }
        Group g = GroupManager.get(server).getByPlayer(player.getUuid());
        if (g == null) {
            return;
        }
        VillagerJob job = VillagerJob.byId(payload.jobId());
        if (job == null) {
            player.sendMessage(Text.literal("Trabajo no válido.").formatted(Formatting.RED), false);
            return;
        }
        long wage = Math.max(0, payload.wage());
        long period = TimeUtil.parsePeriod(payload.period());
        if (period < 0) {
            period = TimeUtil.parsePeriod(ModConfig.get().villagerWageDefaultPeriod);
        }

        if (existing == null) {
            // Límites configurables.
            int perPlayer = ModConfig.get().maxEmployeesPerPlayer;
            if (perPlayer >= 0 && mgr.byEmployer(player.getUuid()).size() >= perPlayer) {
                player.sendMessage(Text.literal("Has alcanzado tu límite de aldeanos empleados (" + perPlayer + ").")
                        .formatted(Formatting.RED), false);
                return;
            }
            int perCountry = ModConfig.get().maxEmployeesPerCountry;
            if (mode == OpenEmploymentPayload.MODE_PUBLIC && perCountry >= 0) {
                long count = mgr.all().stream().filter(e -> e.publicHire() && e.countryId().equals(g.getId())).count();
                if (count >= perCountry) {
                    player.sendMessage(Text.literal("Tu país alcanzó el límite de empleados públicos (" + perCountry + ").")
                            .formatted(Formatting.RED), false);
                    return;
                }
            }
            EmployedVillager ev = new EmployedVillager(payload.villager(), player.getUuid(), g.getId(),
                    mode == OpenEmploymentPayload.MODE_PUBLIC, job, wage, period);
            mgr.put(ev);
            player.sendMessage(Text.literal("Has empleado al aldeano como " + job.display() + " por " + wage + " "
                    + currencyName(server, g) + " cada " + TimeUtil.format(period)
                    + (ev.publicHire() ? " (paga el país)." : " (pagas tú).")).formatted(Formatting.GREEN), false);
        } else {
            existing.setJob(job);
            existing.setWage(wage);
            existing.setPeriodMs(period);
            mgr.markDirty();
            player.sendMessage(Text.literal("Empleo actualizado: " + job.display() + ", " + wage + " cada "
                    + TimeUtil.format(period) + ".").formatted(Formatting.GREEN), false);
        }
        openFor(player, entity);
    }

    private static boolean canManage(MinecraftServer server, ServerPlayerEntity player, EmployedVillager ev) {
        if (ev.employer().equals(player.getUuid())) {
            return true;
        }
        Group g = GroupManager.get(server).getById(ev.countryId());
        return g != null && Governance.isGovernment(server, g, player.getUuid());
    }

    private static String currencyName(MinecraftServer server, Group g) {
        Currency c = CurrencyManager.get(server).getByGroup(g.getId());
        return c == null ? "moneda" : c.getName();
    }
}
