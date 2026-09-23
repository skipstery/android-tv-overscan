package com.skipstery.projectorcalibrator;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;

final class Protocol {
  static final String PACKAGE = "com.skipstery.projectorcalibrator";
  static final String AUTHORITY = PACKAGE + ".bridge";
  static final String DESCRIPTOR = PACKAGE + ".DisplayBridge";
  static final int STATUS = IBinder.FIRST_CALL_TRANSACTION;
  static final int APPLY = STATUS + 1;
  static final int VERSION = 1;

  static void write(Parcel out, Insets value) {
    out.writeInt(value.width);
    out.writeInt(value.height);
    out.writeInt(value.left);
    out.writeInt(value.top);
    out.writeInt(value.right);
    out.writeInt(value.bottom);
  }

  static Insets read(Parcel in) {
    return new Insets(
        in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
  }

  static void write(Bundle out, Insets value) {
    out.putInt("width", value.width);
    out.putInt("height", value.height);
    out.putInt("left", value.left);
    out.putInt("top", value.top);
    out.putInt("right", value.right);
    out.putInt("bottom", value.bottom);
  }

  static Insets read(Bundle in) {
    return new Insets(
        in.getInt("width"),
        in.getInt("height"),
        in.getInt("left"),
        in.getInt("top"),
        in.getInt("right"),
        in.getInt("bottom"));
  }
}
