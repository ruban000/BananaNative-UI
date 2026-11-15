package dev.nxnative.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.Locale;
import java.util.function.Consumer;

/** Reads optional cover and background artwork from a selected Switch dump's RomFS Logo folder. */
final class GameArtwork {
    static final class Artwork {
        final Bitmap icon, background;
        Artwork(Bitmap icon, Bitmap background) { this.icon = icon; this.background = background; }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private GameArtwork() {}

    static void load(File dump, Consumer<Artwork> done) {
        if (dump == null || !dump.isDirectory()) {
            MAIN.post(() -> done.accept(new Artwork(null, null)));
            return;
        }
        new Thread(() -> {
            File iconFile = findArtworkFile(dump, false);
            File backgroundFile = findArtworkFile(dump, true);
            Bitmap icon = decode(iconFile, 512);
            Bitmap background = decode(backgroundFile, 1920);
            Artwork artwork = new Artwork(icon, background);
            MAIN.post(() -> done.accept(artwork));
        }, "nx-game-artwork").start();
    }

    private static File findArtworkFile(File root, boolean background) {
        java.util.ArrayList<File> matches = new java.util.ArrayList<>();
        collectImages(root, matches, 0, 24);
        File best = null;
        int bestScore = Integer.MIN_VALUE;
        for (File file : matches) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            String path = file.getAbsolutePath().toLowerCase(Locale.ROOT);
            boolean isBackground = name.contains("background") || name.contains("wallpaper") || name.contains("banner") || name.startsWith("bg_") || name.startsWith("bg-") || name.equals("bg.png") || name.equals("bg.jpg") || name.equals("bg.jpeg") || path.contains("/background/");
            boolean isLogo = name.contains("logo") || name.contains("icon") || name.contains("cover") || name.contains("jacket") || name.contains("thumbnail");
            int score = 0;
            if (background) { if (isBackground) score += 100; if (isLogo) score -= 100; }
            else { if (isLogo) score += 100; if (isBackground) score -= 100; }
            if (path.contains("/romfs/logo/")) score += 20;
            if (score > bestScore) { bestScore = score; best = file; }
        }
        if (bestScore <= 0 && !background) return matches.isEmpty() ? null : matches.get(0);
        return bestScore > 0 ? best : null;
    }

    private static void collectImages(File dir, java.util.List<File> out, int depth, int maxDepth) {
        if (dir == null || !dir.isDirectory() || depth > maxDepth) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        java.util.Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File child : children) if (child.isFile() && isImage(child)) out.add(child);
        for (File child : children) {
            if (!child.isDirectory()) continue;
            String n = child.getName().toLowerCase(Locale.ROOT);
            if (n.equals("android") || n.equals("data") || n.equals("cache") || n.equals(".git") || n.equals("shader") || n.equals("shaders")) continue;
            collectImages(child, out, depth + 1, maxDepth);
        }
    }

    private static boolean isImage(File f) {
        if (f == null || !f.isFile()) return false;
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp");
    }

    private static Bitmap decode(File file, int maxDimension) {
        if (!isImage(file)) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError | RuntimeException e) {
            return null;
        }
    }
}
