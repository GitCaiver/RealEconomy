import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Generador de las texturas originales del tema de RealEconomy.
 *
 * <p>Estética: <b>Art Déco + steampunk elegante + neotecnología + opulencia burguesa</b>.
 * Mármol negro, oro y latón, cobre pulido, líneas geométricas simétricas, marcos
 * escalonados y abanicos Art Déco en las esquinas, remaches de latón y un filo de
 * <b>cian/turquesa neón</b> como acento tecnológico. Bordes biselados. Todo procedural
 * (nada copiado de otros mods).
 *
 * <p>Genera (nine-slice): panel, button, slot, field (campo de texto) y bar (barra/carril).
 * Uso: {@code java GenTheme <carpeta_destino>}.
 */
public class GenTheme {

    // ---- Paleta ----
    static final int OUTLINE   = 0xFF07070A; // contorno casi negro
    static final int MARBLE_D  = 0xFF131219; // mármol oscuro (sombra / veta profunda)
    static final int MARBLE_M  = 0xFF1C1B25; // mármol oscuro (base del panel)
    static final int MARBLE_L  = 0xFF272634; // mármol oscuro (veta clara)
    static final int GOLD      = 0xFFD4AF37; // oro
    static final int GOLD_HI   = 0xFFF2D785; // brillo del oro
    static final int GOLD_D    = 0xFF8A6B22; // sombra del oro
    static final int BRASS     = 0xFFB08A3E; // latón
    static final int BRASS_D   = 0xFF6E541F; // latón oscuro
    static final int COPPER    = 0xFFB5703C; // cobre pulido
    static final int CYAN      = 0xFF35E0D8; // turquesa neón (acento)
    static final int CYAN_D    = 0xFF167C78; // turquesa apagado

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : ".";
        panel(out + "/panel.png");
        button(out + "/button.png");
        slot(out + "/slot.png");
        field(out + "/field.png");
        bar(out + "/bar.png");
        System.out.println("Texturas del tema (Art Déco / steampunk) generadas en " + out);
    }

    // Ruido determinista (hash) en [0,1)
    static double noise(int x, int y) {
        int n = x * 374761393 + y * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0x7fffffff) / (double) 0x7fffffff;
    }

    static int lerp(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    /**
     * Panel/marco nine-slice 72x72, borde 14. Mármol negro con marco escalonado de oro/latón,
     * abanicos Art Déco en las esquinas (zona fija del nine-slice) y remaches de latón.
     */
    static void panel(String path) throws Exception {
        int S = 72, B = 14;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                int d = Math.min(Math.min(x, y), Math.min(S - 1 - x, S - 1 - y));
                int c;
                if (d == 0) {
                    c = OUTLINE;                                 // contorno exterior
                } else if (d == 1) {
                    c = lerp(BRASS, OUTLINE, 0.25);              // filo de latón
                } else if (d <= 5) {
                    // Banda de mármol oscuro del marco, con sutil brillo vertical.
                    double g = noise(x, y / 3);
                    c = g > 0.9 ? MARBLE_L : (g < 0.12 ? MARBLE_D : MARBLE_M);
                } else if (d == 6) {
                    c = GOLD_D;                                  // escalón: sombra
                } else if (d == 7) {
                    c = GOLD;                                     // anillo dorado exterior
                } else if (d == 8) {
                    c = GOLD_HI;                                  // brillo del anillo
                } else if (d == 9) {
                    c = CYAN_D;                                   // filo tecnológico (turquesa)
                } else if (d == 10) {
                    c = lerp(CYAN, MARBLE_D, 0.35);               // neón turquesa tenue
                } else if (d <= 12) {
                    c = lerp(MARBLE_D, OUTLINE, 0.4);             // bisel interior oscuro
                } else if (d == 13) {
                    c = lerp(MARBLE_L, GOLD_D, 0.3);              // reflejo del bisel
                } else {
                    // Interior: mármol negro con veteado diagonal sutil.
                    double v = noise(x + y, (x - y) / 2);
                    c = v > 0.88 ? MARBLE_L : (v < 0.1 ? MARBLE_D : MARBLE_M);
                }
                img.setRGB(x, y, c);
            }
        }
        // Abanicos Art Déco en las 4 esquinas (dentro del borde fijo del nine-slice).
        decoFan(img, 3, 3, 1, 1);
        decoFan(img, S - 4, 3, -1, 1);
        decoFan(img, 3, S - 4, 1, -1);
        decoFan(img, S - 4, S - 4, -1, -1);
        // Remaches de latón a media banda en los centros de cada lado.
        int m = S / 2;
        rivet(img, m, 3);
        rivet(img, m, S - 4);
        rivet(img, 3, m);
        rivet(img, S - 4, m);
        write(img, path);
    }

    /** Líneas escalonadas tipo abanico Art Déco desde una esquina (ox,oy) en dirección (sx,sy). */
    static void decoFan(BufferedImage img, int ox, int oy, int sx, int sy) {
        int[] lens = {5, 3, 2};
        for (int i = 0; i < lens.length; i++) {
            int step = i + 1;
            for (int k = 0; k <= lens[i]; k++) {
                plot(img, ox + sx * (step + k), oy + sy * step, i == 0 ? GOLD_HI : GOLD);
                plot(img, ox + sx * step, oy + sy * (step + k), i == 0 ? GOLD_HI : GOLD);
            }
        }
    }

    static void rivet(BufferedImage img, int cx, int cy) {
        plot(img, cx, cy, GOLD_HI);
        plot(img, cx - 1, cy, BRASS);
        plot(img, cx + 1, cy, BRASS_D);
        plot(img, cx, cy - 1, BRASS);
        plot(img, cx, cy + 1, BRASS_D);
    }

    static void plot(BufferedImage img, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
            img.setRGB(x, y, c);
        }
    }

    /** Botón nine-slice 32x20, borde 6: cuerpo de latón/mármol oscuro con bisel dorado y brillo superior. */
    static void button(String path) throws Exception {
        int W = 32, H = 20;
        int top = 0xFF3B3322, bot = 0xFF201B12; // cuerpo oscuro (latón envejecido)
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            double t = (y - 1) / (double) (H - 2);
            int body = lerp(top, bot, t);
            for (int x = 0; x < W; x++) {
                int c;
                boolean edge = x == 0 || y == 0 || x == W - 1 || y == H - 1;
                if (edge) {
                    c = OUTLINE;
                } else if (y == 1 || x == 1) {
                    c = GOLD_HI;                         // bisel claro (arriba/izq)
                } else if (y == H - 2 || x == W - 2) {
                    c = GOLD_D;                          // bisel oscuro (abajo/der)
                } else if (y == 2) {
                    c = lerp(GOLD, body, 0.25);          // brillo dorado bajo el bisel
                } else {
                    c = body;
                }
                img.setRGB(x, y, c);
            }
        }
        write(img, path);
    }

    /** Casilla hundida nine-slice 26x26, borde 6: hueco de mármol con bisel dorado (filas/items). */
    static void slot(String path) throws Exception {
        int S = 26;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                int d = Math.min(Math.min(x, y), Math.min(S - 1 - x, S - 1 - y));
                int c;
                if (d == 0) c = OUTLINE;
                else if (x <= 1 || y <= 1) c = GOLD_D;            // bisel superior/izq (hundido)
                else if (x >= S - 2 || y >= S - 2) c = lerp(GOLD, MARBLE_D, 0.3); // bisel inf/der
                else {
                    double v = noise(x, y + x);
                    c = v > 0.9 ? MARBLE_M : MARBLE_D;            // fondo del hueco
                }
                img.setRGB(x, y, c);
            }
        }
        write(img, path);
    }

    /** Campo de texto nine-slice 24x24, borde 6: fondo muy oscuro con marco de latón y filo turquesa. */
    static void field(String path) throws Exception {
        int S = 24;
        int dark = 0xFF0C0B11;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                int d = Math.min(Math.min(x, y), Math.min(S - 1 - x, S - 1 - y));
                int c;
                if (d == 0) c = OUTLINE;
                else if (d == 1) c = BRASS_D;                     // marco de latón
                else if (d == 2) c = lerp(CYAN_D, dark, 0.4);     // filo turquesa tenue
                else c = dark;                                    // interior de escritura
                img.setRGB(x, y, c);
            }
        }
        write(img, path);
    }

    /** Barra/carril nine-slice 24x12, borde 6: canal hundido de mármol con reborde dorado (sliders/tendencias). */
    static void bar(String path) throws Exception {
        int W = 24, H = 12;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int d = Math.min(Math.min(x, y), Math.min(W - 1 - x, H - 1 - y));
                int c;
                if (d == 0) c = OUTLINE;
                else if (y <= 1) c = GOLD_D;                      // sombra superior (hundido)
                else if (y >= H - 2) c = lerp(GOLD, MARBLE_D, 0.4); // luz inferior
                else if (x <= 1 || x >= W - 2) c = BRASS_D;
                else c = 0xFF0E0D13;                              // canal
                img.setRGB(x, y, c);
            }
        }
        write(img, path);
    }

    static void write(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }
}
