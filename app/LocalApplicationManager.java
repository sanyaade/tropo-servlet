package com.voxeo.tropo.app;

import javax.script.ScriptEngine;

public interface LocalApplicationManager extends ApplicationManager {
  ScriptEngine getScriptEngine(String type);
  void putScriptEngine(String type, ScriptEngine eng);
  void execute(ApplicationInstance inst);
}
