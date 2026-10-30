package com.realeconomy.client;

import com.realeconomy.RealEconomy;
import com.realeconomy.network.CurrencyEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Estado de monedas en el cliente: metadatos + texturas de icono registradas. */
public final class ClientCurrencyStore {

    /** Textura de reserva (el sprite base de la moneda) si no hay icono. */
    public static final Identifier DEFAULT_TEXTURE =
            Identifier.of(RealEconomy.MOD_ID, "textures/item/coin.png");

    private static final Map<String, CurrencyEntry> ENTRIES = new HashMap<>();
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();

    private ClientCurrencyStore() {
    }

    public static void updateList(List<CurrencyEntry> entries) {
        ENTRIES.clear();
        for (CurrencyEntry e : entries) {
            ENTRIES.put(e.id(), e);
        }
    }

    public static void updateIcon(String id, byte[] png) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            Identifier texId = Identifier.of(RealEconomy.MOD_ID, "currency/" + sanitize(id));
            MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(texId, new NativeImageBackedTexture(image));
            TEXTURES.put(id, texId);
        } catch (IOException e) {
            RealEconomy.LOGGER.warn("No se pudo cargar el icono de moneda {}: {}", id, e.toString());
        }
    }

    public static Identifier getTexture(String id) {
        return TEXTURES.getOrDefault(id, DEFAULT_TEXTURE);
    }

    public static CurrencyEntry getEntry(String id) {
        return ENTRIES.get(id);
    }

    private static String sanitize(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }
}
