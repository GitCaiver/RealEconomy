package com.realeconomy.finance;

import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

/**
 * Un préstamo que la cuenta del grupo concede a un miembro (parte 9). Se presta un
 * capital en la moneda del grupo con un interés fijo; el total a devolver es
 * {@code capital + capital*interes/100}. Al llegar el plazo (tiempo real), lo que
 * quede pendiente se cobra automáticamente del saldo del deudor hacia la cuenta del
 * grupo; si el saldo no llega, el resto queda como deuda y se sigue cobrando según
 * el deudor recupere saldo.
 */
public class Loan {

    private final UUID borrower;
    private String borrowerName;
    private final long principal;      // capital prestado
    private final int interestPercent; // interés total sobre el capital
    private long outstanding;          // pendiente de devolver (inicia en el total)
    private final long dueAt;          // epoch millis a partir del cual se cobra
    private boolean overdue;           // ya se ha anunciado el vencimiento

    public Loan(UUID borrower, String borrowerName, long principal, int interestPercent,
                long outstanding, long dueAt, boolean overdue) {
        this.borrower = borrower;
        this.borrowerName = borrowerName;
        this.principal = principal;
        this.interestPercent = interestPercent;
        this.outstanding = outstanding;
        this.dueAt = dueAt;
        this.overdue = overdue;
    }

    /** Crea un préstamo nuevo: el pendiente inicial es el total (capital + interés). */
    public static Loan create(UUID borrower, String borrowerName, long principal,
                              int interestPercent, long periodMillis, long now) {
        long total = totalFor(principal, interestPercent);
        return new Loan(borrower, borrowerName, principal, interestPercent, total, now + periodMillis, false);
    }

    public static long totalFor(long principal, int interestPercent) {
        return principal + principal * interestPercent / 100L;
    }

    public UUID getBorrower() {
        return borrower;
    }

    public String getBorrowerName() {
        return borrowerName;
    }

    public void setBorrowerName(String borrowerName) {
        this.borrowerName = borrowerName;
    }

    public long getPrincipal() {
        return principal;
    }

    public int getInterestPercent() {
        return interestPercent;
    }

    /** Total a devolver (capital + interés). */
    public long getTotal() {
        return totalFor(principal, interestPercent);
    }

    public long getOutstanding() {
        return outstanding;
    }

    public void setOutstanding(long outstanding) {
        this.outstanding = Math.max(0L, outstanding);
    }

    public long getDueAt() {
        return dueAt;
    }

    public boolean isDue(long now) {
        return now >= dueAt;
    }

    public boolean isOverdue() {
        return overdue;
    }

    public void setOverdue(boolean overdue) {
        this.overdue = overdue;
    }

    // ---- NBT ----

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putUuid("borrower", borrower);
        c.putString("name", borrowerName == null ? "" : borrowerName);
        c.putLong("principal", principal);
        c.putInt("interest", interestPercent);
        c.putLong("outstanding", outstanding);
        c.putLong("dueAt", dueAt);
        c.putBoolean("overdue", overdue);
        return c;
    }

    public static Loan fromNbt(NbtCompound c) {
        return new Loan(c.getUuid("borrower"), c.getString("name"), c.getLong("principal"),
                c.getInt("interest"), c.getLong("outstanding"), c.getLong("dueAt"), c.getBoolean("overdue"));
    }
}
