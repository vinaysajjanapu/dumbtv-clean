package tv.vinay.dumbtv;

import java.util.Random;

/**
 * The greeting and the one line under it. Both stay on the TV: the app does not fetch quotes.
 * Time of day keeps a separate list for each part of the day. The other categories are one list
 * each. Those lists are placeholders and can grow without changing how a line is chosen.
 */
final class Quotes {
    static final String SALUTATION = "time";
    static final String CUSTOM = "custom";

    static final String CALM = "calm";
    static final String MOTIVATE = "motivate";
    static final String FUNNY = "funny";
    static final String TECH = "tech";
    static final String ROAST = "roast";

    /** Night, morning, afternoon, evening. A line from one part of the day is never used in another. */
    private static final String[][] CALM_LINES = {
        {
            "The quietest hours can hold the best stories.",
            "Let the day close softly.",
            "Nothing more is asked of you tonight.",
            "Rest is part of the work.",
            "A familiar story is good company.",
            "The world can wait until morning.",
            "Give the day a gentle ending.",
            "Choose something calm and let yourself unwind.",
            "A little peace is never wasted.",
            "Some stories are best told after dark.",
            "Slow down. There is nowhere else to be.",
            "Let this be the last easy thing today.",
        },
        {
            "A calm beginning can shape the whole day.",
            "Start with something that lifts your spirits.",
            "Today still has room for surprise.",
            "Curiosity is a good compass.",
            "Begin with what feels inviting.",
            "There is always more to notice.",
            "Make time for something that makes you smile.",
            "A fresh day deserves a fresh story.",
            "Let the morning begin at its own pace.",
            "Small joys are a good way to start.",
            "Choose the thing that makes you curious.",
            "Give yourself something to look forward to.",
        },
        {
            "Take a breath. The day can wait a while.",
            "A good pause can change the whole day.",
            "Make a little room for wonder.",
            "Let this be the easy part of the afternoon.",
            "A change of scene can change your mind.",
            "You have time for one good thing.",
            "The ordinary can still surprise you.",
            "Give the moment your full attention.",
            "Come back to what makes you laugh.",
            "A little beauty is never wasted.",
            "Pause here before the day moves on.",
            "Leave a little room for surprise.",
        },
        {
            "Let the day end gently.",
            "Your attention is yours again.",
            "Choose what deserves your evening.",
            "Good stories make the room feel bigger.",
            "Watch something worth remembering.",
            "Comfort is a perfectly good choice.",
            "Some evenings are meant to be quiet.",
            "Tonight does not need to be productive.",
            "Let the outside world wait a while.",
            "Laughter is a fine use of an evening.",
            "Make tonight feel like your own.",
            "Settle in. The rest of the world can wait.",
        },
    };

    private static final String[] MOTIVATE_LINES = {
        "Start with one thing you care about.",
        "Small steps still move the day.",
        "A little progress is still progress.",
        "Choose the next right thing.",
        "Make room for what matters.",
        "You have already begun.",
        "Keep the part of the day that is yours.",
        "Today can be lighter than yesterday.",
    };

    private static final String[] FUNNY_LINES = {
        "The remote was right there the whole time.",
        "One more episode is a kind of plan.",
        "The couch has accepted you.",
        "Decisions get easier after the credits.",
        "Snacks are a fine agenda.",
        "Nothing is buffering. That is the whole trick.",
        "You came here to watch something. This counts.",
        "The algorithm cannot reach you here.",
    };

    private static final String[] TECH_LINES = {
        "It works on this television.",
        "Turn the day off and on again.",
        "There are no trackers in this sentence.",
        "Compiled locally. Even this joke.",
        "No account required to read this.",
        "Still deciding is not a crash.",
        "Shipped without a subscription.",
        "The bug was the third episode.",
    };

    private static final String[] ROAST_LINES = {
        "You opened this to avoid choosing.",
        "The apps are ready. You are still looking.",
        "Still scrolling for a feeling.",
        "You already know what you will pick.",
        "The remote is not going to decide.",
        "This is the third time you have looked.",
        "You could have pressed OK by now.",
        "Indecision looks good in this light.",
    };

    private static long cachedSeed;
    private static int cachedSize;
    private static int[] cached;

    private Quotes() {}

    /** 0 night, 1 morning, 2 afternoon, 3 evening. */
    static int period(int hour) {
        if (hour < 5 || hour >= 21) return 0;
        if (hour < 12) return 1;
        if (hour < 17) return 2;
        return 3;
    }

    /** Time of day is four lists. Every other category, and only those, is a single list. */
    static boolean byPeriod(String category) {
        return !MOTIVATE.equals(category) && !FUNNY.equals(category)
                && !TECH.equals(category) && !ROAST.equals(category);
    }

    static int period(String category, int hour) {
        return byPeriod(category) ? period(hour) : 0;
    }

    static int count(String category, int hour) {
        return lines(category, hour).length;
    }

    static String hello(int hour, String name) {
        String hello = new String[] {
            "Good Night", "Good Morning", "Good Afternoon", "Good Evening"
        }[period(hour)];
        name = clean(name);
        return name.isEmpty() ? hello : hello + ", " + name;
    }

    /**
     * The small line. A custom greeting replaces the salutation entirely, including the name.
     * Blank custom text keeps the salutation, so the line never disappears.
     */
    static String greeting(int hour, String name, String mode, String custom) {
        if (CUSTOM.equals(mode) && written(custom)) return clean(custom);
        return hello(hour, name);
    }

    /** The large line. Written text replaces the category until it is cleared. */
    static String welcome(String category, String custom, long seed, int step, int hour) {
        if (written(custom)) return clean(custom);
        return line(category, seed, step, hour);
    }

    static boolean written(String text) {
        return !clean(text).isEmpty();
    }

    /** The line at {@code step} of the cycle shuffled by {@code seed}. */
    static String line(String category, long seed, int step, int hour) {
        String[] list = lines(category, hour);
        return list[pick(category, seed, step, hour)];
    }

    /** Which line sits at {@code step} in this category's cycle. */
    static int pick(String category, long seed, int step, int hour) {
        int[] order = order(seed, count(category, hour));
        return order[Math.floorMod(step, order.length)];
    }

    /**
     * The seed of the following cycle. Its first line is not {@code avoid}, so the line that
     * closed one cycle does not open the next.
     */
    static long nextSeed(String category, long seed, int avoid, int hour) {
        long next = seed;
        for (int i = 0; i < 16; i++) {
            next = next * 6364136223846793005L + 1L;
            if (next == 0) next = 1;
            if (pick(category, next, 0, hour) != avoid) return next;
        }
        return next;
    }

    private static String[] lines(String category, int hour) {
        if (MOTIVATE.equals(category)) return MOTIVATE_LINES;
        if (FUNNY.equals(category)) return FUNNY_LINES;
        if (TECH.equals(category)) return TECH_LINES;
        if (ROAST.equals(category)) return ROAST_LINES;
        return CALM_LINES[period(hour)];
    }

    /** The shuffle depends only on the seed and the length, so the same cycle can be rebuilt. */
    private static int[] order(long seed, int n) {
        if (cached != null && cachedSeed == seed && cachedSize == n) return cached;
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        Random random = new Random(seed);
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        cachedSeed = seed;
        cachedSize = n;
        cached = a;
        return a;
    }

    private static String clean(String name) {
        return name == null ? "" : name.trim();
    }
}
