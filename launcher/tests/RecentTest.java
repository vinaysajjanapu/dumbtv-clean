package tv.vinay.dumbtv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Host-side checks for last-used ordering, without a device or test framework. */
public final class RecentTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        Map<String, Long> times = new HashMap<>();
        times.put("plex", 100L);
        times.put("smarttube", 300L);
        times.put("spotify", 200L);

        // Already in the user's order, as Catalog.visible sorts it first.
        List<String> apps = new ArrayList<>(Arrays.asList("prime", "plex", "netflix", "smarttube", "spotify"));
        Collections.sort(apps, (a, b) -> Recent.compare(times, a, b));
        check(apps.equals(Arrays.asList("smarttube", "spotify", "plex", "prime", "netflix")),
                "newest first, never-used keep the user's order: " + apps);

        List<String> none = new ArrayList<>(Arrays.asList("b", "a", "c"));
        Collections.sort(none, (a, b) -> Recent.compare(new HashMap<>(), a, b));
        check(none.equals(Arrays.asList("b", "a", "c")), "no history leaves the order alone");
        check("custom".equals(Config.ORDER.fallback), "default order is the saved order");
        check("Alphabetical".equals(Config.ORDER.name("alpha")), "alphabetical is an order");
        check("Recently Used".equals(Config.ORDER.name("recent")), "recent is an order");
        System.out.println("RecentTest passed");
    }
}
