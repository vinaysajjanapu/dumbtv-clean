package tv.vinay.dumbtv;

/** Small host-side checks for the pixel path, without a device or test framework. */
public final class FocusColorTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        int[] transparent = new int[100];
        check(FocusColor.representative(transparent) == FocusColor.FALLBACK, "transparent fallback");

        int[] icon = new int[100];
        for (int i = 0; i < icon.length; i++) icon[i] = i < 75 ? 0xFFFFFFFF : 0xFFFF3020;
        int color = FocusColor.representative(icon);
        int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
        check(red > green * 2 && red > blue * 2, "colored logo beats white backdrop");
        check(red < 200, "bright art darkened");

        int[] blueIcon = new int[100];
        for (int i = 0; i < blueIcon.length; i++) blueIcon[i] = 0xFF2050E0;
        color = FocusColor.representative(blueIcon);
        check((color & 255) > (color >> 16 & 255), "blue stays blue");
        check((FocusColor.tint(color) >>> 24) == 61, "tint is 24 percent opaque");
        check((FocusColor.tint(color) & 0xFFFFFF) == (color & 0xFFFFFF), "tint keeps color");
        check("service".equals(Config.BACKGROUND.fallback), "default background is the wallpaper service");
        check("custom".equals(Config.PROVIDER.fallback), "search query is the wallpaper source");
        check("Adaptive wallpaper tint".equals(Config.BACKGROUND.name("adaptive-tint")),
                "older background names still parse");
        check("Amber".equals(Config.ACCENT.name("amber")), "amber accent unchanged");
        check(Config.BLUE == 0xFF3D8BFF, "blue accent unchanged");
        check("Never".equals(Config.LABELS.name("never")), "app names can be hidden");
        check("Off".equals(Config.LOCK.name("off")), "child lock starts off");
        check("on".equals(Config.AUTO.fallback), "new apps join the home row");
        check("On".equals(Config.AUTO.name("on")) && "Off".equals(Config.AUTO.name("off")),
                "new apps is on or off");
        System.out.println("FocusColorTest passed");
    }
}
