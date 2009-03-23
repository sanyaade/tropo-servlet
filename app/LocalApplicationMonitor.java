package com.voxeo.tropo.app;

public interface LocalApplicationMonitor extends ApplicationMonitor {
  void setLoopDectionTime(int x);
  int getLoopDectionTime();
  ScriptEnginePool getScriptEnginePool();

}
