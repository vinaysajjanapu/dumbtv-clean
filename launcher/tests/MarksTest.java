package tv.vinay.dumbtv;

/** Host-side checks for which apps get a hand-drawn dark banner. */
public final class MarksTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        check(Marks.of("com.amazon.firetv.youtube") == Marks.YOUTUBE, "fire tv youtube");
        check(Marks.of("com.google.android.youtube.tv") == Marks.YOUTUBE, "android tv youtube");
        check(Marks.of("com.netflix.ninja") == Marks.NETFLIX, "netflix");
        check(Marks.of("org.smarttube.stable") == Marks.NONE, "smarttube keeps its own banner");
        check(Marks.of("com.amazon.avod.thirdpartyclient") == Marks.NONE, "other apps keep theirs");
        check(Marks.of(null) == Marks.NONE, "null package");
        System.out.println("MarksTest passed");
    }
}
