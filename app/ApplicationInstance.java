package com.voxeo.tropo.app;

import javax.servlet.sip.SipApplicationSession;

public interface ApplicationInstance extends Runnable {
  String INST = "com.voxeo.tropo.app.inst";

  SipApplicationSession getApplicationSession();
  String getParentSessionId();
  void terminate(); 
  Application getApp();
  void log(Object msg);
  void block(long milliSeconds);
}
