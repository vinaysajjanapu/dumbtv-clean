package tv.vinay.dumbtv;

import java.util.Arrays;

/** Host-side checks for the greeting and the line under it, without a device. */
public final class QuotesTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        check(Quotes.period(4) == 0 && Quotes.period(21) == 0, "night wraps midnight");
        check(Quotes.period(5) == 1 && Quotes.period(11) == 1, "morning");
        check(Quotes.period(12) == 2 && Quotes.period(16) == 2, "afternoon");
        check(Quotes.period(17) == 3 && Quotes.period(20) == 3, "evening");

        check("Good Morning".equals(Quotes.hello(9, "  ")), "blank name is just the greeting");
        check("Good Evening, Ada".equals(Quotes.hello(19, " Ada ")), "name joins the greeting");
        check("Good Morning, Ada".equals(Quotes.greeting(9, "Ada", Quotes.SALUTATION, "Hi")),
                "time of day ignores custom text");
        check("Hi".equals(Quotes.greeting(9, "Ada", Quotes.CUSTOM, " Hi ")),
                "custom greeting replaces the greeting and the name");
        check("Good Night, Ada".equals(Quotes.greeting(23, "Ada", Quotes.CUSTOM, "  ")),
                "blank custom greeting falls back to the time of day");
        check("Good Afternoon".equals(Quotes.greeting(14, null, "nope", null)),
                "unknown greeting mode follows the time of day");

        check(Quotes.byPeriod(Quotes.CALM) && Quotes.byPeriod(null), "time of day changes through the day");
        String night = Quotes.line(Quotes.CALM, 7, 0, 23);
        boolean nightInMorning = false;
        for (int i = 0; i < Quotes.count(Quotes.CALM, 9); i++) {
            if (night.equals(Quotes.line(Quotes.CALM, 7, i, 9))) nightInMorning = true;
        }
        check(!nightInMorning, "a night line stays in the night list");

        String[] ids = {Quotes.CALM, Quotes.MOTIVATE, Quotes.FUNNY, Quotes.TECH, Quotes.ROAST};
        check(Arrays.equals(ids, Config.WELCOME.values), "welcome categories: " + Arrays.toString(Config.WELCOME.values));
        check(Quotes.CALM.equals(Config.WELCOME.fallback), "the line starts as time of day");
        check("Time of Day".equals(Config.WELCOME.name(Quotes.CALM)), "time of day label");
        check(!Config.WELCOME_LINE, "the welcome line stays off the greeting page for now");
        check(Config.welcomeOn("on") && !Config.welcomeOn("off") && !Config.welcomeOn(null),
                "the welcome line is on only when asked");
        check("welcome.show".equals(Config.WELCOME_SHOW), "welcome line switch");
        check("time".equals(Config.GREETING.fallback), "greeting starts as time of day");
        check("Time of Day".equals(Config.GREETING.name("time")), "time of day greeting label");
        check("Custom".equals(Config.GREETING.name("custom")), "custom greeting label");

        for (String id : Config.WELCOME.values) {
            cycle(id, Quotes.CALM.equals(id) ? 9 : 0);
            if (Quotes.CALM.equals(id)) continue;
            check(!Quotes.byPeriod(id), id + " is a single list");
            check(Quotes.line(id, 5, 1, 3).equals(Quotes.line(id, 5, 1, 16)), id + " ignores the hour");
        }
        check(Quotes.line("nope", 5, 2, 9).equals(Quotes.line(Quotes.CALM, 5, 2, 9)),
                "an unknown category uses time of day");

        check("Stay".equals(Quotes.welcome(Quotes.FUNNY, " Stay ", 1, 0, 9)), "custom line wins");
        check(Quotes.welcome(Quotes.FUNNY, "  ", 1, 0, 9).equals(Quotes.line(Quotes.FUNNY, 1, 0, 9)),
                "a blank custom line uses the category");
        check(!Quotes.written(null) && !Quotes.written("  "), "blank is not an override");

        int n = Quotes.count(Quotes.ROAST, 0);
        int avoid = Quotes.pick(Quotes.ROAST, 42, n - 1, 0);
        long next = Quotes.nextSeed(Quotes.ROAST, 42, avoid, 0);
        check(Quotes.pick(Quotes.ROAST, next, 0, 0) != avoid, "the next cycle starts on a different line");
        System.out.println("QuotesTest passed");
    }

    /** Every line in the list appears once per cycle. */
    private static void cycle(String category, int hour) {
        int n = Quotes.count(category, hour);
        check(n >= 4, category + " has several lines");
        boolean[] seen = new boolean[n];
        long seed = 99;
        for (int step = 0; step < n; step++) {
            int pick = Quotes.pick(category, seed, step, hour);
            String line = Quotes.line(category, seed, step, hour);
            check(pick >= 0 && pick < n && !seen[pick], category + " repeats an index");
            check(line != null && line.length() > 8, category + " line is too short");
            seen[pick] = true;
        }
    }
}
