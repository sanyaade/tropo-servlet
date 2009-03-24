package com.voxeo.tropo.core;

import com.voxeo.tropo.FatalException;
import com.voxeo.sipmethod.mrcp.client.MrcpAsrSession;
import com.voxeo.sipmethod.mrcp.client.MrcpTtsSession;

public interface CallImpl extends Call {
  String INST = "com.voxeo.tropo.core.call.inst";

  long getCreatedTime();

  State getState();

  boolean isActive();

  void setState(final State state);

  void lock();

  void unlock();

  void signal(Object message);

  void await(long ms) throws InterruptedException;

  void updateEndpoint(String remoteAddr, int remotePort, String sdp);

  MrcpAsrSession getASR() throws FatalException;

  MrcpTtsSession getTTS() throws FatalException;  

  String getId();
  
}
