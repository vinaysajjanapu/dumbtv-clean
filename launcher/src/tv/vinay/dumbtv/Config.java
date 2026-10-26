package tv.vinay.dumbtv;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * All settings live in one plain {@code key=value} file in the app's external files dir,
 * so {@code adb pull}/{@code adb push} is the whole export/import story and a hand edit is
 * picked up on the next return to the home screen. Where adb cannot reach that folder
 * (Fire OS 8), the same file can be pushed into the app's media folder instead.
 */
final class Config {
    static final String FILE = "home.conf";

    static final class Choice {
        final String key, title;
        final String[] values, names;
        final String fallback;

        Choice(String key, String title, String fallback, String[] values, String[] names) {
            this.key = key;
            this.title = title;
            this.fallback = fallback;
            this.values = values;
            this.names = names;
        }

        String name(String value) {
            int i = Arrays.asList(values).indexOf(value);
            return i < 0 ? value : names[i];
        }
    }

    /** The blue swatch. All apps rows and a tile being moved still use it. */
    static final int BLUE = 0xFF3D8BFF;
    static final Choice ACCENT = new Choice("accent", "Accent Color", "blue",
            new String[] {"white", "blue", "cyan", "green", "amber", "red", "pink", "violet"},
            new String[] {"White", "Blue", "Cyan", "Green", "Amber", "Red", "Pink", "Violet"});
    static final Choice TILES = new Choice("tiles", "Tile Size", "medium",
            new String[] {"small", "medium", "large"},
            new String[] {"Small", "Medium", "Large"});
    static final Choice LAYOUT = new Choice("layout", "Layout", "rows",
            new String[] {"rows", "grid"},
            new String[] {"Rows", "Grid"});
    static final Choice ORDER = new Choice("order", "Sort Order", "custom",
            new String[] {"custom", "alpha", "recent"},
            new String[] {"Custom", "Alphabetical", "Recently Used"});
    /** New installs join the home row when this is On. Apps already there are left as they are. */
    static final Choice AUTO = new Choice("apps.auto", "Add New Apps Automatically", "on",
            new String[] {"on", "off"},
            new String[] {"On", "Off"});
    static final Choice LABELS = new Choice("labels", "App Names", "never",
            new String[] {"focus", "always", "never"},
            new String[] {"On Focus", "Always", "Never"});
    static final Choice CLOCK = new Choice("clock", "Clock", "auto",
            new String[] {"auto", "24h", "12h"},
            new String[] {"System", "24-Hour", "12-Hour"});
    static final Choice INPUTS = new Choice("inputs", "Inputs Row", "off",
            new String[] {"on", "off"},
            new String[] {"Shown", "Hidden"});
    static final Choice SWITCH = new Choice("switch", "Open Each Input In", "auto",
            new String[] {"auto", "tv", "home"},
            new String[] {"Automatic", "The TV's App", "Dumb TV Clean"});
    /**
     * Older files may still say black, image, or adaptive. The home screen ignores them
     * and always shows the wallpaper service.
     */
    static final Choice BACKGROUND = new Choice("background", "Background", "service",
            new String[] {"black", "graphite", "midnight", "image", "service", "adaptive", "adaptive-tint"},
            new String[] {"Black", "Graphite", "Midnight", "Image File", "Wallpaper Service", "Adaptive Solid", "Adaptive Wallpaper Tint"});
    /** Kept so an older settings file still parses. The search always uses {@code provider.name}. */
    static final Choice PROVIDER = new Choice("provider", "Wallpaper Provider", "custom",
            new String[] {"abstract", "minimal", "gradient", "custom"},
            new String[] {"Abstract", "Minimal", "Gradient", "Custom"});
    static final Choice DIM = new Choice("dim", "Darkness", "40",
            new String[] {"0", "20", "40", "60", "80"},
            new String[] {"0%", "20%", "40%", "60%", "80%"});

    static final Choice ROTATE = new Choice("rotate", "Picture Rotation", "off",
            new String[] {"off", "visit", "hourly", "daily", "daytime"},
            new String[] {"Off", "When You Return", "Every Hour", "Every Day", "By Time of Day"});

    /**
     * Hides All Apps, Home Settings and System Settings and turns off the tile menu. Holding Menu for
     * {@link Home#UNLOCK_MS} opens Home Settings.
     */
    static final Choice LOCK = new Choice("lock", "Child Lock", "off",
            new String[] {"off", "on"},
            new String[] {"Off", "On"});

    /**
     * Time of day follows the clock and then the name. Custom uses {@link #GREETING_TEXT}
     * as the whole greeting. Blank custom text keeps the time-of-day greeting.
     */
    static final Choice GREETING = new Choice("greeting", "Greeting", "time",
            new String[] {"time", "custom"},
            new String[] {"Time of Day", "Custom"});
    static final String GREETING_TEXT = "greeting.text";
    /**
     * The line under the greeting. {@link #WELCOME_TEXT} replaces the category while it is set.
     * calm is the time-of-day list; the others are single lists.
     */
    static final Choice WELCOME = new Choice("welcome", "Style", "calm",
            new String[] {"calm", "motivate", "funny", "tech", "roast"},
            new String[] {"Time of Day", "Motivational", "Funny", "Tech", "Teasing"});
    static final String WELCOME_TEXT = "welcome.text";
    /** On shows the line under the greeting. Off hides that line; the greeting stays. */
    static final String WELCOME_SHOW = "welcome.show";
    /**
     * The line is built, but not offered on the greeting page yet. False hides that section
     * and keeps the line off, whatever the file says. The lists and the switch stay.
     */
    static final boolean WELCOME_LINE = false;

    static final Choice[] CHOICES = {
        ACCENT, TILES, LAYOUT, ORDER, AUTO, LABELS, CLOCK, INPUTS, SWITCH, BACKGROUND, PROVIDER, DIM, ROTATE, LOCK,
        GREETING, WELCOME
    };

    static boolean autoAdd(Config cfg) {
        return "on".equals(cfg.get(AUTO));
    }

    /** Off unless the greeting page offers the line and the file asks for it. */
    static boolean welcomeLine(Config cfg) {
        return WELCOME_LINE && welcomeOn(cfg.get(WELCOME_SHOW, "off"));
    }

    static boolean welcomeOn(String value) {
        return "on".equals(value);
    }

    /** Folder of wallpapers, next to home.conf, that Background › Image file rotates through. */
    static final String WALLPAPERS = "wallpapers";
    private static final String OFFSET = "wallpaper.offset";
    /** Visit count for wallpaper rotation. Not a home.conf key: writing it must not reload settings. */
    private static final String VISITS = "service-visits";

    private static final int[] ACCENTS = {
        0xFFFFFFFF, BLUE, 0xFF2BC4E8, 0xFF34C759, 0xFFFFB340, 0xFFFF5147, 0xFFFF5C9D, 0xFFA07CFF,
    };
    private static final int[] BACKGROUNDS = {0xFF000000, 0xFF141518, 0xFF0B1220, 0xFF000000, 0xFF000000, 0xFF141518, 0xFF141518};

    private static Config instance;

    final File dir;
    private final File file;
    /** The app's media folder, /sdcard/Android/media/tv.vinay.dumbtv: adb can write there. */
    private final File shared;
    private final File sharedFile;
    /** What was last applied from the media folder, so the same push is not applied twice. */
    private final File appliedFile;
    private final Map<String, String> values = new LinkedHashMap<>();
    private long signature = Long.MIN_VALUE;
    private int version;
    private long visits;
    /** 0 not read, 1 read, 2 this process has already counted its first open. */
    private int visitState;

    static Config get(Context c) {
        if (instance == null) instance = new Config(c.getApplicationContext());
        return instance;
    }

    private Config(Context c) {
        File external = c.getExternalFilesDir(null);
        dir = external != null ? external : c.getFilesDir();
        file = new File(dir, FILE);
        // Fire OS 8 (Android 11) hides /sdcard/Android/data/<pkg> from adb, so nothing can be
        // pushed into the app's own folder. The app's media folder is still writable by adb,
        // so it is used as a second source: wallpapers are read from it, and a settings file
        // pushed there is merged in once.
        File[] media = c.getExternalMediaDirs();
        shared = (media != null && media.length > 0 && media[0] != null) ? media[0] : null;
        sharedFile = shared == null ? null : new File(shared, FILE);
        appliedFile = new File(dir, FILE + ".applied");
        refresh();
    }

    /** Changes every time a setting changes, from the UI or from the file on disk. */
    int version() {
        return version;
    }

    void refresh() {
        long sig = signature();
        if (sig == signature) return;
        signature = sig;
        values.clear();
        if (file.isFile()) read();
        if (mergeShared()) save();
        version++;
    }

    /** Both files count: a settings file pushed into the media folder has to be noticed too. */
    private long signature() {
        long sig = file.lastModified() * 31 + file.length();
        if (sharedFile != null) sig = sig * 31 + hash(sharedFile);
        return sig;
    }

    /** Content hash, so a push is noticed even when it carries an old timestamp. */
    private static long hash(File f) {
        if (!f.isFile()) return 0;
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return Arrays.hashCode(out.toByteArray());
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Takes the settings from a file pushed into the media folder, the one folder adb can still
     * write on Fire OS 8. The push is applied once: what was applied is remembered, so the same
     * file is not applied again and a later change on the TV is not overwritten by it.
     */
    private boolean mergeShared() {
        if (sharedFile == null || !sharedFile.isFile()) return false;
        Map<String, String> incoming = parse(sharedFile);
        if (incoming.isEmpty()) return false;
        if (incoming.equals(parse(appliedFile))) return false;
        values.putAll(incoming);
        writeSettings(appliedFile, incoming);
        return true;
    }

    private void read() {
        values.putAll(parse(file));
    }

    private static Map<String, String> parse(File f) {
        Map<String, String> out = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (line.startsWith("#") || eq <= 0) continue;
                out.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        } catch (IOException e) {
            Log.w("dumbtv", "could not read " + f, e);
        }
        return out;
    }

    private void save() {
        writeSettings(file, values);
        signature = signature();
    }

    private static void writeSettings(File target, Map<String, String> settings) {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            w.write("# dumbtv home. Edit on the TV (Home Settings) or here; unknown keys are kept.\n");
            for (Map.Entry<String, String> e : settings.entrySet()) {
                w.write(e.getKey() + "=" + e.getValue() + "\n");
            }
        } catch (IOException e) {
            Log.w("dumbtv", "could not write " + tmp, e);
            return;
        }
        if (!tmp.renameTo(target)) Log.w("dumbtv", "could not replace " + target);
    }

    String get(String key, String fallback) {
        String v = values.get(key);
        return v == null || v.isEmpty() ? fallback : v;
    }

    String get(Choice c) {
        String v = values.get(c.key);
        return v != null && Arrays.asList(c.values).contains(v) ? v : c.fallback;
    }

    void set(String key, String value) {
        if (value == null || value.isEmpty()) values.remove(key);
        else values.put(key, value);
        version++;
        save();
    }

    List<String> list(String key) {
        List<String> out = new ArrayList<>();
        for (String s : get(key, "").split(",")) {
            if (!s.trim().isEmpty()) out.add(s.trim());
        }
        return out;
    }

    void setList(String key, List<String> items) {
        // String.join is Android 8+; TextUtils.join keeps Fire OS 6 (Android 7.1) working.
        set(key, TextUtils.join(",", items));
    }

    void removePrefix(String... prefixes) {
        for (Iterator<String> it = values.keySet().iterator(); it.hasNext(); ) {
            String k = it.next();
            for (String p : prefixes) {
                if (k.startsWith(p)) {
                    it.remove();
                    break;
                }
            }
        }
        version++;
        save();
    }

    int accent() {
        String v = values.get(ACCENT.key);
        if (v != null && v.startsWith("#")) {
            try {
                return Color.parseColor(v) | 0xFF000000;
            } catch (IllegalArgumentException ignored) {
                // falls through to the named palette
            }
        }
        return ACCENTS[Arrays.asList(ACCENT.values).indexOf(get(ACCENT))];
    }

    int background() {
        return BACKGROUNDS[Arrays.asList(BACKGROUND.values).indexOf(get(BACKGROUND))];
    }

    /**
     * Counts a return to the home screen, for the "visit" rotation.
     * Opening after a restart keeps the saved count. The next return in that
     * same run is a new visit, and that is when the picture changes.
     */
    void visited() {
        loadVisits();
        if (visitState == 1) {
            visitState = 2;
            if (visits < 1) writeVisits(visits = 1);
            return;
        }
        writeVisits(++visits);
    }

    /** Steps the rotation forward by one, so the next wallpaper shows now. */
    void nextWallpaper() {
        set(OFFSET, String.valueOf(offset() + 1));
    }

    int wallpaperOffset() {
        return offset();
    }

    /** Which return home the current picture belongs to. A restart keeps this. */
    long homeVisits() {
        long n = loadVisits();
        return n < 1 ? 1 : n;
    }

    private long loadVisits() {
        if (visitState == 0) {
            visits = readVisits();
            visitState = 1;
        }
        return visits;
    }

    private long readVisits() {
        File f = new File(dir, VISITS);
        if (!f.isFile()) return 0;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line = r.readLine();
            return line == null ? 0 : Long.parseLong(line.trim());
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    private void writeVisits(long n) {
        File f = new File(dir, VISITS);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(Long.toString(n));
        } catch (IOException e) {
            Log.w("dumbtv", "could not save the wallpaper visit", e);
        }
    }

    private int offset() {
        try {
            return Integer.parseInt(get(OFFSET, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 0 morning (6-13 h), 1 afternoon (13-20 h), 2 night. */
    static int period() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 6 && h < 13 ? 0 : h >= 13 && h < 20 ? 1 : 2;
    }

    private static final String[][] PERIOD_WORDS = {
        {"morning", "day"},
        {"afternoon", "evening"},
        {"night", "midnight"},
    };

    /**
     * Files whose name carries a word for {@code period}; empty when others are tagged but not
     * this one, null when no file is tagged at all (then the period just indexes the pool).
     */
    private static List<File> forPeriod(List<File> pool, int period) {
        List<File> fit = new ArrayList<>();
        boolean tagged = false;
        for (File f : pool) {
            // Whole words only, so "sunday.jpg" or "holiday.jpg" is not a morning picture.
            List<String> words = Arrays.asList(f.getName().toLowerCase(Locale.ROOT).split("[^a-z]+"));
            for (int p = 0; p < PERIOD_WORDS.length; p++) {
                for (String w : PERIOD_WORDS[p]) {
                    if (words.contains(w)) {
                        tagged = true;
                        if (p == period && !fit.contains(f)) fit.add(f);
                    }
                }
            }
        }
        return tagged ? fit : null;
    }

    List<File> wallpapers() {
        List<File> out = new ArrayList<>();
        addPictures(new File(dir, WALLPAPERS), out);
        if (shared != null) addPictures(new File(shared, WALLPAPERS), out);
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                int byName = a.getName().compareToIgnoreCase(b.getName());
                return byName != 0 ? byName : a.getPath().compareTo(b.getPath());
            }
        });
        return out;
    }

    private static void addPictures(File folder, List<File> out) {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                    || n.endsWith(".webp"))) {
                out.add(f);
            }
        }
    }

    File wallpaper() {
        List<File> pool = wallpapers();
        if (!pool.isEmpty()) {
            long now = System.currentTimeMillis();
            long step;
            String mode = get(ROTATE);
            if ("daytime".equals(mode)) {
                int period = period();
                List<File> fit = forPeriod(pool, period);
                if (fit == null) {
                    // Keep Home Settings › Get Another Picture working here too.
                    int n = pool.size();
                    return pool.get(((period + offset()) % n + n) % n);
                }
                if (!fit.isEmpty()) pool = fit;
                mode = "visit";
            }
            switch (mode) {
                case "visit": step = homeVisits(); break;
                case "hourly": step = now / 3_600_000L; break;
                case "daily": step = (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L; break;
                default: step = 0;
            }
            int n = pool.size();
            return pool.get((int) (((step + offset()) % n + n) % n));
        }
        for (String name : new String[] {"wallpaper.jpg", "wallpaper.jpeg", "wallpaper.png", "wallpaper.webp"}) {
            File f = new File(dir, name);
            if (f.isFile()) return f;
            if (shared != null) {
                File s = new File(shared, name);
                if (s.isFile()) return s;
            }
        }
        return null;
    }
}
