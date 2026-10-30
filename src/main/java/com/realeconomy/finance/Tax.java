package com.realeconomy.finance;

import net.minecraft.nbt.NbtCompound;

/**
 * Un impuesto de grupo: cobra a todos los miembros, a la cuenta del grupo, con
 * una periodicidad en tiempo real. Puede ser una cantidad fija o un porcentaje
 * del saldo del miembro en la moneda del grupo.
 */
public class Tax {

    public enum Type {
        FIXED, PERCENT
    }

    private final String name;
    private Type type;
    private long fixed;        // cantidad fija (FIXED)
    private int percent;       // porcentaje 1-100 (PERCENT)
    private long periodMillis;
    private long lastRun;      // epoch millis del último cobro (o de la creación)

    private Tax(String name) {
        this.name = name;
    }

    public static Tax fixed(String name, long amount, long periodMillis, long now) {
        Tax t = new Tax(name);
        t.type = Type.FIXED;
        t.fixed = amount;
        t.periodMillis = periodMillis;
        t.lastRun = now;
        return t;
    }

    public static Tax percent(String name, int percent, long periodMillis, long now) {
        Tax t = new Tax(name);
        t.type = Type.PERCENT;
        t.percent = percent;
        t.periodMillis = periodMillis;
        t.lastRun = now;
        return t;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public long getFixed() {
        return fixed;
    }

    public int getPercent() {
        return percent;
    }

    public long getPeriodMillis() {
        return periodMillis;
    }

    public long getLastRun() {
        return lastRun;
    }

    public void setLastRun(long lastRun) {
        this.lastRun = lastRun;
    }

    public boolean isDue(long now) {
        return now - lastRun >= periodMillis;
    }

    /** Cantidad a cobrar a un miembro según su saldo actual en la moneda del grupo. */
    public long computeFor(long memberBalance) {
        if (type == Type.FIXED) {
            return fixed;
        }
        return memberBalance * percent / 100L;
    }

    /** Descripción legible del importe (sin periodicidad). */
    public String amountLabel() {
        return type == Type.FIXED ? (fixed + " fijo") : (percent + "%");
    }

    // ---- NBT ----

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putString("name", name);
        c.putString("type", type.name());
        c.putLong("fixed", fixed);
        c.putInt("percent", percent);
        c.putLong("period", periodMillis);
        c.putLong("lastRun", lastRun);
        return c;
    }

    public static Tax fromNbt(NbtCompound c) {
        Tax t = new Tax(c.getString("name"));
        t.type = "PERCENT".equals(c.getString("type")) ? Type.PERCENT : Type.FIXED;
        t.fixed = c.getLong("fixed");
        t.percent = c.getInt("percent");
        t.periodMillis = c.getLong("period");
        t.lastRun = c.getLong("lastRun");
        return t;
    }
}
