package com.skipstery.projectorcalibrator;

import android.graphics.Rect;
import android.os.IBinder;
import java.lang.reflect.Method;

final class Projection {
  private final Class<?> displayControl;
  private final Class<?> surface = Class.forName("android.view.SurfaceControl");
  private final Class<?> transaction = Class.forName("android.view.SurfaceControl$Transaction");
  private final Method setProjection =
      transaction.getMethod(
          "setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class);
  long id;
  int width, height, mode, state;
  private IBinder token;

  Projection() throws Exception {
    String cp = System.getenv("SYSTEMSERVERCLASSPATH");
    if (cp == null) throw new IllegalStateException("System server class path unavailable");
    Class<?> factory = Class.forName("com.android.internal.os.ClassLoaderFactory");
    ClassLoader loader =
        (ClassLoader)
            factory
                .getDeclaredMethod(
                    "createClassLoader",
                    String.class,
                    String.class,
                    String.class,
                    ClassLoader.class,
                    int.class,
                    boolean.class,
                    String.class)
                .invoke(null, cp, null, null, ClassLoader.getSystemClassLoader(), 0, true, null);
    displayControl = loader.loadClass("com.android.server.display.DisplayControl");
    Method load = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
    load.setAccessible(true);
    load.invoke(Runtime.getRuntime(), displayControl, "android_servers");
    refresh();
  }

  boolean refresh() throws Exception {
    long[] ids = (long[]) displayControl.getMethod("getPhysicalDisplayIds").invoke(null);
    if (ids.length != 1)
      throw new IllegalStateException("Exactly one physical display is required");
    long newId = ids[0];
    Object info = surface.getMethod("getDynamicDisplayInfo", long.class).invoke(null, newId);
    if (info == null) throw new IllegalStateException("Display is disconnected");
    int active = info.getClass().getField("activeDisplayModeId").getInt(info);
    int w = 0, h = 0;
    for (Object m : (Object[]) info.getClass().getField("supportedDisplayModes").get(info)) {
      if (m.getClass().getField("id").getInt(m) == active) {
        w = m.getClass().getField("width").getInt(m);
        h = m.getClass().getField("height").getInt(m);
        break;
      }
    }
    if (w <= 0 || h <= 0) throw new IllegalStateException("No active display mode");
    Class<?> global = Class.forName("android.hardware.display.DisplayManagerGlobal");
    Object logical =
        global
            .getMethod("getDisplayInfo", int.class)
            .invoke(global.getMethod("getInstance").invoke(null), 0);
    int logicalW = logical.getClass().getField("logicalWidth").getInt(logical);
    int logicalH = logical.getClass().getField("logicalHeight").getInt(logical);
    if (logicalW != w || logicalH != h)
      throw new IllegalStateException("Reset custom wm size before using calibration");
    int newState = logical.getClass().getField("state").getInt(logical);
    boolean changed =
        id != newId || width != w || height != h || mode != active || state != newState;
    id = newId;
    width = w;
    height = h;
    mode = active;
    state = newState;
    token =
        (IBinder) displayControl.getMethod("getPhysicalDisplayToken", long.class).invoke(null, id);
    if (token == null) throw new IllegalStateException("No physical display token");
    return changed;
  }

  void apply(Insets value, boolean enabled) throws Exception {
    if (value.width != width || value.height != height)
      throw new IllegalArgumentException("Display mode changed; retry after reconnecting");
    Rect source = new Rect(0, 0, width, height);
    Rect target =
        enabled
            ? new Rect(value.left, value.top, width - value.right, height - value.bottom)
            : source;
    Object tx = transaction.getConstructor().newInstance();
    try {
      setProjection.invoke(tx, token, 0, source, target);
      transaction.getMethod("apply").invoke(tx);
    } finally {
      transaction.getMethod("close").invoke(tx);
    }
  }
}
