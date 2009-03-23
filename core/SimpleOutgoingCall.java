package com.voxeo.tropo.core;

import java.io.IOException;

import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;
import javax.servlet.sip.ar.SipApplicationRoutingDirective;

import org.apache.log4j.Logger;

import com.mot.mrcp.MrcpException;
import com.voxeo.tropo.Configuration;
import com.voxeo.tropo.ServletContextConstants;
import com.voxeo.tropo.app.Application;
import com.voxeo.tropo.app.ApplicationInstance;
import com.voxeo.sipmethod.mrcp.client.Endpoint;

public class SimpleOutgoingCall extends SimpleCall implements OutgoingCall {

  private static final Logger LOG = Logger.getLogger(SimpleOutgoingCall.class);

  protected boolean _isAnswerOnMedia = false;

  protected String _mrcpSDP = null;

  protected SipServletRequest _origReq = null;

  public SimpleOutgoingCall(final SimpleCallFactory callFactory, final SipServletRequest origReq, final SipServletRequest req,
      final boolean answerOnMedia, ApplicationInstance inst) throws IOException, MrcpException {
    super(callFactory, req, inst);
    init(origReq, answerOnMedia);
  }
  
  public SimpleOutgoingCall(final SimpleCallFactory callFactory, final SipServletRequest origReq, final SipServletRequest req,
      final boolean answerOnMedia, Application app) throws IOException, MrcpException {
    super(callFactory, req, app);
    init(origReq, answerOnMedia);
  }
  
  void init(final SipServletRequest origReq, final boolean answerOnMedia) throws IOException {
    _isAnswerOnMedia = answerOnMedia;
    setState(State.ANSWERING);
    final URI to = _invite.getTo().getURI();
    if (to.isSipURI()) {
      final SipURI dest = (SipURI) to;
      initMrcpClient(new Endpoint(dest.getHost(), dest.getPort() > 0 ? dest.getPort() : 1, null), _sipSession
          .getApplicationSession());
    }
    else {
      initMrcpClient(null, _sipSession.getApplicationSession());
    }
    _isMixed = true;
    _mrcpSDP = getASR().getMrcpEndpoint().getSdp();
    if (_mrcpSDP != null && Configuration.get().getHackSDP()) {
      _mrcpSDP = _mrcpSDP.replaceAll("127.0.0.1", Configuration.get().getMediaAddress());
    }
    _origReq = origReq;
    _invite.setContent(_mrcpSDP, ServletContextConstants.CONTENT_TYPE_SDP);
    _invite.setRoutingDirective(SipApplicationRoutingDirective.CONTINUE, _origReq == null ? null : _origReq);
    LOG.info(this + " will be called.");    
  }
  
  public void update(final SipServletRequest invite) throws IOException {
    updateInvite(invite);
    _invite.setContent(_mrcpSDP, ServletContextConstants.CONTENT_TYPE_SDP);
    _invite.setRoutingDirective(SipApplicationRoutingDirective.CONTINUE, _origReq == null ? null : _origReq);
    LOG.info("Updated Outgoing " + this);
  }

  public boolean isAnswerOnMedia() {
    return _isAnswerOnMedia;
  }

  @Override
  protected void stateChanged(final State oldState, final State newState) {
    if (!_stateLock.isHeldByCurrentThread()) {
      throw new IllegalStateException("The caller must hold the lock first.");
    }
    if (LOG.isDebugEnabled()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(this.toString() + " state is changed: " + oldState + "->"
            + newState);
      }
    }
    if ((oldState == Call.State.RINGING || oldState == Call.State.ANSWERING)
        && (newState == Call.State.ANSWERED || newState == Call.State.FAILED || newState == Call.State.DISCONNECTED)) {
      signal(null);
    }

    if (oldState == Call.State.ANSWERED && (newState == Call.State.FAILED || newState == Call.State.DISCONNECTED)) {
      signal(null);
    }
    super.stateChanged(oldState, newState);
  }
}
