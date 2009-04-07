package com.voxeo.tropo.util;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class ScriptThreadPoolExecutor extends ThreadPoolExecutor {

  public ScriptThreadPoolExecutor(final int coreSize, final int maxSize, final ThreadFactory factory) {
    super(coreSize, maxSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), factory);
  }

  @Override
  protected void beforeExecute(final Thread t, final Runnable r) {
    // TODO setCurrentMDC for script thread scope.
    super.beforeExecute(t, r);
  }

  @Override
  protected void afterExecute(final Runnable r, final Throwable t) {
    Utils.clearLogContext();
    super.afterExecute(r, t);
  }

}
