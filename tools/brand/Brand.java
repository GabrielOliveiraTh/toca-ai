import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

public class Brand {
    static final String INK = "#0a0a0a", PAPER = "#fafafa";
    static final double K = 0.5522847498;

    static final double BX0 = 88, BY0 = 104, BX1 = 424, BY1 = 344, BR = 72;
    static final double BCX = (BX0 + BX1) / 2, BCY = (BY0 + BY1) / 2;

    static Path2D.Double bubble() {
        Path2D.Double p = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        double k = BR * K;
        p.moveTo(BX0 + BR, BY0);
        p.lineTo(BX1 - BR, BY0);
        p.curveTo(BX1 - BR + k, BY0, BX1, BY0 + BR - k, BX1, BY0 + BR);
        p.lineTo(BX1, BY1 - BR);
        p.curveTo(BX1, BY1 - BR + k, BX1 - BR + k, BY1, BX1 - BR, BY1);
        p.lineTo(220, BY1);
        p.curveTo(204, 372, 160, 398, 108, 408);
        p.curveTo(94, 411, BX0, 406, BX0, 392);
        p.lineTo(BX0, BY0 + BR);
        p.curveTo(BX0, BY0 + BR - k, BX0 + BR - k, BY0, BX0 + BR, BY0);
        p.closePath();
        return p;
    }

    static final double TS = 120;
    static final double TW = TS * Math.sqrt(3) / 2;
    static final double RD = 16;
    static final double RT = 20;

    static Path2D.Double rawTriangle() {
        double[][] v = {{0, 0}, {0, TS}, {TW, TS / 2}};
        double[] r = {RD, RD, RT};
        Path2D.Double p = new Path2D.Double();
        for (int i = 0; i < 3; i++) {
            double[] a = v[(i + 2) % 3], b = v[i], c = v[(i + 1) % 3];
            double[] pin = along(b, a, r[i]), pout = along(b, c, r[i]);
            if (i == 0) p.moveTo(pin[0], pin[1]); else p.lineTo(pin[0], pin[1]);
            p.quadTo(b[0], b[1], pout[0], pout[1]);
        }
        p.closePath();
        return p;
    }
    static double[] along(double[] from, double[] to, double d) {
        double dx = to[0] - from[0], dy = to[1] - from[1], l = Math.hypot(dx, dy);
        return new double[]{from[0] + dx / l * d, from[1] + dy / l * d};
    }

    static double[] centroid(Shape s) {
        PathIterator it = new FlatteningPathIterator(s.getPathIterator(null), 0.005, 20);
        double[] c = new double[6];
        List<double[]> pts = new ArrayList<>();
        while (!it.isDone()) {
            int t = it.currentSegment(c);
            if (t == PathIterator.SEG_MOVETO || t == PathIterator.SEG_LINETO) pts.add(new double[]{c[0], c[1]});
            it.next();
        }
        double A = 0, cx = 0, cy = 0;
        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i), q = pts.get((i + 1) % pts.size());
            double cr = p[0] * q[1] - q[0] * p[1];
            A += cr; cx += (p[0] + q[0]) * cr; cy += (p[1] + q[1]) * cr;
        }
        A /= 2;
        return new double[]{cx / (6 * A), cy / (6 * A), Math.abs(A)};
    }

    static Path2D.Double play;
    static double[] playCentroidRaw, playShift;
    static Path2D.Double glyphShape;
    static Rectangle2D glyphBounds;

    static void buildGeometry() {
        Path2D.Double raw = rawTriangle();
        playCentroidRaw = centroid(raw);
        double dx = BCX - playCentroidRaw[0], dy = BCY - playCentroidRaw[1];
        playShift = new double[]{dx, dy};
        play = new Path2D.Double(raw, AffineTransform.getTranslateInstance(dx, dy));
        glyphShape = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        glyphShape.append(bubble(), false);
        glyphShape.append(play, false);
        glyphBounds = bubble().getBounds2D();
    }

    static String n(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        if (s.equals("-0")) s = "0";
        return s;
    }
    static String pathData(Shape s, AffineTransform at) {
        StringBuilder sb = new StringBuilder();
        double[] c = new double[6];
        for (PathIterator it = s.getPathIterator(at); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> sb.append('M').append(n(c[0])).append(' ').append(n(c[1]));
                case PathIterator.SEG_LINETO -> sb.append('L').append(n(c[0])).append(' ').append(n(c[1]));
                case PathIterator.SEG_QUADTO -> sb.append('Q').append(n(c[0])).append(' ').append(n(c[1])).append(' ').append(n(c[2])).append(' ').append(n(c[3]));
                case PathIterator.SEG_CUBICTO -> sb.append('C').append(n(c[0])).append(' ').append(n(c[1])).append(' ').append(n(c[2])).append(' ').append(n(c[3])).append(' ').append(n(c[4])).append(' ').append(n(c[5]));
                case PathIterator.SEG_CLOSE -> sb.append('Z');
            }
        }
        return sb.toString();
    }
    static AffineTransform inSquare(double s) {
        AffineTransform at = new AffineTransform();
        at.translate(256, 256);
        at.scale(s, s);
        at.translate(-glyphBounds.getCenterX(), -glyphBounds.getCenterY());
        return at;
    }
    static void write(Path p, String s) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s, StandardCharsets.UTF_8);
    }
    static String markSvg(double s) {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 512 512\" width=\"512\" height=\"512\" role=\"img\" aria-label=\"Toca Aí\">\n"
            + "  <rect width=\"512\" height=\"512\" rx=\"112.64\" fill=\"" + INK + "\"/>\n"
            + "  <path fill=\"" + PAPER + "\" fill-rule=\"evenodd\" d=\"" + pathData(glyphShape, inSquare(s)) + "\"/>\n"
            + "</svg>\n";
    }

    enum Kind { ROUNDED, FULL }
    static BufferedImage render(int size, Kind kind, double s) {
        int ss = size <= 64 ? 16 : 4;
        int W = size * ss;
        BufferedImage big = new BufferedImage(W, W, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = big.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double k = W / 512.0;
        g.setColor(Color.decode(INK));
        if (kind == Kind.ROUNDED) g.fill(new RoundRectangle2D.Double(0, 0, W, W, 112.64 * k * 2, 112.64 * k * 2));
        else g.fillRect(0, 0, W, W);
        AffineTransform at = new AffineTransform();
        at.scale(k, k);
        at.concatenate(inSquare(s));
        g.setColor(Color.decode(PAPER));
        g.fill(at.createTransformedShape(glyphShape));
        g.dispose();
        return boxDown(big, size, ss);
    }
    static BufferedImage boxDown(BufferedImage big, int size, int ss) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            double a = 0, r = 0, gg = 0, b = 0;
            for (int j = 0; j < ss; j++) for (int i = 0; i < ss; i++) {
                int p = big.getRGB(x * ss + i, y * ss + j);
                double pa = (p >>> 24) / 255.0;
                a += pa; r += ((p >> 16) & 255) * pa; gg += ((p >> 8) & 255) * pa; b += (p & 255) * pa;
            }
            int cnt = ss * ss;
            int A = (int) Math.round(a / cnt * 255);
            int R = a > 0 ? (int) Math.round(r / a) : 0, G = a > 0 ? (int) Math.round(gg / a) : 0, B = a > 0 ? (int) Math.round(b / a) : 0;
            out.setRGB(x, y, (A << 24) | (R << 16) | (G << 8) | B);
        }
        return out;
    }
    static byte[] png(BufferedImage im) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ImageIO.write(im, "png", bo);
        return bo.toByteArray();
    }
    static void savePng(Path p, BufferedImage im) throws IOException {
        Files.createDirectories(p.getParent());
        Files.write(p, png(im));
    }
    static byte[] ico(int[] sizes, BufferedImage[] ims) throws IOException {
        List<byte[]> data = new ArrayList<>();
        for (BufferedImage im : ims) data.add(png(im));
        int off = 6 + 16 * sizes.length, total = off;
        for (byte[] d : data) total += d.length;
        ByteBuffer b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        for (int i = 0; i < sizes.length; i++) {
            b.put((byte) (sizes[i] >= 256 ? 0 : sizes[i])).put((byte) (sizes[i] >= 256 ? 0 : sizes[i]));
            b.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
            b.putInt(data.get(i).length).putInt(off);
            off += data.get(i).length;
        }
        for (byte[] d : data) b.put(d);
        return b.array();
    }

    static Shape wordShape; static Rectangle2D wordBounds; static double capH, xH;
    static void buildWord(Path ttf) throws Exception {
        Font base = Font.createFont(Font.TRUETYPE_FONT, ttf.toFile());
        System.out.println("fonte: " + base.getFontName() + " / " + base.getFamily());
        float SZ = 1000f;
        Map<TextAttribute, Object> at = new HashMap<>();
        at.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        Font f = base.deriveFont(at).deriveFont(SZ);
        FontRenderContext frc = new FontRenderContext(null, true, true);
        String text = "Toca Aí";
        GlyphVector gv = f.layoutGlyphVector(frc, text.toCharArray(), 0, text.length(), Font.LAYOUT_LEFT_TO_RIGHT);
        double track = -0.025 * SZ;
        Path2D.Double all = new Path2D.Double(Path2D.WIND_NON_ZERO);
        for (int i = 0; i < gv.getNumGlyphs(); i++) {
            Point2D pos = gv.getGlyphPosition(i);
            Shape o = gv.getGlyphOutline(i);
            all.append(AffineTransform.getTranslateInstance(i * track, 0).createTransformedShape(o), false);
        }
        wordShape = all; wordBounds = all.getBounds2D();
        capH = f.createGlyphVector(frc, "T").getOutline().getBounds2D().getHeight();
        xH = f.createGlyphVector(frc, "o").getOutline().getBounds2D().getHeight();
        System.out.println("glifos=" + gv.getNumGlyphs() + " capH/em=" + capH / SZ + " bounds=" + wordBounds);
    }

    static final double GH = 64;
    static double gScale, wScale, wX, wBase, totalW, totalH, offX, offY;
    static void layoutLogo() {
        gScale = GH / glyphBounds.getHeight();
        double bodyH = (BY1 - BY0) * gScale, bodyCy = (BCY - glyphBounds.getY()) * gScale;
        double cap = bodyH * 0.58;
        wScale = cap / capH;
        double gap = GH * 0.22;
        double gw = glyphBounds.getWidth() * gScale;
        wX = gw + gap - wordBounds.getX() * wScale;
        wBase = bodyCy + cap / 2;
        double right = wX + (wordBounds.getMaxX()) * wScale;
        double top = Math.min(0, wBase + wordBounds.getY() * wScale);
        double bottom = Math.max(GH, wBase + wordBounds.getMaxY() * wScale);
        totalW = right; totalH = bottom - top; offX = 0; offY = -top;
    }
    static String logoSvg(String color) {
        AffineTransform ga = new AffineTransform();
        ga.translate(offX, offY);
        ga.scale(gScale, gScale);
        ga.translate(-glyphBounds.getX(), -glyphBounds.getY());
        AffineTransform wa = new AffineTransform();
        wa.translate(offX + wX, offY + wBase);
        wa.scale(wScale, wScale);
        String vb = n(totalW) + " " + n(totalH);
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + vb + "\" width=\"" + n(totalW * 2) + "\" height=\"" + n(totalH * 2) + "\" role=\"img\" aria-label=\"Toca Aí\" fill=\"" + color + "\">\n"
            + "  <path fill-rule=\"evenodd\" d=\"" + pathData(glyphShape, ga) + "\"/>\n"
            + "  <path d=\"" + pathData(wordShape, wa) + "\"/>\n"
            + "</svg>\n";
    }
    static BufferedImage renderLogo(int w, Color bg, Color fg) {
        double sc = w / totalW;
        int h = (int) Math.ceil(totalH * sc);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (bg != null) { g.setColor(bg); g.fillRect(0, 0, w, h); }
        g.setColor(fg);
        AffineTransform ga = new AffineTransform();
        ga.scale(sc, sc); ga.translate(offX, offY); ga.scale(gScale, gScale); ga.translate(-glyphBounds.getX(), -glyphBounds.getY());
        g.fill(ga.createTransformedShape(glyphShape));
        AffineTransform wa = new AffineTransform();
        wa.scale(sc, sc); wa.translate(offX + wX, offY + wBase); wa.scale(wScale, wScale);
        g.fill(wa.createTransformedShape(wordShape));
        g.dispose();
        return im;
    }

    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a[0]);
        Path ttf = Paths.get(a[1]);
        Path sheet = Paths.get(a[2]);
        buildGeometry();
        buildWord(ttf);
        layoutLogo();

        double cRaw = playCentroidRaw[0];
        double[] cf = centroid(play);
        System.out.printf(Locale.ROOT, "triangulo: caixa %.2f x %.2f; centroide da area (arredondado) bruto=(%.3f,%.3f); deslocamento=(%.3f,%.3f); centroide final=(%.3f,%.3f) alvo=(%.1f,%.1f)%n",
            TW, TS, playCentroidRaw[0], playCentroidRaw[1], playShift[0], playShift[1], cf[0], cf[1], BCX, BCY);
        Rectangle2D pb = play.getBounds2D();
        System.out.printf(Locale.ROOT, "caixa do play: x %.2f..%.2f (centro da caixa %.2f, centroide %.2f => a caixa fica %.2f px a direita do centro); y %.2f..%.2f%n",
            pb.getMinX(), pb.getMaxX(), pb.getCenterX(), cf[0], pb.getCenterX() - cf[0], pb.getMinY(), pb.getMaxY());
        System.out.println("balloon bbox: " + glyphBounds);

        write(root.resolve("brand/mark.svg"), markSvg(1.0));
        Rectangle2D gb = glyphBounds;
        AffineTransform gt = AffineTransform.getTranslateInstance(-gb.getX(), -gb.getY());
        write(root.resolve("brand/glyph.svg"),
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + n(gb.getWidth()) + " " + n(gb.getHeight()) + "\" fill=\"currentColor\" role=\"img\" aria-label=\"Toca Aí\">\n"
            + "  <path fill-rule=\"evenodd\" d=\"" + pathData(glyphShape, gt) + "\"/>\n</svg>\n");
        write(root.resolve("brand/logo-horizontal.svg"), logoSvg(INK));
        write(root.resolve("brand/logo-horizontal-dark.svg"), logoSvg(PAPER));
        write(root.resolve("favicon.svg"), markSvg(1.15).replace(" width=\"512\" height=\"512\"", ""));
        write(sheet.resolveSibling("glyph-inline.txt"),
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + n(gb.getWidth()) + " " + n(gb.getHeight()) + "\" fill=\"currentColor\" aria-hidden=\"true\" focusable=\"false\"><path fill-rule=\"evenodd\" d=\"" + pathData(glyphShape, gt) + "\"/></svg>");

        Path ic = root.resolve("icons");
        savePng(ic.resolve("icon-512.png"), render(512, Kind.ROUNDED, 1.0));
        savePng(ic.resolve("icon-192.png"), render(192, Kind.ROUNDED, 1.0));
        savePng(ic.resolve("icon-maskable-512.png"), render(512, Kind.FULL, 0.9));
        savePng(ic.resolve("apple-touch-icon.png"), render(180, Kind.FULL, 1.0));
        BufferedImage f16 = render(16, Kind.ROUNDED, 1.2), f32 = render(32, Kind.ROUNDED, 1.2), f48 = render(48, Kind.ROUNDED, 1.15);
        savePng(ic.resolve("favicon-16.png"), f16);
        savePng(ic.resolve("favicon-32.png"), f32);
        Files.write(root.resolve("favicon.ico"), ico(new int[]{16, 32, 48}, new BufferedImage[]{f16, f32, f48}));

        int W = 1400, H = 1000;
        BufferedImage sh = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sh.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.decode("#f5f5f5")); g.fillRect(0, 0, W / 2, H);
        g.setColor(Color.decode("#0a0a0a")); g.fillRect(W / 2, 0, W / 2, H);
        for (int half = 0; half < 2; half++) {
            int x0 = half * W / 2 + 30;
            boolean dark = half == 1;
            Color fg = Color.decode(dark ? PAPER : INK);
            g.drawImage(ImageIO.read(ic.resolve("icon-512.png").toFile()), x0, 30, 300, 300, null);
            g.drawImage(ImageIO.read(ic.resolve("icon-maskable-512.png").toFile()), x0 + 330, 30, 160, 160, null);
            g.drawImage(ImageIO.read(ic.resolve("apple-touch-icon.png").toFile()), x0 + 330, 200, 130, 130, null);
            AffineTransform at = new AffineTransform();
            at.translate(x0 + 520, 40); at.scale(0.4, 0.4); at.translate(-gb.getX(), -gb.getY());
            g.setColor(fg); g.fill(at.createTransformedShape(glyphShape));
            for (int i = 0; i < 2; i++) {
                double hh = i == 0 ? 28 : 16, sc = hh / gb.getHeight();
                AffineTransform t2 = new AffineTransform();
                t2.translate(x0 + 520 + i * 50, 200); t2.scale(sc, sc); t2.translate(-gb.getX(), -gb.getY());
                g.fill(t2.createTransformedShape(glyphShape));
            }
            g.drawImage(renderLogo(520, null, fg), x0, 370, null);
            g.drawImage(renderLogo(220, null, fg), x0, 520, null);
            g.drawImage(renderLogo(120, null, fg), x0 + 260, 520, null);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            BufferedImage i16 = ImageIO.read(ic.resolve("favicon-16.png").toFile()), i32 = ImageIO.read(ic.resolve("favicon-32.png").toFile());
            g.drawImage(i16, x0, 640, 16, 16, null);
            g.drawImage(i32, x0 + 30, 640, 32, 32, null);
            g.drawImage(i16, x0 + 90, 620, 256, 256, null);
            g.drawImage(i32, x0 + 360, 620, 256, 256, null);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        g.dispose();
        ImageIO.write(sh, "png", sheet.toFile());
        BufferedImage lg = renderLogo(1400, Color.WHITE, Color.decode(INK));
        ImageIO.write(lg, "png", sheet.resolveSibling("logo-big.png").toFile());
    }
}
