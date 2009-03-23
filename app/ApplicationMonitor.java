package com.voxeo.tropo.app;

import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.util.Collection;
import java.util.Date;

public interface ApplicationMonitor {
  /**
   * @param x loop detection runs in every x mins. disabled if -1;
   */
  ThreadInfo[] getDeadLocks();
  MemoryUsage getMemoryUsage();
  long getTotalCalls();
  long getActiveCalls();
  Date getSystemStartTime();
  void incCallCounter();
  void decCallCounter();
  boolean isMrcpServerConnected();
  Collection<String> listTropoLogNames();
  Collection<String> listSIPMethodLogNames();  
}
