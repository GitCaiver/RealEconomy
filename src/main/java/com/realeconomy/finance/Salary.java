package com.realeconomy.finance;

import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

/**
 * Un sueldo recurrente que la cuenta del grupo paga a un miembro con una
 * periodicidad en tiempo real. Si la cuenta no tiene saldo suficiente, el pago
 * se salta hasta el siguiente ciclo.
 */
public class Salary {

    private final UUID member;
    private String memberName;
    private long amount;
    private long periodMillis;
    private long lastRun;

    public Salary(UUID member, String memberName, long amount, long periodMillis, long now) {
        this.member = member;
        this.memberName = memberName;
        this.amount = amount;
        this.periodMillis = periodMillis;
        this.lastRun = now;
    }

    public UUID getMember() {
        return member;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public long getAmount() {
        return amount;
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

    // ---- NBT ----

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putUuid("member", member);
        c.putString("name", memberName == null ? "" : memberName);
        c.putLong("amount", amount);
        c.putLong("period", periodMillis);
        c.putLong("lastRun", lastRun);
        return c;
    }

    public static Salary fromNbt(NbtCompound c) {
        return new Salary(c.getUuid("member"), c.getString("name"),
                c.getLong("amount"), c.getLong("period"), c.getLong("lastRun"));
    }
}
