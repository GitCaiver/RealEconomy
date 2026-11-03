package com.realeconomy.employment;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Un aldeano de MCA empleado: su empleador, el país cuya moneda cobra, el trabajo, el
 * sueldo periódico (desde la cuenta del empleador), su cartera propia y su lugar de
 * trabajo (centro + radio) con el cofre vinculado donde deja lo producido.
 */
public class EmployedVillager {

    private final UUID villager;
    private UUID employer;
    private String countryId;
    /** true = paga la cuenta del país (empleo público/gobierno); false = paga el saldo del empleador. */
    private boolean publicHire;
    private VillagerJob job;
    private long wage;
    private long periodMs;
    private long lastPaid;
    private long wallet;
    /** false si el empleador se quedó sin saldo: el aldeano deja de trabajar hasta que haya. */
    private boolean active = true;

    private String dimId;
    private BlockPos zoneCenter;
    private int zoneRadius;
    private BlockPos chest;

    public EmployedVillager(UUID villager, UUID employer, String countryId, boolean publicHire,
                            VillagerJob job, long wage, long periodMs) {
        this.villager = villager;
        this.employer = employer;
        this.countryId = countryId;
        this.publicHire = publicHire;
        this.job = job;
        this.wage = wage;
        this.periodMs = periodMs;
        this.lastPaid = System.currentTimeMillis();
    }

    public UUID villager() {
        return villager;
    }

    public UUID employer() {
        return employer;
    }

    public void setEmployer(UUID employer) {
        this.employer = employer;
    }

    public String countryId() {
        return countryId;
    }

    public void setCountryId(String countryId) {
        this.countryId = countryId;
    }

    public boolean publicHire() {
        return publicHire;
    }

    public void setPublicHire(boolean publicHire) {
        this.publicHire = publicHire;
    }

    public VillagerJob job() {
        return job;
    }

    public void setJob(VillagerJob job) {
        this.job = job;
    }

    public long wage() {
        return wage;
    }

    public void setWage(long wage) {
        this.wage = wage;
    }

    public long periodMs() {
        return periodMs;
    }

    public void setPeriodMs(long periodMs) {
        this.periodMs = periodMs;
    }

    public long lastPaid() {
        return lastPaid;
    }

    public void setLastPaid(long lastPaid) {
        this.lastPaid = lastPaid;
    }

    public long wallet() {
        return wallet;
    }

    public void addWallet(long amount) {
        this.wallet += amount;
    }

    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String dimId() {
        return dimId;
    }

    public BlockPos zoneCenter() {
        return zoneCenter;
    }

    public int zoneRadius() {
        return zoneRadius;
    }

    public boolean hasZone() {
        return zoneCenter != null && zoneRadius > 0 && dimId != null;
    }

    public void setZone(String dimId, BlockPos center, int radius) {
        this.dimId = dimId;
        this.zoneCenter = center;
        this.zoneRadius = radius;
    }

    public BlockPos chest() {
        return chest;
    }

    public void setChest(BlockPos chest) {
        this.chest = chest;
    }

    /** ¿La posición está dentro de la zona de trabajo (cubo de lado 2·radio centrado)? */
    public boolean inZone(String dim, BlockPos pos) {
        if (!hasZone() || !dimId.equals(dim)) {
            return false;
        }
        return Math.abs(pos.getX() - zoneCenter.getX()) <= zoneRadius
                && Math.abs(pos.getZ() - zoneCenter.getZ()) <= zoneRadius
                && Math.abs(pos.getY() - zoneCenter.getY()) <= zoneRadius;
    }

    // ---- NBT ----

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putUuid("villager", villager);
        c.putUuid("employer", employer);
        c.putString("country", countryId);
        c.putBoolean("public", publicHire);
        c.putString("job", job.key());
        c.putLong("wage", wage);
        c.putLong("period", periodMs);
        c.putLong("lastPaid", lastPaid);
        c.putLong("wallet", wallet);
        c.putBoolean("active", active);
        if (dimId != null && zoneCenter != null) {
            c.putString("dim", dimId);
            c.putLong("zone", zoneCenter.asLong());
            c.putInt("radius", zoneRadius);
        }
        if (chest != null) {
            c.putLong("chest", chest.asLong());
        }
        return c;
    }

    public static EmployedVillager fromNbt(NbtCompound c) {
        VillagerJob job = VillagerJob.parse(c.getString("job"));
        if (job == null) {
            job = VillagerJob.MINERO;
        }
        EmployedVillager ev = new EmployedVillager(c.getUuid("villager"), c.getUuid("employer"),
                c.getString("country"), c.getBoolean("public"), job, c.getLong("wage"), c.getLong("period"));
        ev.lastPaid = c.getLong("lastPaid");
        ev.wallet = c.getLong("wallet");
        ev.active = !c.contains("active") || c.getBoolean("active");
        if (c.contains("dim") && c.contains("zone")) {
            ev.dimId = c.getString("dim");
            ev.zoneCenter = BlockPos.fromLong(c.getLong("zone"));
            ev.zoneRadius = c.getInt("radius");
        }
        if (c.contains("chest")) {
            ev.chest = BlockPos.fromLong(c.getLong("chest"));
        }
        return ev;
    }
}
