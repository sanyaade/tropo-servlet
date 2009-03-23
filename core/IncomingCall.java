package com.voxeo.tropo.core;

public interface IncomingCall extends Call {
  void answer(int timeout);

  void reject();

  void redirect(String number);
}
