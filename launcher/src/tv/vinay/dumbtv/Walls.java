package tv.vinay.dumbtv;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

/**
 * One picture at a time from Wallhaven. General and safe only, at least 1920 wide,
 * so the TV gets a clean full-screen image. The home screen always uses this.
 * The image URL is saved, and a restart shows that same picture. Another is fetched
 * when Get Another Picture is used, the search changes, or rotation reaches its next turn.
 */
final class Walls {
    private static final int MAX_BYTES = 12 * 1024 * 1024;
    /** Shown and searched when Search Query has never been set. A saved search is left alone. */
    static final String DEFAULT_QUERY = "abstract dark";
    /** Turn this picture belongs to: search, rotation, offset and turn, on one line. */
    static final String KEY = "service.key";
    /** Wallhaven image URL of the picture in {@link #file}. */
    static final String URL = "service.url";

    enum Need { KEEP, REFILL, SEARCH }

    private Walls() {}

    static boolean on(Config cfg) {
        return cfg != null;
    }

    /** What to ask for. A blank search uses {@link #DEFAULT_QUERY}. */
    static String query(Config cfg) {
        String q = cfg.get("provider.name", "").trim();
        return q.isEmpty() ? DEFAULT_QUERY : q;
    }

    static File file(Config cfg) {
        return new File(cfg.dir, "service-wall");
    }

    static File shown(Config cfg) {
        File f = file(cfg);
        return f.isFile() && f.length() > 0 ? f : null;
    }

    /**
     * KEEP when the file on disk is still the right picture. REFILL when that picture's
     * URL should be downloaded again. SEARCH when this turn needs a different picture.
     */
    static Need need(Config cfg) {
        if (!on(cfg) || query(cfg).isEmpty()) return Need.KEEP;
        claim(cfg);
        boolean same = key(cfg).equals(cfg.get(KEY, ""));
        if (same && shown(cfg) != null) return Need.KEEP;
        if (same && cfg.get(URL, "").startsWith("https://")) return Need.REFILL;
        return Need.SEARCH;
    }

    /**
     * Older builds stored this key with line breaks, so home.conf kept only the search
     * query. The picture already on disk still belongs to that search.
     */
    private static void claim(Config cfg) {
        if (shown(cfg) == null) return;
        String saved = cfg.get(KEY, "");
        if (saved.equals(key(cfg)) || !saved.equals(query(cfg))) return;
        cfg.set(KEY, key(cfg));
    }

    static String key(Config cfg) {
        return keyFor(query(cfg), cfg.get(Config.ROTATE), cfg.wallpaperOffset(), turn(cfg));
    }

    /** One line, so home.conf can store it. A line break in the query is encoded. */
    static String keyFor(String query, String rotate, int offset, String turn) {
        return url(query) + "|" + rotate + "|" + offset + "|" + turn;
    }

    private static String turn(Config cfg) {
        String mode = cfg.get(Config.ROTATE);
        long now = System.currentTimeMillis();
        if ("visit".equals(mode)) return "v" + cfg.homeVisits();
        if ("hourly".equals(mode)) return "h" + now / 3_600_000L;
        if ("daily".equals(mode)) return "d" + (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L;
        if ("daytime".equals(mode)) return "p" + Config.period();
        return "off";
    }

    /**
     * Downloads a matching picture over {@code dest}. Returns its URL, or null on failure.
     * Leaves {@code dest} alone when nothing was saved.
     */
    static String fetch(String query, File dest) {
        String page = get(search(query), 1024 * 1024);
        String image = page == null ? null : pick(page);
        return image != null && store(image, dest) ? image : null;
    }

    /** Downloads a known image URL over {@code dest}. Leaves {@code dest} alone on failure. */
    static boolean store(String image, File dest) {
        File tmp = new File(dest.getParentFile(), dest.getName() + ".tmp");
        try {
            if (!download(image, tmp)) return false;
            if (dest.exists() && !dest.delete()) Log.w("dumbtv", "could not replace " + dest);
            if (!tmp.renameTo(dest)) {
                Log.w("dumbtv", "could not move " + tmp);
                return false;
            }
            return true;
        } finally {
            if (tmp.exists() && !tmp.delete()) Log.w("dumbtv", "could not remove " + tmp);
        }
    }

    private static String search(String query) {
        String seed = Integer.toString(new Random().nextInt(Integer.MAX_VALUE), 36);
        return "https://wallhaven.cc/api/v1/search?q=" + url(query)
                + "&categories=100&purity=100&atleast=1920x1080&ratios=16x9&sorting=random&seed="
                + url(seed);
    }

    private static String pick(String page) {
        try {
            JSONArray data = new JSONObject(page).optJSONArray("data");
            if (data == null || data.length() == 0) return null;
            List<String> paths = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                String path = data.getJSONObject(i).optString("path", "");
                if (path.startsWith("https://")) paths.add(path);
            }
            if (paths.isEmpty()) return null;
            return paths.get(new Random().nextInt(paths.size()));
        } catch (org.json.JSONException e) {
            Log.w("dumbtv", "wallpaper search was not readable", e);
            return null;
        }
    }

    private static boolean download(String image, File tmp) {
        byte[] body = read(image, MAX_BYTES);
        if (body == null || body.length < 32) return false;
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(body);
            return true;
        } catch (java.io.IOException e) {
            Log.w("dumbtv", "could not save " + tmp, e);
            return false;
        }
    }

    private static String get(String address, int max) {
        byte[] body = read(address, max);
        return body == null ? null : new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] read(String address, int max) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(20_000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "image/jpeg,image/png,image/webp,application/json");
            conn.setRequestProperty("User-Agent", "dumbtv-home");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream in = conn.getInputStream()) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n, total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > max) return null;
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } catch (java.io.IOException e) {
            Log.w("dumbtv", "could not reach the wallpaper service", e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String url(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return "";
        }
    }
}
