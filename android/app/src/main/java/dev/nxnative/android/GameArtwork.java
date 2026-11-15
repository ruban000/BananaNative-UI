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
            File logo = findLogo(dump);
            Bitmap icon = decode(findImage(logo, new String[]{"icon", "icon0", "game_icon", "cover", "title"}, true), 512);
            Bitmap background = decode(findImage(logo, new String[]{"background", "bg", "banner"}, false), 1920);
            Artwork artwork = new Artwork(icon, background);
            MAIN.post(() -> done.accept(artwork));
        }, "nx-game-artwork").start();
    }

    private static File findLogo(File dump) {
        File updates = new File(dump, "updates");
        File fixed = new File(updates, "655360/romfs/Logo");
        if (fixed.isDirectory()) return fixed;
        File direct = new File(dump, "romfs/Logo");
        if (direct.isDirectory()) return direct;
        File logo = new File(dump, "Logo");
        if (logo.isDirectory()) return logo;
        File[] updateDirs = updates.listFiles();
        if (updateDirs != null) for (File update : updateDirs) {
            File candidate = new File(update, "romfs/Logo");
            if (candidate.isDirectory()) return candidate;
        }
        // Some dumps add extra wrapper folders. Search the selected folder's
        // subdirectories for a Logo directory after checking the known layouts.
        return findNamedDirectory(dump, "Logo", 0, 8);
    }

    private static File findNamedDirectory(File dir, String name, int depth, int maxDepth) {
        if (dir == null || !dir.isDirectory() || depth > maxDepth) return null;
        File[] children = dir.listFiles();
        if (children == null) return null;
        java.util.Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File child : children) {
            if (child.isDirectory() && child.getName().equalsIgnoreCase(name)) return child;
        }
        for (File child : children) {
            if (!child.isDirectory()) continue;
            String n = child.getName().toLowerCase(Locale.ROOT);
            if (n.equals("android") || n.equals("data") || n.equals("cache") || n.equals(".git")) continue;
            File found = findNamedDirectory(child, name, depth + 1, maxDepth);
            if (found != null) return found;
        }
        return null;
    }

    private static File findImage(File logo, String[] preferred, boolean allowFallback) {
        if (logo == null || !logo.isDirectory()) return null;
        for (String name : preferred) {
            File exact = new File(logo, name);
            if (isImage(exact)) return exact;
            for (String ext : new String[]{".png", ".jpg", ".jpeg", ".webp"}) {
                File withExt = new File(logo, name + ext);
                if (isImage(withExt)) return withExt;
            }
        }
        File backgroundDir = new File(logo, "background");
        File found = firstImage(backgroundDir);
        if (found != null && !allowFallback) return found;
        return allowFallback ? firstImageExceptBackground(logo) : null;
    }

    private static File firstImageExceptBackground(File logo) {
        File[] files = logo.listFiles();
        if (files == null) return null;
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : files) if (!f.isDirectory() && isImage(f) && !f.getName().toLowerCase(Locale.ROOT).contains("background")) return f;
        for (File f : files) if (f.isDirectory() && !f.getName().equalsIgnoreCase("background")) {
            File nested = firstImage(f);
            if (nested != null) return nested;
        }
        return null;
    }

    private static File firstImage(File dir) {
        if (dir == null || !dir.isDirectory()) return isImage(dir) ? dir : null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : files) if (isImage(f)) return f;
        for (File f : files) if (f.isDirectory()) {
            File nested = firstImage(f);
            if (nested != null) return nested;
        }
        return null;
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
