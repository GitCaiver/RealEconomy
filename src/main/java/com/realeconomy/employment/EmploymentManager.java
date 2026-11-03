package com.realeconomy.employment;

import com.realeconomy.RealEconomy;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.finance.FinanceManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registro de aldeanos de MCA empleados y pago periódico de sueldos desde la cuenta del
 * empleador (cuenta del país si es empleo público, o saldo del empleador si es privado).
 * Si el empleador no tiene saldo, el aldeano queda inactivo hasta que vuelva a haberlo.
 */
public class EmploymentManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_employment";

    public static final PersistentState.Type<EmploymentManager> TYPE = new PersistentState.Type<>(
            EmploymentManager::new,
            EmploymentManager::fromNbt,
            null
    );

    private final Map<UUID, EmployedVillager> byVillager = new HashMap<>();
    private long lastTick;

    public EmploymentManager() {
    }

    public static EmploymentManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    public EmployedVillager get(UUID villager) {
        return byVillager.get(villager);
    }

    public boolean isEmployed(UUID villager) {
        return byVillager.containsKey(villager);
    }

    public void put(EmployedVillager ev) {
        byVillager.put(ev.villager(), ev);
        markDirty();
    }

    public void remove(UUID villager) {
        if (byVillager.remove(villager) != null) {
            markDirty();
        }
    }

    public List<EmployedVillager> byEmployer(UUID employer) {
        List<EmployedVillager> list = new ArrayList<>();
        for (EmployedVillager ev : byVillager.values()) {
            if (ev.employer().equals(employer)) {
                list.add(ev);
            }
        }
        return list;
    }

    public List<EmployedVillager> all() {
        return new ArrayList<>(byVillager.values());
    }

    /** Al disolver un país: termina los empleos públicos de ese país (deja de pagarlos). */
    public void removeCountry(String countryId) {
        if (byVillager.values().removeIf(ev -> ev.countryId().equals(countryId) && ev.publicHire())) {
            markDirty();
        }
    }

    // ---- Pago de sueldos ----

    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastTick < 1000L) {
            return;
        }
        lastTick = now;
        if (byVillager.isEmpty()) {
            return;
        }
        BalanceManager balances = BalanceManager.get(server);
        GroupManager gm = GroupManager.get(server);
        CurrencyManager cm = CurrencyManager.get(server);
        boolean dirty = false;

        for (EmployedVillager ev : byVillager.values()) {
            if (ev.wage() <= 0 || ev.periodMs() <= 0) {
                continue;
            }
            if (now - ev.lastPaid() < ev.periodMs()) {
                continue;
            }
            Group country = gm.getById(ev.countryId());
            Currency currency = country == null ? null : cm.getByGroup(country.getId());
            if (country == null || currency == null) {
                continue; // país o moneda ya no existen
            }
            UUID payer = ev.publicHire() ? FinanceManager.accountUuid(country) : ev.employer();
            boolean paid = balances.subtract(payer, currency.getId(), ev.wage());
            if (paid) {
                ev.addWallet(ev.wage());
                ev.setLastPaid(now);
                if (!ev.active()) {
                    ev.setActive(true);
                    notifyEmployer(server, ev, Text.literal("Tu aldeano vuelve a cobrar y a trabajar.")
                            .formatted(Formatting.GREEN));
                }
                dirty = true;
            } else if (ev.active()) {
                ev.setActive(false);
                notifyEmployer(server, ev, Text.literal("Sin saldo para pagar a tu aldeano ("
                        + ev.job().display() + "): deja de trabajar hasta que haya.").formatted(Formatting.RED));
                dirty = true;
            }
        }
        if (dirty) {
            markDirty();
        }
    }

    private void notifyEmployer(MinecraftServer server, EmployedVillager ev, Text message) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(ev.employer());
        if (p != null) {
            p.sendMessage(message, true);
        }
    }

    // ---- NBT ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (EmployedVillager ev : byVillager.values()) {
            list.add(ev.toNbt());
        }
        nbt.put("employed", list);
        return nbt;
    }

    private static EmploymentManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        EmploymentManager mgr = new EmploymentManager();
        NbtList list = nbt.getList("employed", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            EmployedVillager ev = EmployedVillager.fromNbt(list.getCompound(i));
            mgr.byVillager.put(ev.villager(), ev);
        }
        return mgr;
    }
}
