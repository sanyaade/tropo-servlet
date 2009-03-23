package com.voxeo.tropo.app;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.log4j.Logger;

public class SimulatedCompiledScript extends CompiledScript {
  private static final Logger LOG = Logger.getLogger(SimulatedCompiledScript.class);
  LocalApplicationManager _mgr;
  String _source;
  String _type;

  public SimulatedCompiledScript(LocalApplicationManager mgr, String source, String type) {
    super();
    _mgr = mgr;
    _type = type;
    _source = source;
  }

  @Override
  public Object eval() throws ScriptException {
    ScriptEngine eng = _mgr.getScriptEngine(_type);
    try {
      return eng.eval(_source);
    }
    finally {
      _mgr.putScriptEngine(_type, eng);
    }
  }

  @Override
  public Object eval(Bindings bindings) throws ScriptException {
    // wait at most 10s to try to get a valid script engine
    ScriptEngine eng = _mgr.getScriptEngine(_type);
    if (eng == null) {
      throw new RuntimeException("Can not execute " + this + " because engine pool is exhausted or the application type is not supported.");
    }
    try {
      return eng.eval(_source, bindings);
    }
    finally {
      // make sure to return the engine when done
      _mgr.putScriptEngine(_type, eng);
    }
  }

  @Override
  public Object eval(ScriptContext context) throws ScriptException {
    // wait at most 10s to try to get a valid script engine
    ScriptEngine eng = _mgr.getScriptEngine(_type);
    if (eng == null) {
      throw new RuntimeException("Can not execute " + this + " because engine pool is exhausted or the application type is not supported.");
    }
    try {
      return eng.eval(_source, context);
    }
    finally {
      // make sure to return the engine when done
      _mgr.putScriptEngine(_type, eng);
    }
  }

  @Override
  public ScriptEngine getEngine() {
    throw new RuntimeException(this.getClass().getName() + " does not support getEngine() method.");
  }

}
