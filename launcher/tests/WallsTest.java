package tv.vinay.dumbtv;

/** The saved wallpaper identity has to survive home.conf, which stores one line per key. */
public final class WallsTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        String key = Walls.keyFor("sunset", "off", 0, "off");
        check(key.indexOf('\n') < 0 && key.indexOf('\r') < 0, "key stays on one line");
        check("sunset|off|0|off".equals(key), "key is query, rotation, offset and turn");
        String messy = Walls.keyFor("sea\nbirds", "daily", 3, "d100");
        check(messy.indexOf('\n') < 0, "a newline in the query stays in the value");
        check("sea%0Abirds|daily|3|d100".equals(messy), "newline in a query is encoded");
        System.out.println("WallsTest passed");
    }
}
