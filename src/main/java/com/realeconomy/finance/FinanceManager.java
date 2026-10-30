package com.realeconomy.finance;

import com.realeconomy.RealEconomy;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Impuestos y sueldos por grupo (parte 6). La cuenta del grupo es el saldo del
 * {@link BalanceManager} indexado por la UUID del grupo (en la moneda del grupo,
 * cuyo id coincide con el id del grupo).
 *
 * <p>El procesamiento es en tiempo real: {@link #tick(MinecraftServer)} se llama
 * cada tick del servidor y cobra/paga lo que esté vencido según el reloj del sistema.
 */
public class FinanceManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_finance";
    /** Cada cuánto (ms) se revisa qué está vencido; no afecta a la periodicidad configurada. */
    private static final long CHECK_INTERVAL = 1000L;

    public static final PersistentState.Type<FinanceManager> TYPE = new PersistentState.Type<>(
            FinanceManager::new,
            FinanceManager::fromNbt,
            null
    );

    /** Datos financieros de un grupo. */
    public static class GroupFinance {
        final List<Tax> taxes = new ArrayList<>();
        final Map<UUID, Salary> salaries = new LinkedHashMap<>();
        /** Sueldo por acción de cada trabajo (clave = Job.name()). Parte 8. */
        final Map<String, Long> wages = new LinkedHashMap<>();
        /** Préstamos activos por deudor (uno por miembro). Parte 9. */
        final Map<UUID, Loan> loans = new LinkedHashMap<>();
        /** Tasa de impuestos general del país (0-100 %), ajustable con la barra. 2ª tanda. */
        int taxRate = 0;
        /** Último cobro de la tasa general (ms, reloj real). */
        long taxRateLast = 0L;
    }

    private final Map<String, GroupFinance> byGroup = new HashMap<>();
    private long lastCheck = 0L;
    private MinecraftServer server;

    public static FinanceManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        FinanceManager mgr = psm.getOrCreate(TYPE, STATE_ID);
        mgr.server = server;
        return mgr;
    }

    private GroupFinance getOrCreate(String groupId) {
        return byGroup.computeIfAbsent(groupId, k -> new GroupFinance());
    }

    /** UUID que identifica la cuenta de un grupo en el libro de saldos. */
    public static UUID accountUuid(Group group) {
        return UUID.fromString(group.getId());
    }

    // ---- Impuestos ----

    public List<Tax> taxes(String groupId) {
        GroupFinance gf = byGroup.get(groupId);
        return gf == null ? List.of() : gf.taxes;
    }

    public Tax getTax(String groupId, String name) {
        for (Tax t : taxes(groupId)) {
            if (t.getName().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }

    public void addTax(String groupId, Tax tax) {
        getOrCreate(groupId).taxes.add(tax);
        markDirty();
    }

    public boolean removeTax(String groupId, String name) {
        GroupFinance gf = byGroup.get(groupId);
        if (gf == null) {
            return false;
        }
        boolean removed = gf.taxes.removeIf(t -> t.getName().equalsIgnoreCase(name));
        if (removed) {
            markDirty();
        }
        return removed;
    }

    // ---- Sueldos ----

    public Map<UUID, Salary> salaries(String groupId) {
        GroupFinance gf = byGroup.get(groupId);
        return gf == null ? Map.of() : gf.salaries;
    }

    public void setSalary(String groupId, Salary salary) {
        getOrCreate(groupId).salaries.put(salary.getMember(), salary);
        markDirty();
    }

    public boolean removeSalary(String groupId, UUID member) {
        GroupFinance gf = byGroup.get(groupId);
        if (gf == null || gf.salaries.remove(member) == null) {
            return false;
        }
        markDirty();
        return true;
    }

    // ---- Sueldos por trabajo (parte 8) ----

    public long getWage(String groupId, com.realeconomy.jobs.Job job) {
        GroupFinance gf = byGroup.get(groupId);
        return gf == null ? 0L : gf.wages.getOrDefault(job.name(), 0L);
    }

    public void setWage(String groupId, com.realeconomy.jobs.Job job, long amount) {
        GroupFinance gf = getOrCreate(groupId);
        if (amount <= 0) {
            gf.wages.remove(job.name());
        } else {
            gf.wages.put(job.name(), amount);
        }
        markDirty();
    }

    // ---- Tasa de impuestos general (barra 0-100, 2ª tanda) ----

    public int getTaxRate(String groupId) {
        GroupFinance gf = byGroup.get(groupId);
        return gf == null ? 0 : gf.taxRate;
    }

    public void setTaxRate(String groupId, int rate) {
        GroupFinance gf = getOrCreate(groupId);
        gf.taxRate = Math.max(0, Math.min(100, rate));
        gf.taxRateLast = System.currentTimeMillis();
        markDirty();
    }

    /** Fuerza el cobro inmediato de la tasa general (para pruebas). */
    public void chargeTaxRateNow(MinecraftServer server, Group g) {
        GroupFinance gf = byGroup.get(g.getId());
        if (gf == null || gf.taxRate <= 0) {
            return;
        }
        Currency c = CurrencyManager.get(server).getByGroup(g.getId());
        if (c == null) {
            return;
        }
        collectTaxRate(server, g, gf, c.getId(), accountUuid(g), BalanceManager.get(server));
        gf.taxRateLast = System.currentTimeMillis();
        markDirty();
    }

    /** Periodo (ms) de cobro de la tasa general, según la config. */
    private static long taxRatePeriod() {
        long p = com.realeconomy.util.TimeUtil.parsePeriod(com.realeconomy.config.ModConfig.get().countryTaxPeriod);
        return p > 0 ? p : 3_600_000L;
    }

    private void collectTaxRate(MinecraftServer server, Group g, GroupFinance gf, String currencyId,
                                UUID account, BalanceManager balances) {
        long collected = 0L;
        for (UUID member : g.getMembers()) {
            long bal = balances.get(member, currencyId);
            long amount = bal * gf.taxRate / 100L;
            if (amount <= 0) {
                continue;
            }
            balances.subtract(member, currencyId, amount);
            collected += amount;
            notify(server, member, Text.literal("Impuesto general (" + gf.taxRate + "%): -" + amount
                    + " a la cuenta de ").append(tagText(g)).formatted(Formatting.YELLOW));
        }
        if (collected > 0) {
            balances.add(account, currencyId, collected);
        }
    }

    // ---- Préstamos (parte 9) ----

    public Map<UUID, Loan> loans(String groupId) {
        GroupFinance gf = byGroup.get(groupId);
        return gf == null ? Map.of() : gf.loans;
    }

    public Loan getLoan(String groupId, UUID borrower) {
        GroupFinance gf = byGroup.get(groupId);
        return gf == null ? null : gf.loans.get(borrower);
    }

    public void addLoan(String groupId, Loan loan) {
        getOrCreate(groupId).loans.put(loan.getBorrower(), loan);
        markDirty();
    }

    public boolean removeLoan(String groupId, UUID borrower) {
        GroupFinance gf = byGroup.get(groupId);
        if (gf == null || gf.loans.remove(borrower) == null) {
            return false;
        }
        markDirty();
        return true;
    }

    // ---- Elimina todos los datos financieros de un grupo (al disolverlo) ----

    public void removeGroup(String groupId) {
        if (byGroup.remove(groupId) != null) {
            markDirty();
        }
    }

    // ---- Procesamiento en tiempo real ----

    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK_INTERVAL) {
            return;
        }
        lastCheck = now;
        if (byGroup.isEmpty()) {
            return;
        }
        GroupManager groups = GroupManager.get(server);
        CurrencyManager currencies = CurrencyManager.get(server);
        BalanceManager balances = BalanceManager.get(server);
        boolean changed = false;

        for (Map.Entry<String, GroupFinance> e : byGroup.entrySet()) {
            String groupId = e.getKey();
            GroupFinance gf = e.getValue();
            Group g = groups.getById(groupId);
            if (g == null) {
                continue;
            }
            Currency currency = currencies.getByGroup(groupId);
            if (currency == null) {
                continue; // sin moneda no se puede cobrar ni pagar
            }
            String currencyId = currency.getId();
            UUID account = accountUuid(g);

            for (Tax tax : gf.taxes) {
                if (tax.isDue(now)) {
                    collectTax(server, g, tax, currencyId, account, balances);
                    tax.setLastRun(now);
                    changed = true;
                }
            }
            if (gf.taxRate > 0 && now - gf.taxRateLast >= taxRatePeriod()) {
                collectTaxRate(server, g, gf, currencyId, account, balances);
                gf.taxRateLast = now;
                changed = true;
            }
            for (Salary salary : gf.salaries.values()) {
                if (salary.isDue(now)) {
                    paySalary(server, g, salary, currencyId, account, balances);
                    salary.setLastRun(now);
                    changed = true;
                }
            }
            if (!gf.loans.isEmpty() && collectLoans(server, g, gf, now, currencyId, account, balances)) {
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    /**
     * Cobra los préstamos vencidos: cuando llega el plazo se descuenta lo pendiente
     * del saldo del deudor hacia la cuenta del grupo; si no hay saldo suficiente, el
     * resto queda como deuda y se seguirá cobrando en siguientes ciclos. Devuelve
     * true si hubo algún cambio.
     */
    private boolean collectLoans(MinecraftServer server, Group g, GroupFinance gf, long now,
                                 String currencyId, UUID account, BalanceManager balances) {
        boolean changed = false;
        List<UUID> paidOff = new ArrayList<>();
        for (Loan loan : gf.loans.values()) {
            if (!loan.isDue(now) || loan.getOutstanding() <= 0) {
                continue;
            }
            long bal = balances.get(loan.getBorrower(), currencyId);
            long charge = Math.min(loan.getOutstanding(), bal);
            if (charge > 0) {
                balances.subtract(loan.getBorrower(), currencyId, charge);
                balances.add(account, currencyId, charge);
                loan.setOutstanding(loan.getOutstanding() - charge);
                changed = true;
            }
            if (loan.getOutstanding() <= 0) {
                paidOff.add(loan.getBorrower());
                notify(server, loan.getBorrower(), Text.literal("Préstamo de ").append(tagText(g))
                        .append(Text.literal(" saldado por completo.")).formatted(Formatting.GREEN));
                changed = true;
            } else if (!loan.isOverdue()) {
                loan.setOverdue(true);
                changed = true;
                notify(server, loan.getBorrower(), Text.literal("Tu préstamo de ").append(tagText(g))
                        .append(Text.literal(" ha vencido. Deuda pendiente: " + loan.getOutstanding()
                                + ". Se cobrará automáticamente de tu saldo.")).formatted(Formatting.RED));
            }
        }
        for (UUID borrower : paidOff) {
            gf.loans.remove(borrower);
        }
        return changed;
    }

    private void collectTax(MinecraftServer server, Group g, Tax tax, String currencyId,
                            UUID account, BalanceManager balances) {
        long collected = 0L;
        for (UUID member : g.getMembers()) {
            long bal = balances.get(member, currencyId);
            long amount = tax.computeFor(bal);
            if (amount <= 0) {
                continue;
            }
            long charge = Math.min(amount, bal);
            if (charge <= 0) {
                continue;
            }
            balances.subtract(member, currencyId, charge);
            collected += charge;
            notify(server, member, Text.literal("Impuesto \"" + tax.getName() + "\": -" + charge
                    + " a la cuenta de ").append(tagText(g)).formatted(Formatting.YELLOW));
        }
        if (collected > 0) {
            balances.add(account, currencyId, collected);
        }
    }

    private void paySalary(MinecraftServer server, Group g, Salary salary, String currencyId,
                           UUID account, BalanceManager balances) {
        long amount = salary.getAmount();
        if (amount <= 0) {
            return;
        }
        if (!balances.subtract(account, currencyId, amount)) {
            // Cuenta del grupo sin saldo suficiente: avisa al líder si está conectado.
            notify(server, g.getLeader(), Text.literal("La cuenta de ").append(tagText(g))
                    .append(Text.literal(" no tiene saldo para el sueldo de " + salary.getMemberName() + "."))
                    .formatted(Formatting.RED));
            return;
        }
        balances.add(salary.getMember(), currencyId, amount);
        notify(server, salary.getMember(), Text.literal("Sueldo de ").append(tagText(g))
                .append(Text.literal(": +" + amount)).formatted(Formatting.GREEN));
    }

    private static Text tagText(Group g) {
        return Text.literal("[" + g.getTag() + "]").formatted(g.getColor());
    }

    private static void notify(MinecraftServer server, UUID uuid, Text message) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            player.sendMessage(message, false);
        }
    }

    // ---- NBT ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Map.Entry<String, GroupFinance> e : byGroup.entrySet()) {
            GroupFinance gf = e.getValue();
            if (gf.taxes.isEmpty() && gf.salaries.isEmpty() && gf.wages.isEmpty() && gf.loans.isEmpty()
                    && gf.taxRate == 0) {
                continue;
            }
            NbtCompound gc = new NbtCompound();
            gc.putString("group", e.getKey());
            gc.putInt("taxRate", gf.taxRate);
            gc.putLong("taxRateLast", gf.taxRateLast);
            NbtList taxList = new NbtList();
            for (Tax t : gf.taxes) {
                taxList.add(t.toNbt());
            }
            gc.put("taxes", taxList);
            NbtList salaryList = new NbtList();
            for (Salary s : gf.salaries.values()) {
                salaryList.add(s.toNbt());
            }
            gc.put("salaries", salaryList);
            NbtCompound wagesNbt = new NbtCompound();
            for (Map.Entry<String, Long> w : gf.wages.entrySet()) {
                wagesNbt.putLong(w.getKey(), w.getValue());
            }
            gc.put("wages", wagesNbt);
            NbtList loanList = new NbtList();
            for (Loan l : gf.loans.values()) {
                loanList.add(l.toNbt());
            }
            gc.put("loans", loanList);
            list.add(gc);
        }
        nbt.put("groups", list);
        return nbt;
    }

    private static FinanceManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        FinanceManager mgr = new FinanceManager();
        NbtList list = nbt.getList("groups", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound gc = list.getCompound(i);
            GroupFinance gf = new GroupFinance();
            gf.taxRate = gc.getInt("taxRate");
            gf.taxRateLast = gc.getLong("taxRateLast");
            NbtList taxList = gc.getList("taxes", NbtElement.COMPOUND_TYPE);
            for (int j = 0; j < taxList.size(); j++) {
                gf.taxes.add(Tax.fromNbt(taxList.getCompound(j)));
            }
            NbtList salaryList = gc.getList("salaries", NbtElement.COMPOUND_TYPE);
            for (int j = 0; j < salaryList.size(); j++) {
                Salary s = Salary.fromNbt(salaryList.getCompound(j));
                gf.salaries.put(s.getMember(), s);
            }
            NbtCompound wagesNbt = gc.getCompound("wages");
            for (String key : wagesNbt.getKeys()) {
                gf.wages.put(key, wagesNbt.getLong(key));
            }
            NbtList loanList = gc.getList("loans", NbtElement.COMPOUND_TYPE);
            for (int j = 0; j < loanList.size(); j++) {
                Loan l = Loan.fromNbt(loanList.getCompound(j));
                gf.loans.put(l.getBorrower(), l);
            }
            mgr.byGroup.put(gc.getString("group"), gf);
        }
        return mgr;
    }
}
