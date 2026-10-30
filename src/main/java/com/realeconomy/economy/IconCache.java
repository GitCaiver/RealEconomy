package com.realeconomy.economy;

import com.realeconomy.RealEconomy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Descarga iconos de moneda desde una URL, los normaliza a PNG 64x64 y los
 * cachea en el mundo (realeconomy/icons/&lt;id&gt;.png). Si la descarga falla,
 * usa un icono por defecto incluido en el mod.
 *
 * <p>Las descargas se hacen fuera del hilo del servidor; quien llama debe usar
 * {@link #downloadAsync}.
 */
public final class IconCache {

    public static final int ICON_SIZE = 64;
    private static final long MAX_BYTES = 4L * 1024 * 1024; // 4 MB

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private IconCache() {
    }

    public static Path iconsDir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("realeconomy").resolve("icons");
    }

    public static Path iconFile(MinecraftServer server, String currencyId) {
        return iconsDir(server).resolve(sanitize(currencyId) + ".png");
    }

    private static String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /** Lee de disco los bytes PNG cacheados de una moneda, o el icono por defecto. */
    public static byte[] readOrDefault(MinecraftServer server, String currencyId) {
        try {
            Path f = iconFile(server, currencyId);
            if (Files.isRegularFile(f)) {
                return Files.readAllBytes(f);
            }
        } catch (IOException e) {
            RealEconomy.LOGGER.warn("No se pudo leer el icono cacheado de {}: {}", currencyId, e.toString());
        }
        return defaultIcon();
    }

    /** Bytes PNG del icono por defecto incluido en el jar. */
    public static byte[] defaultIcon() {
        try (InputStream in = IconCache.class.getResourceAsStream("/realeconomy/default_icon.png")) {
            if (in != null) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            RealEconomy.LOGGER.error("No se pudo cargar el icono por defecto", e);
        }
        return new byte[0];
    }

    /**
     * Descarga el icono en segundo plano. Al terminar (con éxito o fallback),
     * ejecuta {@code onReady} en el hilo del servidor con los bytes PNG finales.
     */
    public static void downloadAsync(MinecraftServer server, String currencyId, String url,
                                     java.util.function.Consumer<byte[]> onReady) {
        java.util.concurrent.CompletableFuture.supplyAsync(() -> downloadAndCache(server, currencyId, url))
                .whenComplete((bytes, err) -> {
                    byte[] result = (err == null && bytes != null) ? bytes : defaultIcon();
                    server.execute(() -> onReady.accept(result));
                });
    }

    private static byte[] downloadAndCache(MinecraftServer server, String currencyId, String url) {
        try {
            if (url == null || url.isBlank()) {
                return cacheDefault(server, currencyId);
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "RealEconomy-Mod")
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200 || resp.body() == null || resp.body().length == 0
                    || resp.body().length > MAX_BYTES) {
                RealEconomy.LOGGER.warn("Icono de {}: respuesta inválida ({} bytes, código {})",
                        currencyId, resp.body() == null ? 0 : resp.body().length, resp.statusCode());
                return cacheDefault(server, currencyId);
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(resp.body()));
            if (img == null) {
                RealEconomy.LOGGER.warn("Icono de {}: la URL no es una imagen válida", currencyId);
                return cacheDefault(server, currencyId);
            }
            byte[] png = toPng(normalize(img));
            write(server, currencyId, png);
            RealEconomy.LOGGER.info("Icono de {} descargado y cacheado.", currencyId);
            return png;
        } catch (Exception e) {
            RealEconomy.LOGGER.warn("Fallo al descargar el icono de {} ({}): {}", currencyId, url, e.toString());
            return cacheDefault(server, currencyId);
        }
    }

    private static byte[] cacheDefault(MinecraftServer server, String currencyId) {
        byte[] def = defaultIcon();
        write(server, currencyId, def);
        return def;
    }

    private static void write(MinecraftServer server, String currencyId, byte[] png) {
        try {
            Path dir = iconsDir(server);
            Files.createDirectories(dir);
            Files.write(iconFile(server, currencyId), png);
        } catch (IOException e) {
            RealEconomy.LOGGER.warn("No se pudo cachear el icono de {}: {}", currencyId, e.toString());
        }
    }

    private static BufferedImage normalize(BufferedImage src) {
        BufferedImage dst = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, ICON_SIZE, ICON_SIZE, null);
        g.dispose();
        return dst;
    }

    private static byte[] toPng(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", out);
        return out.toByteArray();
    }
}
