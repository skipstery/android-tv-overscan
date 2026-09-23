package com.skipstery.projectorcalibrator;

public final class Insets {
  public final int width, height, left, top, right, bottom;

  public Insets(int width, int height, int left, int top, int right, int bottom) {
    if (width <= 0
        || height <= 0
        || width > 16384
        || height > 16384
        || left < 0
        || top < 0
        || right < 0
        || bottom < 0
        || (long) left + right > width / 2
        || (long) top + bottom > height / 2) {
      throw new IllegalArgumentException(
          "Margins must leave at least half of each display dimension visible");
    }
    this.width = width;
    this.height = height;
    this.left = left;
    this.top = top;
    this.right = right;
    this.bottom = bottom;
  }

  public int contentWidth() {
    return width - left - right;
  }

  public int contentHeight() {
    return height - top - bottom;
  }

  public int edge(int edge) {
    switch (edge) {
      case 0:
        return left;
      case 1:
        return right;
      case 2:
        return top;
      case 3:
        return bottom;
      default:
        throw new IllegalArgumentException("Unknown edge");
    }
  }

  public Insets adjust(int edge, int delta) {
    int l = left, r = right, t = top, b = bottom;
    switch (edge) {
      case 0:
        l = clamp((long) l + delta, width / 2 - r);
        break;
      case 1:
        r = clamp((long) r + delta, width / 2 - l);
        break;
      case 2:
        t = clamp((long) t + delta, height / 2 - b);
        break;
      case 3:
        b = clamp((long) b + delta, height / 2 - t);
        break;
      default:
        throw new IllegalArgumentException("Unknown edge");
    }
    return new Insets(width, height, l, t, r, b);
  }

  private static int clamp(long value, int max) {
    return (int) Math.max(0, Math.min(max, value));
  }

  public Insets resized(int w, int h) {
    return new Insets(
        w,
        h,
        scale(left, w, width),
        scale(top, h, height),
        scale(right, w, width),
        scale(bottom, h, height));
  }

  private static int scale(int margin, int dimension, int original) {
    return (int) Math.floor((double) margin * dimension / original);
  }

  public static Insets full(int w, int h) {
    return new Insets(w, h, 0, 0, 0, 0);
  }
}
