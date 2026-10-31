package com.realeconomy.politics;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Una "decisión grande" pendiente de votación por los líderes de partido
 * (repúblicas y otras formas con {@code requiresVoteBigDecisions}). Tipos: {@code tax}
 * (fijar impuesto general) o {@code print} (imprimir dinero).
 */
public class BigDecision {

    public final String countryId;
    public final String kind;   // "tax" | "print"
    public final long value;
    public final String proposerName;
    public final long created;
    public final Set<UUID> approvals = new LinkedHashSet<>();
    public final Set<UUID> rejections = new LinkedHashSet<>();

    public BigDecision(String countryId, String kind, long value, String proposerName, long created) {
        this.countryId = countryId;
        this.kind = kind;
        this.value = value;
        this.proposerName = proposerName;
        this.created = created;
    }

    public String describe() {
        return "tax".equals(kind)
                ? "fijar el impuesto general al " + value + "%"
                : "imprimir " + value + " monedas";
    }

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putString("country", countryId);
        c.putString("kind", kind);
        c.putLong("value", value);
        c.putString("proposer", proposerName);
        c.putLong("created", created);
        c.put("approvals", uuidList(approvals));
        c.put("rejections", uuidList(rejections));
        return c;
    }

    private static NbtList uuidList(Set<UUID> set) {
        NbtList list = new NbtList();
        for (UUID u : set) {
            NbtCompound c = new NbtCompound();
            c.putUuid("uuid", u);
            list.add(c);
        }
        return list;
    }

    public static BigDecision fromNbt(NbtCompound c) {
        BigDecision d = new BigDecision(c.getString("country"), c.getString("kind"),
                c.getLong("value"), c.getString("proposer"), c.getLong("created"));
        readUuids(c.getList("approvals", NbtElement.COMPOUND_TYPE), d.approvals);
        readUuids(c.getList("rejections", NbtElement.COMPOUND_TYPE), d.rejections);
        return d;
    }

    private static void readUuids(NbtList list, Set<UUID> into) {
        for (int i = 0; i < list.size(); i++) {
            into.add(list.getCompound(i).getUuid("uuid"));
        }
    }
}
