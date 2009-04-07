package com.voxeo.tropo.app;

import javax.servlet.ServletRequest;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;

import org.apache.log4j.Logger;

import com.voxeo.tropo.util.Utils;

public abstract class AbstractInstance implements ApplicationInstance {
  private static final Logger LOG = Logger.getLogger(AbstractInstance.class);
  
  protected Application _app;
  
  protected ServletRequest _invite;
  
  protected SipApplicationSession _session;

  protected String _sessionId; // _sessionId == _session.getId();
  
  protected String _callId = "-1";

  protected String _parentId; // _parentId == SBC x-id


  public AbstractInstance(final SipApplicationSession appSession, final ServletRequest invite, final Application app) {
    _session = appSession;
    _sessionId = appSession.getId();
    _invite = invite;
    _app = app;
    if (_invite instanceof SipServletRequest) {
      _parentId = ((SipServletRequest) invite).getHeader("x-sid");
      _callId = ((SipServletRequest) invite).getCallId();
    }
  }
  
  public Application getApp() {
    return _app;
  }

  public SipApplicationSession getApplicationSession() {
    return _session;
  }

  public String getParentSessionId() {
    return _parentId;
  }

  public void log(final Object msg) {
    if (msg instanceof Throwable) {
      final Throwable t = (Throwable) msg;
      LOG.error(this + " : " + t.getMessage(), t);
    }
    else {
      LOG.info(this + " : " + msg);
    }
  }

  public synchronized void block(final long milliSeconds) {
    final long begin = System.currentTimeMillis();
    long time = milliSeconds;
    while (time > 0) {
      try {
        wait(time);
      }
      catch (final InterruptedException e) {
        ;// ignore
      }
      time = milliSeconds - (System.currentTimeMillis() - begin);
    }
  }

  public void setLogContext() {
    Utils.setLogContext(this, _callId);
  }

  @Override
  public String toString() {
    return "ApplicationInstance[" + getApp().getURL() + ", " + _sessionId + "]";
  }

}
