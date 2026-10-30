import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Generador de las texturas originales del tema de RealEconomy.
 * Estética: marco de madera oscura, panel marrón, botones crema/dorado con borde negro y detalles dorados.
 * Todo procedural (nada copiado de otros mods).
 */
public class GenTheme {

    // Paleta
    static final int OUTLINE   = 0xFF0E0B07;
    static final int FRAME_D   = 0xFF2A1D0F;
    static final int FRAME_M   = 0xFF3C2A17;
    static final int FRAME_L   = 0xFF513722;
    static final int GOLD      = 0xFFCBA14B;
    static final int GOLD_HI   = 0xFFE7C878;
    static final int GOLD_D    = 0xFF8A6B2E;
    static final int PANEL_B   = 0xFF5B4026;
    static final int PANEL_D   = 0xFF49331F;
    static final int PANEL_L   = 0xFF6B4C2D;

    public static void main(String[] args) throws Exception {
        String out = args[0];
        panel(out + "/panel.png");
        button(out + "/button.png");
        slot(out + "/slot.png");
        System.out.println("Texturas del tema generadas en " + out);
    }

    // Ruido determinista (hash) en [0,1)
    static double noise(int x, int y) {
        int n = x * 374761393 + y * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0x7fffffff) / (double) 0x7fffffff;
    }

    static int lerp(int a, int b, double t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    /** Ventana nine-slice 72x72, borde 14: marco de madera con anillo dorado y panel marrón. */
    static void panel(String path) throws Exception {
        int S = 72, B = 14;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                // distancia al borde más cercano
                int d = Math.min(Math.min(x, y), Math.min(S - 1 - x, S - 1 - y));
                int c;
                if (d == 0) {
                    c = OUTLINE;                         // contorno exterior negro
                } else if (d <= 10) {
                    // Banda de madera: vetas horizontales
                    double streak = noise(x / 2, y);
                    if (streak > 0.80) c = FRAME_L;
                    else if (streak < 0.22) c = FRAME_D;
                    else c = FRAME_M;
                    if (d == 1) c = lerp(c, OUTLINE, 0.35);   // sombra interior del contorno
                } else if (d == 11) {
                    c = GOLD_D;                          // sombra del anillo dorado
                } else if (d == 12) {
                    c = GOLD;                            // anillo dorado
                } else if (d == 13) {
                    c = GOLD_HI;                          // brillo interior del anillo
                } else {
                    // Panel marrón con grano sutil
                    double g = noise(x, y / 2);
                    c = g > 0.86 ? PANEL_L : (g < 0.14 ? PANEL_D : PANEL_B);
                    if (d == 14) c = lerp(c, PANEL_D, 0.6); // inset oscuro justo bajo el oro
                }
                img.setRGB(x, y, c);
            }
        }
        write(img, path);
    }

    /** Botón nine-slice 32x20, borde 6/6/6/6: degradado crema→dorado, borde negro, brillo superior. */
    static void button(String path) throws Exception {
        int W = 32, H = 20;
        int top = 0xFFF2E3B6, bot = 0xFFBE8F38;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            double t = (y - 1) / (double) (H - 2);
            int body = lerp(top, bot, Math.max(0, Math.min(1, t)));
            for (int x = 0; x < W; x++) {
                int c;
                boolean edge = x == 0 || y == 0 || x == W - 1 || y == H - 1;
                if (edge) {
                    c = OUTLINE;
                } else if (y == 1) {
                    c = 0xFFFFF4D2;                      // brillo superior
                } else if (y == H - 2) {
                    c = 0xFF8E6A28;                      // sombra inferior
                } else if (x == 1) {
                    c = lerp(body, 0xFFFFF4D2, 0.35);    // brillo izq
                } else if (x == W - 2) {
                    c = lerp(body, 0xFF8E6A28, 0.35);    // sombra der
                } else {
                    c = body;
                }
                img.setRGB(x, y, c);
            }
        }
        write(img, path);
    }

    /** Casilla hundida 26x26, borde 6: hueco oscuro con bisel, para listados/items. */
    static void slot(String path) throws Exception {
        int S = 26;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                int d = Math.min(Math.min(x, y), Math.min(S - 1 - x, S - 1 - y));
                int c;
                if (d == 0) c = 0xFF14100A;
                else if (x <= 1 || y <= 1) c = 0xFF2A1D0F;       // bisel superior/izq (sombra)
                else if (x >= S - 2 || y >= S - 2) c = 0xFF6B4C2D; // bisel inf/der (luz)
                else c = 0xFF3A2817;                              // fondo del hueco
                img.setRGB(x, y, c);
            }
        }
        write(img, path);
    }

    static void write(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }
}
