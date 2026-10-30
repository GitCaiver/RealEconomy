package com.realeconomy.economy;

import com.realeconomy.RealEconomy;
import com.realeconomy.config.ModConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Política monetaria por moneda (parte 3, 2ª tanda): dinero en circulación,
 * valor frente a la esmeralda y registro de emisiones (imprimir/quemar).
 *
 * <p>La circulación es el dinero <em>emitido</em> por el país (impreso menos
 * quemado). El valor de una moneda baja cuanto más dinero hay en circulación,
 * según una fórmula configurable: {@code valor = base * baseCirc / (baseCirc + circulación)}.
 */
public class MonetaryManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_monetary";

    public static final PersistentState.Type<MonetaryManager> TYPE = new PersistentState.Type<>(
            MonetaryManager::new,
            MonetaryManager::fromNbt,
            null
    );

    /** Una emisión registrada: imprimir (print=true) o quemar/retirar (print=false). */
    public static final class MoneyLog {
        public final long time;
        public final long amount;
        public final boolean print;
        public final String actor;
        public final long after;

        public MoneyLog(long time, long amount, boolean print, String actor, long after) {
            this.time = time;
            this.amount = amount;
            this.print = print;
            this.actor = actor == null ? "" : actor;
            this.after = after;
        }

        NbtCompound toNbt() {
            NbtCompound c = new NbtCompound();
            c.putLong("time", time);
            c.putLong("amount", amount);
            c.putBoolean("print", print);
            c.putString("actor", actor);
            c.putLong("after", after);
            return c;
        }

        static MoneyLog fromNbt(NbtCompound c) {
            return new MoneyLog(c.getLong("time"), c.getLong("amount"), c.getBoolean("print"),
                    c.getString("actor"), c.getLong("after"));
        }
    }

    private static final class Data {
        long circulation;
        final List<MoneyLog> logs = new ArrayList<>();
    }

    private final Map<String, Data> byCurrency = new HashMap<>();

    public static MonetaryManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    private Data data(String currencyId) {
        return byCurrency.computeIfAbsent(currencyId, k -> new Data());
    }

    // ---- Consultas ----

    public long circulation(String currencyId) {
        Data d = byCurrency.get(currencyId);
        return d == null ? 0L : d.circulation;
    }

    /** Valor actual (esmeraldas por moneda) según la circulación y la config. */
    public double value(String currencyId) {
        return valueForCirculation(circulation(currencyId));
    }

    /** Valor (esmeraldas por moneda) que tendría la moneda con la circulación dada. */
    public static double valueForCirculation(long circulation) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.devaluationEnabled) {
            return cfg.currencyBaseValue;
        }
        long circ = Math.max(0L, circulation);
        long base = Math.max(1L, cfg.currencyBaseCirculation);
        double v = cfg.currencyBaseValue * base / (double) (base + circ);
        return Math.max(cfg.currencyMinValue, Math.min(cfg.currencyMaxValue, v));
    }

    /** Monedas equivalentes a un precio en esmeraldas, al valor actual. */
    public long coinsForEmeralds(String currencyId, double emeralds) {
        double v = value(currencyId);
        if (v <= 0) {
            return 0L;
        }
        return Math.max(0L, Math.round(emeralds / v));
    }

    /** Valor en esmeraldas de una cantidad de monedas, al valor actual. */
    public double emeraldsForCoins(String currencyId, long coins) {
        return coins * value(currencyId);
    }

    public List<MoneyLog> logs(String currencyId) {
        Data d = byCurrency.get(currencyId);
        return d == null ? List.of() : d.logs;
    }

    // ---- Operaciones ----

    /** Imprime (crea) dinero: aumenta la circulación y registra la emisión. */
    public void print(String currencyId, long amount, String actor) {
        if (amount <= 0) {
            return;
        }
        Data d = data(currencyId);
        d.circulation += amount;
        addLog(d, new MoneyLog(System.currentTimeMillis(), amount, true, actor, d.circulation));
        markDirty();
    }

    /** Quema (retira) dinero de la circulación; no baja de cero. Registra la operación. */
    public void burn(String currencyId, long amount, String actor) {
        if (amount <= 0) {
            return;
        }
        Data d = data(currencyId);
        d.circulation -= Math.min(amount, d.circulation);
        addLog(d, new MoneyLog(System.currentTimeMillis(), amount, false, actor, d.circulation));
        markDirty();
    }

    private static void addLog(Data d, MoneyLog log) {
        d.logs.add(log);
        int max = Math.max(1, ModConfig.get().moneyLogMax);
        while (d.logs.size() > max) {
            d.logs.remove(0);
        }
    }

    /** Elimina los datos monetarios de una moneda (al disolver el país). */
    public void remove(String currencyId) {
        if (byCurrency.remove(currencyId) != null) {
            markDirty();
        }
    }

    // ---- NBT ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Map.Entry<String, Data> e : byCurrency.entrySet()) {
            Data d = e.getValue();
            if (d.circulation == 0 && d.logs.isEmpty()) {
                continue;
            }
            NbtCompound c = new NbtCompound();
            c.putString("currency", e.getKey());
            c.putLong("circulation", d.circulation);
            NbtList logs = new NbtList();
            for (MoneyLog log : d.logs) {
                logs.add(log.toNbt());
            }
            c.put("logs", logs);
            list.add(c);
        }
        nbt.put("monetary", list);
        return nbt;
    }

    private static MonetaryManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        MonetaryManager mgr = new MonetaryManager();
        NbtList list = nbt.getList("monetary", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            Data d = new Data();
            d.circulation = c.getLong("circulation");
            NbtList logs = c.getList("logs", NbtElement.COMPOUND_TYPE);
            for (int j = 0; j < logs.size(); j++) {
                d.logs.add(MoneyLog.fromNbt(logs.getCompound(j)));
            }
            mgr.byCurrency.put(c.getString("currency"), d);
        }
        return mgr;
    }
}
