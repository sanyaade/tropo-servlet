package com.voxeo.tropo.core;

import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServletRequest;

import org.apache.log4j.Logger;

import com.voxeo.tropo.ErrorException;
import com.voxeo.tropo.app.Application;
import com.voxeo.tropo.app.ApplicationInstance;
import com.voxeo.tropo.util.MrcpRTCCallback;
import com.voxeo.tropo.util.MrcpRTCListener;
import com.voxeo.tropo.util.Utils;

public class SimpleCallFactory implements CallFactory {

  private static final Logger LOG = Logger.getLogger(SimpleCallFactory.class);

  protected SipApplicationSession _session;
  
  protected ApplicationInstance _inst;
  
  protected Application _app;

  protected SipFactory _sipFactory;


  public SimpleCallFactory(final ApplicationInstance inst) {
    _inst = inst;
    _app = inst.getApp();
    _session = inst.getApplicationSession();
    _sipFactory = _app.getManager().getSipFactory();
  }
  
  public SimpleCallFactory(final Application app, final SipApplicationSession session) {
    _session = session;
    _sipFactory = app.getManager().getSipFactory();
    _app = app;
  }

  public SimpleOutgoingCall call(final String from, final String to, final boolean answerOnMedia, final int timeout, final String callRecordUri, final String callRecordFormat) {
    return call(null, from, to, answerOnMedia, timeout, null, callRecordUri, callRecordFormat);
  }

  public SimpleOutgoingCall call(final SipServletRequest origReq, final String from, final String to,
      final boolean answerOnMedia, final int timeout, final MrcpRTCListener listener, final String callRecordUri, final String callRecordFormat) {
    final SimpleOutgoingCall call;
    String caller = Utils.processFrom(from, origReq == null ? null : origReq.getLocalAddr());
    if ((caller == null || caller.length() == 0) && origReq != null) {
      caller = origReq.getFrom().toString();
    }
    final String callee = Utils.processTo(to);
    LOG.info("Creating outgoing call : " + caller + "-->" + callee + "," + answerOnMedia + "," + timeout);
    try {
      final SipServletRequest req = _sipFactory.createRequest(_session, "INVITE", caller, callee);
      if (_inst != null) {
        call = new SimpleOutgoingCall(this, origReq == null ? null : origReq, req,
            answerOnMedia, _inst);
      }
      else {
        call = new SimpleOutgoingCall(this, origReq == null ? null : origReq, req,
            answerOnMedia, _app);        
      }
      if (listener != null) {
        if (!listener.isStarted()) {
          throw new ErrorException("Outbound call cancelled.");
        }
        listener.setCallback(new MrcpRTCCallback() {
          public void invoke() {
            call.hangup();
          }
        });
      }
      req.send();
      LOG.info(call + " is calling.");
      if (LOG.isDebugEnabled()) {
        LOG.debug(call + "->sending outgoing INVITE [timeout:" + timeout
            + ",answerOnMedia:" + answerOnMedia + "]:\r\n" + req);
      }
      final long start = System.currentTimeMillis();
      long time = timeout;
      call.lock();
      try {
        while ((call.getState() == Call.State.RINGING || call.getState() == Call.State.ANSWERING) && time > 0) {
          time = timeout - (System.currentTimeMillis() - start);
          call.await(time);
        }
        if (time <= 0 && (call.getState() == Call.State.RINGING || call.getState() == Call.State.ANSWERING)) {
          call.hangup();
          throw new ErrorException("Outbound call is timeout.");
        }
        if (call.getState() != Call.State.ANSWERED) {
          LOG.error(call + " can not be completed at this time.");
          if (listener != null && !listener.isStarted()) {
            throw new ErrorException("Outbound call cancelled.");
          }
          throw new ErrorException("Outbound call can not complete.");
        }
        LOG.info(call + " is answered.");
        if (callRecordUri != null && callRecordUri.length() > 0) {
          call.startCallRecording(callRecordUri, callRecordFormat, null, null);
        }
      }
      finally {
        call.unlock();
      }
      return call;
    }
    catch (final ErrorException e) {
      throw e;
    }
    catch (final Throwable e) {
      LOG.error("Error creating outgoing call to " + to + " : " + e.getMessage(), e);
      throw new ErrorException(e);
    }
  }
}
