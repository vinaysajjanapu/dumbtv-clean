package tv.vinay.dumbtv;

/**
 * Owner-supplied banners for apps whose own tiles are a solid bright colour.
 * The pictures are the owner's images in {@code res/drawable-nodpi/tile_*.jpg},
 * centre-cropped to 16:9; {@link Tile} centre-crops again at draw time so they
 * fill the card without stretching. Add a package here, and a drawable if it
 * needs its own picture, when another app should get the same treatment.
 */
final class Marks {
    static final int NONE = 0;
    static final int YOUTUBE = 1;
    static final int NETFLIX = 2;

    private Marks() {}

    /** Which custom banner to draw, or {@link #NONE} to keep the app's own art. */
    static int of(String packageName) {
        if (packageName == null) return NONE;
        switch (packageName) {
            case "com.amazon.firetv.youtube":
            case "com.google.android.youtube.tv":
                return YOUTUBE;
            case "com.netflix.ninja":
                return NETFLIX;
            default:
                return NONE;
        }
    }
}
