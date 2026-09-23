package com.skipstery.projectorcalibrator;

import android.content.Context;
import android.content.SharedPreferences;

final class ProfileStore {
  private final SharedPreferences prefs;

  ProfileStore(Context context) {
    prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE);
  }

  Insets load(String display, Insets initial) {
    String prefix = display + ".";
    if (!prefs.contains(prefix + "width")) {
      save(display, initial);
      return initial;
    }
    try {
      return new Insets(
              prefs.getInt(prefix + "width", initial.width),
              prefs.getInt(prefix + "height", initial.height),
              prefs.getInt(prefix + "left", 0),
              prefs.getInt(prefix + "top", 0),
              prefs.getInt(prefix + "right", 0),
              prefs.getInt(prefix + "bottom", 0))
          .resized(initial.width, initial.height);
    } catch (IllegalArgumentException e) {
      return Insets.full(initial.width, initial.height);
    }
  }

  void save(String display, Insets value) {
    String p = display + ".";
    prefs
        .edit()
        .putInt(p + "width", value.width)
        .putInt(p + "height", value.height)
        .putInt(p + "left", value.left)
        .putInt(p + "top", value.top)
        .putInt(p + "right", value.right)
        .putInt(p + "bottom", value.bottom)
        .apply();
  }
}
