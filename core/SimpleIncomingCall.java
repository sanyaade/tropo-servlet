package com.voxeo.tropo.core;

import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

import org.apache.log4j.Logger;

import com.voxeo.tropo.ErrorException;
import com.voxeo.tropo.FatalException;
import com.voxeo.tropo.ServletContextConstants;
import com.voxeo.tropo.app.Application;
import com.voxeo.tropo.app.ApplicationInstance;
import com.voxeo.tropo.util.Utils;
import com.voxeo.sipmethod.mrcp.client.Endpoint;

public class SimpleIncomingCall extends SimpleCall implements IncomingCall {

  private static final Logger LOG = Logger.getLogger(SimpleIncomingCall.class);

  public SimpleIncomingCall(final SimpleCallFactory callFactory, final SipServletRequest invite, final ApplicationInstance inst) {
    super(callFactory, invite, inst);
    init();
  }
  
  public SimpleIncomingCall(final SimpleCallFactory callFactory, final SipServletRequest invite, final Application app) {
    super(callFactory, invite, app);
    init();
  }
  
  void init() {
    setState(State.RINGING);
    if (LOG.isDebugEnabled()) {
      LOG.debug(this + "->got INVITE:\r\n" + _invite);
    }
    LOG.info(this + " is received.");    
  }

  public void answer(final int timeout) {
    LOG.info(this + "->answer(" + timeout + ")");
    try {
      initMrcpClient(new Endpoint(_invite.getRemoteAddr(), _invite.getRemotePort(), _invite.getContent().toString()),
          _sipSession.getApplicationSession());
      final long start = System.currentTimeMillis();
      long time = timeout;
      lock();
      try {
        if (_state == State.RINGING || _state == State.ANSWERING) {
          setState(Call.State.ANSWERING);
        }
        else {
          throw new FatalException("Expected Ringing or Answering state: " + _state);
        }
        final String content = getASR().getMrcpEndpoint().getSdp();
        final SipServletResponse response = _invite.createResponse(SipServletResponse.SC_OK);
        response.setContent(content, ServletContextConstants.CONTENT_TYPE_SDP);
        response.send();
        LOG.info(this + " is answering.");
        while ((_state == State.RINGING || _state == State.ANSWERING) && time > 0) {
          time = timeout - (System.currentTimeMillis() - start);
          await(time);
        }
        if (time <= 0 && _state != State.ANSWERED) {
          throw new ErrorException("Can not answer within " + timeout + "ms.");
        }
        assertReady("answer", Call.State.ANSWERED);
        LOG.info(this + " is answered.");
      }
      finally {
        unlock();
      }
    }
    catch (final Throwable t) {
      lock();
      try {
        if (_state == State.RINGING || _state == State.ANSWERING) {
          try {
            _invite.createResponse(SipServletResponse.SC_SERVER_INTERNAL_ERROR).send();
          }
          catch (final Throwable t1) {
            ;
          }
          setState(State.FAILED);
        }
      }
      finally {
        unlock();
      }
      handleException(t, "answer");
    }
  }

  public void reject() {
    LOG.info(this + "->reject()");
    lock();
    try {
      if (_state == State.RINGING || _state == State.ANSWERING) {
        setState(Call.State.REJECTING);
        _invite.createResponse(SipServletResponse.SC_DECLINE).send();
        LOG.info(this + " has been rejected.");
      }
      else if (_state == State.REJECTING || _state == State.REJECTED) {
        ;
      }
      else {
        LOG.error(this + "can not be rejected in " + _state + " state.");
        throw new ErrorException("Expected Ringing or Answering state: " + _state);
      }
    }
    catch (final Throwable t) {
      handleException(t, "reject");
    }
    finally {
      unlock();
    }
  }

  public void redirect(String number) {
    LOG.info(this + "->redirect(" + number + ")");
    number = Utils.processTo(number);
    lock();
    try {
      if (_state == State.RINGING || _state == State.ANSWERING) {
        setState(Call.State.REDIRECTING);
        final SipServletResponse response = _invite.createResponse(SipServletResponse.SC_MOVED_TEMPORARILY);
        response.addHeader("Contact", number);
        response.send();
        LOG.info(this + " has been redirected to " + number);
      }
      else if (_state == State.REDIRECTING || _state == State.REDIRECTED) {
        ;
      }
      else {
        LOG.error(this + " can not be redirected in " + _state + " state.");
        throw new ErrorException("Expected Ringing or Answering state: " + _state);
      }
    }
    catch (final Throwable t) {
      handleException(t, "redirect");
    }
    finally {
      unlock();
    }
  }

  @Override
  protected void stateChanged(final State oldState, final State newState) {
    if (!_stateLock.isHeldByCurrentThread()) {
      throw new IllegalStateException("The caller must hold the lock first.");
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug(this.toString() + " state is changed: " + oldState + "->" + newState);
    }
    if (oldState == Call.State.ANSWERING
        && (newState == Call.State.ANSWERED || newState == Call.State.FAILED || newState == Call.State.DISCONNECTED)) {
      signal(null);
    }
    super.stateChanged(oldState, newState);
  }
}
