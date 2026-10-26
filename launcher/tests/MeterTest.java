package tv.vinay.dumbtv;

/** Host-side checks for the memory-meter arithmetic, without a device or test framework. */
public final class MeterTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        long mb = 1024L * 1024L;
        check("247 MB free of 903 MB".equals(Meter.label(247 * mb, 903 * mb)), "label in whole MB");
        check("0 MB free of 0 MB".equals(Meter.label(0, 0)), "no numbers yet does not throw");

        check(Math.abs(Meter.used(250 * mb, 1000 * mb) - 0.75f) < 0.001f, "a quarter free is three quarters used");
        check(Meter.used(0, 0) == 0f, "unknown total shows an empty bar");
        check(Meter.used(2000 * mb, 1000 * mb) == 0f, "never below empty");
        check(Meter.used(-1, 1000 * mb) == 1f, "never above full");

        check(Meter.low(true, 500 * mb, 1000 * mb), "Android saying low is low");
        check(Meter.low(false, 100 * mb, 1000 * mb), "under 15% free is low");
        check(!Meter.low(false, 247 * mb, 903 * mb), "27% free is not low");
        check(!Meter.low(false, 0, 0), "unknown total is not low");
        System.out.println("MeterTest passed");
    }
}
