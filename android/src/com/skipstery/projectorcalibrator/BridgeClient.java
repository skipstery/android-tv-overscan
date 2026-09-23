package com.skipstery.projectorcalibrator;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

final class BridgeClient {
  private static volatile IBinder bridge;
  static volatile String display = "";
  static volatile Insets current = Insets.full(1920, 1080);

  static void attach(IBinder binder, String identifier, Insets value) {
    bridge = binder;
    display = identifier;
    current = value;
    try {
      binder.linkToDeath(
          () -> {
            if (bridge == binder) bridge = null;
          },
          0);
    } catch (RemoteException e) {
      bridge = null;
    }
  }

  static boolean connected() {
    IBinder b = bridge;
    return b != null && b.isBinderAlive();
  }

  static Insets transact(Insets value, boolean enabled) throws RemoteException {
    IBinder b = bridge;
    if (b == null) throw new RemoteException("Start the utility on your computer");
    Parcel request = Parcel.obtain(), reply = Parcel.obtain();
    try {
      request.writeInterfaceToken(Protocol.DESCRIPTOR);
      if (value != null) {
        Protocol.write(request, value);
        request.writeInt(enabled ? 1 : 0);
      }
      if (!b.transact(value == null ? Protocol.STATUS : Protocol.APPLY, request, reply, 0))
        throw new RemoteException("Bridge operation unavailable");
      reply.readException();
      Insets actual = Protocol.read(reply);
      current = actual;
      return actual;
    } finally {
      request.recycle();
      reply.recycle();
    }
  }
}
