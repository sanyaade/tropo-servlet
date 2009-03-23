package com.voxeo.tropo;

import java.io.IOException;

import javax.annotation.Resource;
import javax.management.MBeanServer;
import javax.script.ScriptException;
import javax.sdp.SessionDescription;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.TelURL;
import javax.servlet.sip.URI;

import org.apache.log4j.Logger;

import com.voxeo.tropo.app.AbstractApplicationManager;
import com.voxeo.tropo.app.Application;
import com.voxeo.tropo.app.ApplicationInstance;
import com.voxeo.tropo.app.ApplicationManager;
import com.voxeo.tropo.app.InvalidApplicationException;
import com.voxeo.tropo.app.RedirectException;
import com.voxeo.tropo.core.Call;
import com.voxeo.tropo.core.CallImpl;
import com.voxeo.tropo.core.IncomingCall;
import com.voxeo.tropo.core.OutgoingCall;
import com.voxeo.tropo.core.Call.State;
import com.voxeo.tropo.util.DumpHelper;
import com.voxeo.tropo.util.Utils;

@SuppressWarnings("serial")
@javax.servlet.sip.annotation.SipServlet(name = "tropo", loadOnStartup = 1)
public class SIPDriver extends SipServlet {

  private static final Logger LOG = Logger.getLogger(SIPDriver.class);

  protected ApplicationManager _appMgr;

  @Resource
  protected SipFactory _sipFactory;

  @Resource
  protected MBeanServer _server;

  @Override
  public void init() {
    final ServletContext ctx = getServletContext();
    try {
      Configuration.init(this.getServletConfig());
      AbstractApplicationManager.load(Configuration.get(), ctx);
      DumpHelper.initialization(_server);
    }
    catch (final Exception e) {
      LOG.error("Unable to initialize Tropo:", e);
      throw new RuntimeException(e);
    }

    _appMgr = (ApplicationManager) ctx.getAttribute(ServletContextConstants.APP_MANAGER);
  }

  @Override
  public void destroy() {
    _appMgr.dispose();
  }

  @Override
  protected void doInvite(final SipServletRequest req) throws ServletException, IOException {
    if (req.isInitial()) {
      req.createResponse(SipServletResponse.SC_TRYING).send();
      LOG.info("A new call is coming from " + req.getFrom().getURI() + " to " + req.getTo().getURI());

      final URI token = req.getTo().getURI();
      try {
        // set guid session id for logging
        req.getSession().getApplicationSession().setAttribute(ServletContextConstants.GUID_SESSION_ID, Utils.getGUID());
        final Application app = _appMgr.get(token);
        Utils.setLogContext(app, req);
        req.createResponse(SipServletResponse.SC_RINGING).send();
        app.execute(req);
      }
      catch (final RedirectException e) {
        LOG.info("Redirect request for " + req.getTo());
        SipServletResponse response = req.createResponse(SipServletResponse.SC_MOVED_TEMPORARILY);
        for(SipURI uri : e.getContacts()) {
          response.addHeader("Contact", uri.toString());
        }
        response.send();       
      }
      catch (final InvalidApplicationException e) {
        LOG.error("Unknown application name for " + req.getTo() + ". " + e.getMessage(), e);
        req.createResponse(SipServletResponse.SC_NOT_FOUND).send();
      }
      catch (final ScriptException e) {
        LOG.error("Invalid script for " + req.getTo() + ". " + e.getMessage(), e);
        req.createResponse(SipServletResponse.SC_TEMPORARLY_UNAVAILABLE).send();
      }
    }
    else { // re-invite
      final CallImpl call = findCall(req);
      if (call != null) {
        if (call.getState() == Call.State.ANSWERED) {
          updateEndpoint(call, req);
        }
      }
    }
  }

  @Override
  protected void doAck(final SipServletRequest req) throws ServletException, IOException {
    final CallImpl call = findCall(req);
    if (call != null) {
      call.lock();
      try {
        if (call.getState() == Call.State.ANSWERING) {
          updateEndpoint(call, req);
          call.setState(Call.State.ANSWERED);
        }
        else if (call.getState() == Call.State.REJECTING) {
          call.setState(Call.State.REJECTED);
        }
        else if (call.getState() == Call.State.REDIRECTING) {
          call.setState(Call.State.REDIRECTED);
        }
        else {
          if (LOG.isDebugEnabled()) {
            LOG.debug("No match state: " + call);
          }
        }
      }
      finally {
        call.unlock();
      }
    }
  }

  @Override
  protected void doCancel(final SipServletRequest req) throws ServletException, IOException {
    final CallImpl call = findCall(req);
    if (call != null) {
      call.lock();
      try {
        if (call.getState() == State.RINGING) {
          call.setState(Call.State.DISCONNECTED);
        }
      }
      finally {
        call.unlock();
      }
    }
  }

  @Override
  protected void doBye(final SipServletRequest req) throws ServletException, IOException {
    final SipApplicationSession as = req.getSession().getApplicationSession();
    final ApplicationInstance inst = (ApplicationInstance) as.getAttribute(ApplicationInstance.INST);
    final CallImpl call = findCall(req);
    if (call != null) {
      call.setState(Call.State.DISCONNECTED);
      LOG.info("A call just ended: " + req.getFrom().getURI() + "--->" + req.getTo().getURI());
      req.createResponse(SipServletResponse.SC_OK).send();
    }
    else {
      req.createResponse(SipServletResponse.SC_DOES_NOT_EXIT_ANYWHERE).send();
    }
    // check which leg is this???
    if (inst != null) {
      inst.terminate();
    }
    else {
      LOG.error("No matching Application Instance is found.");
    }
  }

  @Override
  protected void doProvisionalResponse(final SipServletResponse res) throws ServletException, IOException {
    final CallImpl call = findCall(res);
    if (call == null || call instanceof IncomingCall) {
      return;
    }
    if (res.getMethod().equalsIgnoreCase("INVITE")) {
      final int code = res.getStatus();
      switch (code) {
        case SipServletResponse.SC_RINGING:
          call.lock();
          try {
            if (call.getState() == Call.State.ANSWERING) {
              call.setState(Call.State.RINGING);
            }
          }
          finally {
            call.unlock();
          }
          break;
        case SipServletResponse.SC_SESSION_PROGRESS:
          final OutgoingCall out = (OutgoingCall) call;
          if (out.isAnswerOnMedia()) {
            final Object o = res.getContent();
            if (o instanceof SessionDescription) {
              call.lock();
              try {
                final Call.State state = call.getState();
                if (state == Call.State.ANSWERING || state == Call.State.RINGING) {
                  call.setState(Call.State.ANSWERED);
                }
              }
              finally {
                call.unlock();
              }
            }
          }
          break;
        default:
          break;
      }
    }
  }

  @Override
  protected void doSuccessResponse(final SipServletResponse res) throws ServletException, IOException {
    if (res.getMethod().equalsIgnoreCase("INVITE")) {
      final CallImpl call = findCall(res);
      if (call != null) {
        call.lock();
        try {
          final Call.State state = call.getState();
          if (state == Call.State.ANSWERING || state == Call.State.RINGING) {
            updateEndpoint(call, res);
            call.setState(Call.State.ANSWERED);
          }
        }
        finally {
          call.unlock();
        }
      }
      res.createAck().send();
    }
    else if (res.getMethod().equalsIgnoreCase("BYE")) {
      // do we do anything?
    }
  }

  @Override
  protected void doErrorResponse(final SipServletResponse res) throws ServletException, IOException {
    if (res.getMethod().equalsIgnoreCase("INVITE")) {
      final CallImpl call = findCall(res);
      if (call != null) {
        call.lock();
        try {
          final Call.State state = call.getState();
          if (state == Call.State.ANSWERING || state == Call.State.RINGING) {
            call.setState(Call.State.DISCONNECTED);
          }
          else if (state == Call.State.ANSWERED) { // answerOnMedia case
            call.hangup();
          }
        }
        finally {
          call.unlock();
        }
      }
    }
  }

  @Override
  protected void doRedirectResponse(final SipServletResponse res) throws ServletException, IOException {
    if (res.getMethod().equalsIgnoreCase("INVITE")) {
      final CallImpl call = findCall(res);
      if (call != null) {
        call.lock();
        try {
          final Call.State state = call.getState();
          if (state == Call.State.ANSWERING || state == Call.State.RINGING) {
            final Address contact = res.getAddressHeader("Contact");
            if (contact != null) {
              URI target = contact.getURI();
              final SipServletRequest req = _sipFactory.createRequest(res.getApplicationSession(), "INVITE", res
                  .getFrom(), res.getTo());
              if (target instanceof TelURL) {
                target = _sipFactory.createURI(Utils.prefixNumber(String.valueOf(target)));
              }
              req.setRequestURI(target);
              ((OutgoingCall) call).update(req);
              req.send();
              res.getSession().invalidate();
            }
          }
        }
        finally {
          call.unlock();
        }
      }
    }
  }

  protected CallImpl findCall(final SipServletMessage message) {
    Utils.setLogContext(message);
    final CallImpl call = (CallImpl) message.getSession().getAttribute(Call.INST);
    if (call == null) {
      LOG.warn("No call is found for " + message);
    }
    else {
      if (LOG.isDebugEnabled()) {
        LOG.debug("do" + (message instanceof SipServletResponse ? ((SipServletResponse) message).getStatus() : "")
            + message.getMethod() + " find call: " + call);
      }
    }
    return call;
  }

  protected void updateEndpoint(final CallImpl call, final SipServletMessage message) {
    Object content = null;
    try {
      content = message.getContent();
    }
    catch (final Throwable t) {
      LOG.warn("getContent error " + t);
    }
    if (content != null) {
      call.updateEndpoint(message.getRemoteAddr(), message.getRemotePort(), content.toString());
    }
  }

}
