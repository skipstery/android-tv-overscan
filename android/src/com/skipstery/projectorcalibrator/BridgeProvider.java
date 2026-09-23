package com.skipstery.projectorcalibrator;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;

public final class BridgeProvider extends ContentProvider {
  private final Binder lifetime = new Binder();

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public Bundle call(String method, String arg, Bundle extras) {
    if (Binder.getCallingUid() != 2000)
      throw new SecurityException("Only authorized ADB shell may bootstrap calibration");
    Bundle reply = new Bundle();
    if ("status".equals(method)) {
      boolean ready = false;
      try {
        if (BridgeClient.connected()) {
          BridgeClient.transact(null, true);
          ready = true;
        }
      } catch (Exception ignored) {
      }
      reply.putBoolean("ready", ready);
      reply.putInt("protocol", Protocol.VERSION);
      return reply;
    }
    if (!"attach".equals(method) || extras == null)
      throw new IllegalArgumentException("Unknown bootstrap operation");
    if (extras.getInt("protocol") != Protocol.VERSION)
      throw new IllegalArgumentException("Incompatible bridge version");
    IBinder control = extras.getBinder("control");
    if (control == null || !control.isBinderAlive())
      throw new IllegalArgumentException("Missing control binder");
    String display = extras.getString("display");
    if (display == null || display.length() > 100)
      throw new IllegalArgumentException("Invalid display identifier");
    Insets initial = Protocol.read(extras);
    Insets saved = new ProfileStore(getContext()).load(display, initial);
    BridgeClient.attach(control, display, saved);
    reply.putBoolean("attached", true);
    reply.putInt("app_uid", Process.myUid());
    reply.putBinder("lifetime", lifetime);
    Protocol.write(reply, saved);
    return reply;
  }

  @Override
  public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getType(Uri u) {
    return null;
  }

  @Override
  public Uri insert(Uri u, ContentValues v) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri u, String s, String[] a) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri u, ContentValues v, String s, String[] a) {
    throw new UnsupportedOperationException();
  }
}
