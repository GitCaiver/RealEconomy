package com.realeconomy.economy;

import com.realeconomy.RealEconomy;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.network.CurrencyEntry;
import com.realeconomy.network.ModNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro persistente de monedas. En esta versión hay (como máximo) una moneda
 * por grupo, con id == groupId. Orquesta la descarga del icono y el envío de
 * metadatos e iconos a los clientes.
 */
public class CurrencyManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_currencies";

    public static final PersistentState.Type<CurrencyManager> TYPE = new PersistentState.Type<>(
            CurrencyManager::new,
            CurrencyManager::fromNbt,
            null
    );

    private final Map<String, Currency> byId = new HashMap<>();
    private MinecraftServer server;

    public static CurrencyManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        CurrencyManager mgr = psm.getOrCreate(TYPE, STATE_ID);
        mgr.server = server;
        return mgr;
    }

    public Currency getById(String id) {
        return byId.get(id);
    }

    public Currency getByGroup(String groupId) {
        return byId.get(groupId);
    }

    public Collection<Currency> all() {
        return byId.values();
    }

    public boolean groupHasCurrency(String groupId) {
        return byId.containsKey(groupId);
    }

    /** Crea la moneda de un grupo y lanza la descarga del icono. */
    public Currency createForGroup(Group group, String name, String iconUrl) {
        Currency c = new Currency(group.getId(), group.getId(), name, iconUrl);
        byId.put(c.getId(), c);
        markDirty();
        broadcastList();
        refreshIcon(c);
        return c;
    }

    public void setName(Currency c, String name) {
        c.setName(name);
        markDirty();
        broadcastList();
    }

    public void setIcon(Currency c, String url) {
        c.setIconUrl(url);
        markDirty();
        refreshIcon(c);
    }

    /** (Re)descarga el icono y, cuando esté listo, lo difunde a los clientes. */
    private void refreshIcon(Currency c) {
        if (server == null) {
            return;
        }
        IconCache.downloadAsync(server, c.getId(), c.getIconUrl(),
                png -> ModNetworking.broadcastIcon(server, c.getId(), png));
    }

    /** Metadatos de todas las monedas (resolviendo color y siglas del grupo). */
    public List<CurrencyEntry> buildEntries() {
        List<CurrencyEntry> list = new ArrayList<>();
        GroupManager groups = server == null ? null : GroupManager.get(server);
        for (Currency c : byId.values()) {
            String colorName = "gold";
            String tag = "";
            if (groups != null) {
                Group g = groups.getById(c.getGroupId());
                if (g != null) {
                    Formatting f = g.getColor();
                    colorName = (f == null ? Formatting.GOLD : f).getName();
                    tag = g.getTag();
                }
            }
            list.add(new CurrencyEntry(c.getId(), c.getName(), colorName, tag));
        }
        return list;
    }

    public void broadcastList() {
        if (server != null) {
            ModNetworking.broadcastList(server);
        }
    }

    // ---- NBT ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Currency c : byId.values()) {
            list.add(c.toNbt());
        }
        nbt.put("currencies", list);
        return nbt;
    }

    private static CurrencyManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        CurrencyManager mgr = new CurrencyManager();
        NbtList list = nbt.getList("currencies", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            Currency c = Currency.fromNbt(list.getCompound(i));
            mgr.byId.put(c.getId(), c);
        }
        return mgr;
    }
}
