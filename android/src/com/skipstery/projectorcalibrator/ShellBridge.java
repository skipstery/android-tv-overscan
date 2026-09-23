package com.skipstery.projectorcalibrator;

import android.content.AttributionSource;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShellBridge extends Binder {
  private final Projection projection;
  private final int user;
  private volatile int appUid = -1;
  private Insets current;
  private boolean enabled = true;
  private boolean needsReapply = false;
  private long attachedDisplay;
  private Object activityManager;
  private Class<?> activityInterface;
  private Object provider;
  private IBinder owner;
  private final Binder providerToken = new Binder();

  private ShellBridge(int user) throws Exception {
    this.user = user;
    projection = new Projection();
    current = readCurrentProjection();
  }

  public static void main(String[] args) {
    try {
      if (Process.myUid() != 2000)
        throw new SecurityException("Start this helper using authorized ADB shell");
      Looper.prepareMainLooper();
      ShellBridge bridge = new ShellBridge(args.length == 0 ? 0 : Integer.parseInt(args[0]));
      bridge.attach();
      bridge.watch();
      System.out.println("READY Projector Calibrator bridge");
      System.out.flush();
      Looper.loop();
    } catch (Throwable error) {
      error.printStackTrace();
      System.exit(1);
    }
    System.exit(0);
  }

  private Insets readCurrentProjection() {
    try {
      java.lang.Process command =
          new ProcessBuilder("/system/bin/dumpsys", "SurfaceFlinger").start();
      Pattern pattern =
          Pattern.compile(
              "orientedDisplaySpace=ProjectionSpace\\{bounds=Rect\\(0, 0, (\\d+), (\\d+)\\),"
                  + " content=Rect\\((\\d+), (\\d+), (\\d+), (\\d+)\\)");
      Insets imported = null;
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(command.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          Matcher m = pattern.matcher(line);
          if (m.find()
              && Integer.parseInt(m.group(1)) == projection.width
              && Integer.parseInt(m.group(2)) == projection.height) {
            imported =
                new Insets(
                    projection.width,
                    projection.height,
                    Integer.parseInt(m.group(3)),
                    Integer.parseInt(m.group(4)),
                    projection.width - Integer.parseInt(m.group(5)),
                    projection.height - Integer.parseInt(m.group(6)));
          }
        }
      }
      if (command.waitFor() == 0 && imported != null) return imported;
    } catch (Exception ignored) {
    }
    return Insets.full(projection.width, projection.height);
  }

  private synchronized void attach() throws Exception {
    if (provider == null) {
      activityInterface = Class.forName("android.app.IActivityManager");
      activityManager =
          Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
      Object holder =
          activityInterface
              .getMethod(
                  "getContentProviderExternal",
                  String.class,
                  int.class,
                  IBinder.class,
                  String.class)
              .invoke(activityManager, Protocol.AUTHORITY, user, providerToken, Protocol.AUTHORITY);
      if (holder == null) throw new IllegalStateException("Install the TV application first");
      provider = holder.getClass().getField("provider").get(holder);
    }
    Bundle request = new Bundle();
    request.putBinder("control", this);
    request.putInt("protocol", Protocol.VERSION);
    Insets initial =
        attachedDisplay != 0 && attachedDisplay != projection.id
            ? Insets.full(projection.width, projection.height)
            : current.resized(projection.width, projection.height);
    request.putString("display", Long.toString(projection.id));
    Protocol.write(request, initial);
    Bundle reply =
        (Bundle)
            Class.forName("android.content.IContentProvider")
                .getMethod(
                    "call",
                    AttributionSource.class,
                    String.class,
                    String.class,
                    String.class,
                    Bundle.class)
                .invoke(
                    provider,
                    new AttributionSource.Builder(Process.myUid())
                        .setPackageName("com.android.shell")
                        .build(),
                    Protocol.AUTHORITY,
                    "attach",
                    null,
                    request);
    if (reply == null || !reply.getBoolean("attached"))
      throw new IllegalStateException("TV application rejected helper");
    appUid = reply.getInt("app_uid", -1);
    if (appUid < 10000) throw new SecurityException("Unexpected application UID");
    if (owner == null) {
      owner = reply.getBinder("lifetime");
      if (owner == null) throw new IllegalStateException("Missing application lifetime binder");
      owner.linkToDeath(() -> System.exit(0), 0);
    }
    current = Protocol.read(reply);
    projection.apply(current, enabled);
    attachedDisplay = projection.id;
  }

  @Override
  protected synchronized boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
    if (Binder.getCallingUid() != appUid)
      throw new SecurityException("Calibration control is private to its TV application");
    data.enforceInterface(Protocol.DESCRIPTOR);
    long identity = Binder.clearCallingIdentity();
    try {
      if (code == Protocol.APPLY) {
        Insets value = Protocol.read(data);
        boolean active = data.readInt() != 0;
        if (data.dataAvail() != 0) throw new IllegalArgumentException("Unexpected request data");
        if (projection.refresh()) needsReapply = true;
        projection.apply(value, active);
        current = value;
        enabled = active;
        needsReapply = false;
      } else if (code != Protocol.STATUS) return false;
      reply.writeNoException();
      Protocol.write(reply, current);
      return true;
    } catch (Exception error) {
      reply.writeException(new IllegalStateException(error.getMessage()));
      return true;
    } finally {
      Binder.restoreCallingIdentity(identity);
    }
  }

  private void watch() {
    Handler handler = new Handler(Looper.getMainLooper());
    handler.postDelayed(
        new Runnable() {
          @Override
          public void run() {
            try {
              synchronized (ShellBridge.this) {
                if (projection.refresh()) needsReapply = true;
                if (needsReapply) {
                  if (attachedDisplay != projection.id
                      || current.width != projection.width
                      || current.height != projection.height) {
                    attach();
                  } else projection.apply(current, enabled);
                  needsReapply = false;
                }
              }
            } catch (Exception e) {
              needsReapply = true;
              System.err.println("Waiting for display: " + e.getMessage());
            }
            handler.postDelayed(this, 2000);
          }
        },
        2000);
  }
}
