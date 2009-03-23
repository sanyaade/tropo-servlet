package com.voxeo.tropo.app;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Properties;

import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.sip.ConvergedHttpSession;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipSession;

import org.apache.log4j.Logger;

import com.voxeo.tropo.core.Call;
import com.voxeo.tropo.core.IncomingCall;
import com.voxeo.tropo.core.SimpleCallFactory;
import com.voxeo.tropo.core.SimpleIncomingCall;
import com.voxeo.tropo.util.Utils;
import com.voxeo.sipmethod.mrcp.client.MrcpFactory;

public class SimpleInstance extends AbstractInstance {
  private static final Logger LOG = Logger.getLogger(SimpleInstance.class);

  ScriptContext _context;

  CompiledScript _script;

  SipFactory _sipFactory;

  MrcpFactory _mrcpFactory;

  Application _app;

  static ThreadLocal<ApplicationInstance> _self = new ThreadLocal<ApplicationInstance>();

  public SimpleInstance(final SipServletRequest invite, final CompiledScript script, final Application app) {
    this(invite.getSession().getApplicationSession(), invite, script, app);
    invite.getSession().getApplicationSession().setInvalidateWhenReady(false);
  }

  public SimpleInstance(final HttpServletRequest invite, final CompiledScript script, final Application app) {
    this(((ConvergedHttpSession) invite.getSession()).getApplicationSession(), invite, script, app);
  }

  public SimpleInstance(final SipApplicationSession appSession, final ServletRequest invite,
      final CompiledScript script, final Application app) {
    super(appSession, invite, app);
    _script = script;
    _session.setAttribute(ApplicationInstance.INST, this);
    _sipFactory = app.getManager().getSipFactory();
    _mrcpFactory = app.getManager().getMrcpFactory();
    _app = app;
    _context = new SimpleScriptContext();
    // should already been set setLogContext();

    LOG.info(toString() + " has been created.");

    // set a default null string to facilitate the judge in script layer
    _context.setAttribute("incomingCall", "nullCall", ScriptContext.ENGINE_SCOPE);
    // expose application instance to the script
    _context.setAttribute("appInstance", this, ScriptContext.ENGINE_SCOPE);
    final Properties params = _app.getParameters();
    if (params != null) {
      for (final Object name : params.keySet()) {
        _context.setAttribute((String) name, params.get(name), ScriptContext.ENGINE_SCOPE);
        if (LOG.isDebugEnabled()) {
          LOG.debug(name + ":" + params.get(name) + " is added into the context  of app instance : " + this.toString());
        }
      }
    }
    final SimpleCallFactory cf = new SimpleCallFactory(this);
    _context.setAttribute("callFactory", cf, ScriptContext.ENGINE_SCOPE);
    if (LOG.isDebugEnabled()) {
      LOG.debug(cf.toString() + " is added into the context of app instance : " + this.toString());
    }
  }

  public static ApplicationInstance getCurrentApplicationInstance() {
    return _self.get();
  }

  Field getField(Object o, String name) throws SecurityException {
    try {
      return o.getClass().getDeclaredField(name);
    }
    catch (NoSuchFieldException e) {
      ; //ignore
    }
    return null;
  }

  /**
   * return old ScriptEngine in the CompiledScripte
   * 
   * @param cs
   * @param eng
   * @return
   */
  ScriptEngine replaceScriptEgnine(CompiledScript cs, ScriptEngine eng) {
    if (cs instanceof SimulatedCompiledScript) {
      return eng;
    }
    String fn = "engine";
    ScriptEngine oldEng = cs.getEngine();
    try {
      Field f = getField(cs, fn); // groovy, js
      if (f == null) {
        fn = "_engine";
        f = getField(cs, fn); // php

      }
      if (f == null) {
        fn = "this$0";
        f = getField(cs, fn); // jython, jruby
      }
      
      if (f == null) {
        Field[] fs = cs.getClass().getDeclaredFields();
        StringBuffer buf = new StringBuffer("All available fields in " + cs.getClass().getName() + " are: ");
        for (int i = 0; i < fs.length; i++) {
          if (i == fs.length - 1) {
            buf.append('[').append(fs[i].getName()).append(']');
          }
          else {
            buf.append('[').append(fs[i].getName()).append(']').append(",");
          }
        }
        LOG.error(toString() + " no field with name 'engine' or '_engine' . " + buf.toString());
        return eng;
      }
      f.setAccessible(true);
      f.set(cs, eng);
      f.setAccessible(false);
      if (LOG.isDebugEnabled()) {
        LOG.debug(toString() + " replaced script engine [" + oldEng + "] --> [" + cs.getEngine() + "] in field <" + fn + ">");
      }
      return oldEng;
    }
    catch (SecurityException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(toString() + " error replacing script engine : " + e.getMessage(), e);
      }
    }
    catch (IllegalArgumentException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(toString() + " error replacing script engine : " + e.getMessage(), e);
      }
    }
    catch (IllegalAccessException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(toString() + " error replacing script engine : " + e.getMessage(), e);
      }
    }
    return eng;
  }

  public void run() {
    setLogContext();
    _self.set(this);
    IncomingCall call = null;
    SipApplicationSession appSession = getApplicationSession();
    if (_invite instanceof SipServletRequest) {
      final SimpleCallFactory cf = (SimpleCallFactory) _context.getAttribute("callFactory");
      call = new SimpleIncomingCall(cf, (SipServletRequest) _invite, this);
      _context.setAttribute("incomingCall", call, ScriptContext.ENGINE_SCOPE);
      if (LOG.isDebugEnabled()) {
        LOG.debug(call.toString() + " is added into the context");
      }
    }
    ScriptEngine eng = null;
    ScriptEngine oldEng = null;
    try {
      LOG.info(this + " starts execution.");
      if (_script instanceof SimulatedCompiledScript) {
        _script.eval(_context);
      }
      else {
        eng = ((LocalApplicationManager) getApp().getManager()).getScriptEngine(getApp().getType());
        oldEng = replaceScriptEgnine(_script, eng);
        _script.eval(_context);
      }
      LOG.info(this + " ends execution.");
    }
    catch (final ScriptException e) {
      LOG.error(Utils.buildScriptExceptionMessage(this, "runtime", e), e);
    }
    catch (final SecurityException e) {
      LOG.error(this + " violates the sandbox: " + e.getMessage(), e);
    }
    catch (final Throwable t) {
      LOG.error(this + " has unknown errors: " + t.getMessage(), t);
    }
    finally {
      if (eng != null) {
        if (oldEng != null && eng != oldEng) {
          replaceScriptEgnine(_script, oldEng);
        }
        ((LocalApplicationManager) getApp().getManager()).putScriptEngine(getApp().getType(), eng);
      }
      if (appSession != null && appSession.isValid()) {
        try {
          final Iterator<?> sessions = appSession.getSessions("SIP");
          while (sessions.hasNext()) {
            try {
              final SipSession session = (SipSession) sessions.next();
              if (LOG.isDebugEnabled()) {
                LOG.debug(appSession + " has " + session);
              }
              if (session != null && session.isValid()) {
                final Call c = (Call) session.getAttribute(Call.INST);
                if (c != null) {
                  c.hangup();
                }
              }
            }
            catch (final Throwable t) {
              ;
            }
          }
        }
        catch (final Throwable t) {
          ;
        }
        if (appSession.isReadyToInvalidate()) {
          appSession.invalidate();
        }
        else {
          appSession.setInvalidateWhenReady(true);
        }
      }
      System.gc();
    }
  }

  public void terminate() {

  }


}
