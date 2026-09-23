import com.skipstery.projectorcalibrator.Insets;

public final class InsetsTest {
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    Insets calibrated = new Insets(1920, 1080, 40, 31, 40, 32);
    check(
        calibrated.contentWidth() == 1840 && calibrated.contentHeight() == 1017,
        "Measured projector calibration");
    check(
        calibrated.adjust(0, -100).left == 0, "Margins cannot expand outside the physical screen");
    check(
        calibrated.adjust(0, Integer.MAX_VALUE).left == 920,
        "Large remote increments cannot hide the interface");
    check(
        calibrated.adjust(3, Integer.MIN_VALUE).bottom == 0,
        "Large negative increments do not overflow");
    Insets fourK = calibrated.resized(3840, 2160);
    check(
        fourK.left == 80 && fourK.top == 62 && fourK.bottom == 64,
        "HDMI mode changes preserve relative margins");
    boolean rejected = false;
    try {
      new Insets(1920, 1080, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 0);
    } catch (IllegalArgumentException e) {
      rejected = true;
    }
    check(rejected, "Untrusted values must be validated before native compositor calls");
    Insets value = calibrated;
    java.util.Random random = new java.util.Random(7);
    for (int i = 0; i < 10000; i++) {
      value = value.adjust(random.nextInt(4), random.nextInt());
      check(
          value.contentWidth() >= 960 && value.contentHeight() >= 540,
          "The remote must always leave a usable picture");
    }
    System.out.println("Insets checks passed");
  }
}
