package com.voxeo.tropo.core;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.TelURL;
import javax.servlet.sip.URI;

import org.apache.log4j.Logger;

import com.mot.mrcp.MrcpException;
import com.voxeo.sipmethod.mrcp.client.Endpoint;
import com.voxeo.sipmethod.mrcp.client.Grammar;
import com.voxeo.sipmethod.mrcp.client.GrammarFactory;
import com.voxeo.sipmethod.mrcp.client.MrcpAsrResult;
import com.voxeo.sipmethod.mrcp.client.MrcpAsrSession;
import com.voxeo.sipmethod.mrcp.client.MrcpClient;
import com.voxeo.sipmethod.mrcp.client.MrcpConstants;
import com.voxeo.sipmethod.mrcp.client.MrcpFactory;
import com.voxeo.sipmethod.mrcp.client.MrcpSession;
import com.voxeo.sipmethod.mrcp.client.MrcpTtsResult;
import com.voxeo.sipmethod.mrcp.client.MrcpTtsSession;
import com.voxeo.sipmethod.mrcp.client.RecognizerRequestHandle;
import com.voxeo.sipmethod.mrcp.client.RecorderRequestHandle;
import com.voxeo.sipmethod.mrcp.client.RequestHandle;
import com.voxeo.sipmethod.mrcp.client.Grammar.InputMode;
import com.voxeo.tropo.Configuration;
import com.voxeo.tropo.ErrorException;
import com.voxeo.tropo.FatalException;
import com.voxeo.tropo.ServletContextConstants;
import com.voxeo.tropo.app.Application;
import com.voxeo.tropo.app.ApplicationInstance;
import com.voxeo.tropo.app.ApplicationMonitor;
import com.voxeo.tropo.util.MrcpBackgroundAudioPlayer;
import com.voxeo.tropo.util.MrcpRTCListener;
import com.voxeo.tropo.util.Utils;

public class SimpleCall implements CallImpl {

  private static final Logger LOG = Logger.getLogger(SimpleCall.class);

  protected MrcpFactory _mrcpFactory;

  protected SipFactory _sipFactory;

  protected SimpleCallFactory _callFactory;

  protected SipSession _sipSession;

  protected SipServletRequest _invite; // do we have to keep the invite?

  protected State _state;

  protected final ReentrantLock _stateLock = new ReentrantLock();

  protected final Condition _stateCondition = _stateLock.newCondition();

  protected final Object _mrcpLock = new Object();

  protected MrcpClient _mrcpClient = null;

  protected String _calledId;

  protected String _callerId;

  protected String _callerName;

  protected String _calledName;

  protected SimpleCall _peerLeg;

  protected boolean _isMixed = false;

  protected ApplicationMonitor _monitor;

  protected long _createdTime = System.currentTimeMillis();

  protected String _id;

  public SimpleCall(final SimpleCallFactory callFactory, final SipServletRequest invite, final ApplicationInstance inst) {
    this(callFactory, inst.getApp().getManager().getSipFactory(), inst.getApp().getManager().getMrcpFactory(), invite);
  }

  public SimpleCall(final SimpleCallFactory callFactory, final SipServletRequest invite, final Application app) {
    this(callFactory, app.getManager().getSipFactory(), app.getManager().getMrcpFactory(), invite);
  }

  SimpleCall(final SimpleCallFactory callFactory, final SipFactory sipFactory, final MrcpFactory mrcpFactory,
      final SipServletRequest invite) {
    _callFactory = callFactory;
    _mrcpFactory = mrcpFactory;
    _sipFactory = sipFactory;
    _monitor = (ApplicationMonitor) invite.getSession().getServletContext().getAttribute(
        ServletContextConstants.APP_MONITOR);
    updateInvite(invite);
    _callerId = extractId(_invite.getFrom().getURI());
    _calledId = extractId(_invite.getTo().getURI());
    _calledName = _invite.getFrom().getDisplayName();
    _callerName = _invite.getTo().getDisplayName();
    if (_monitor != null) {
      _monitor.incCallCounter();
    }
    _id = _sipSession.getCallId();
  }

  public State getState() {
    lock();
    try {
      return _state;
    }
    finally {
      unlock();
    }
  }

  public void setState(final State state) {
    // MessageResourceUtil.format(message, parameters, null)
    lock();
    try {
      final State old = _state;
      _state = state;
      stateChanged(old, _state);
    }
    finally {
      unlock();
    }
  }

  public void updateEndpoint(final String remoteAddr, final int remotePort, final String sdp) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(this + "->updateEndpoint when " + getState());
    }
    if (getState() == Call.State.RINGING || getState() == Call.State.ANSWERING || getState() == Call.State.ANSWERED) {
      getMrcpClient().setEndpoint(new Endpoint(remoteAddr, remotePort, sdp));
    }
  }

  public void hangup() {
    LOG.info(this + "->hangup()");
    lock();
    try {
      if (_state == State.DISCONNECTED) {
        return;
      }
      else if (_state == State.ANSWERED) {
        try {
          _sipSession.createRequest("BYE").send();
        }
        finally {
          setState(Call.State.DISCONNECTED);
        }
      }
      else if (_state == State.RINGING || _state == State.ANSWERING) {
        try {
          if (this instanceof OutgoingCall) {
            _invite.createCancel().send();
          }
          else {
            _invite.createResponse(SipServletResponse.SC_DECLINE).send();
          }
        }
        finally {
          setState(Call.State.DISCONNECTED);
        }
      }
    }
    catch (final Throwable e) {
      handleException(e, "hangup");
    }
    finally {
      unlock();
    }
  }

  public Map<String, String> prompt(final String ttsOrUrl, final boolean bargein, final String grammar,
      final String confidence, final String mode, final int wait) {
    LOG.info(this + "->prompt(\"" + ttsOrUrl + "\"," + bargein + ",\"" + grammar + "\"," + confidence + "," + mode
        + "," + wait + ")");
    try {
      assertReady("prompt", Call.State.ANSWERED);

      RecognizerRequestHandle handle = null;
      if (grammar != null && grammar.length() > 0) {
        handle = getASR().startRecognize(GrammarFactory.createGrammar(grammar),
            buildRecognitionProperties(wait, confidence, mode));
      }

      if (ttsOrUrl != null && ttsOrUrl.length() > 0) {
        getTTS().speak(Utils.genSSML(ttsOrUrl, Configuration.get().isParseSpeechText()), buildSpeakProperties(bargein),
            Configuration.get().getMaxTimeSpeak());
      }

      if (handle == null) {
        final Map<String, String> retval = new HashMap<String, String>(1);
        retval.put("value", "noChoice");
        return retval;
      }

      final MrcpAsrResult result = handle.waitForResult(buildRecognitionProperties(wait, confidence, mode), wait);
      if (result.getType() == MrcpAsrResult.Type.SUCCESS) {
        final Map<String, String> retval = getReturnValue(result);
        if (LOG.isDebugEnabled()) {
          LOG.debug(this + "->prompt returned : " + retval);
        }
        return retval;
      }
      else {
        LOG.warn(this + "->prompt got error: " + result.getType());
        throw new ErrorException(result.getType().toString());
      }
    }
    catch (final Throwable e) {
      handleException(e, "prompt");
      return null;
    }
  }

  // just for test
  public Map<String, String> promptWithRecord(final String ttsOrUrl, final boolean bargein, final String grammar,
      final String confidence, final String mode, final int wait, final boolean record, final boolean beep,
      final int maxtime, final int finalSilence) {
    LOG.info(this + "->promptWithRecord(\"" + ttsOrUrl + "\"," + bargein + ",\"" + grammar + "\"," + confidence + ","
        + mode + "," + wait + "," + record + "," + beep + "," + maxtime + "," + finalSilence + ")");
    try {
      assertReady("prompt and record", Call.State.ANSWERED);

      final Properties props = buildRecognitionProperties(wait, confidence, mode);
      RequestHandle handle = null;
      if (grammar == null || grammar.length() == 0) {
        if (record) {
          handle = getASR().startRecord(null, props, maxtime, finalSilence);
        }
      }
      else {
        final Grammar g = GrammarFactory.createGrammar(grammar);
        if (record) {
          handle = getASR().startRecord(g, props, maxtime, finalSilence);
        }
        else {
          handle = getASR().startRecognize(g, props);
        }
      }

      MrcpTtsResult ttsResult = null;
      if (ttsOrUrl != null && ttsOrUrl.length() > 0) {
        ttsResult = getTTS().speak(Utils.genSSML(ttsOrUrl, Configuration.get().isParseSpeechText()),
            buildSpeakProperties(bargein), Configuration.get().getMaxTimeSpeak());
      }
      if (beep && ttsResult.getReturnCode() != MrcpTtsSession.TTS_RC_BARGEIN) {
        getTTS().play(new URL(Configuration.get().getBeepURL()), true, 3000);
      }

      MrcpAsrResult result = null;
      if (handle instanceof RecognizerRequestHandle) {
        result = ((RecognizerRequestHandle) handle).waitForResult(props, wait);
      }
      else if (handle instanceof RecorderRequestHandle) {
        result = ((RecorderRequestHandle) handle).waitForResult(props, wait);
      }
      Map<String, String> retval = new HashMap<String, String>(0);

      if (result == null) {
        return retval;
      }

      if (result.getType() == MrcpAsrResult.Type.SUCCESS || result.getType() == MrcpAsrResult.Type.NO_MATCH && record
          && (grammar == null || grammar.length() == 0)) {
        retval = getReturnValue(result);
        if (LOG.isDebugEnabled()) {
          LOG.debug(this + "->promptWithRecord returned : " + retval);
        }
        return retval;
      }
      else {
        LOG.warn(this + "->promptWithRecord got error: " + result.getType());
        throw new ErrorException(result.getType().toString());
      }
    }
    catch (final Throwable e) {
      handleException(e, "prompt and record");
      return null;
    }
  }
  /**
   * format: audio/wav, audio/gsm, audio/au
   */
  public void startCallRecording(final String filenameOrUrl, final String format, final String publicKey,
      final String publicKeyUri) {
    LOG.info(this + "->startCallRecording(\"" + filenameOrUrl + "\",\"" + format + "\",\"" + publicKey + "\",\"" + publicKeyUri + "\")");
    assertReady("startCallRecording", State.ANSWERED);
    if (filenameOrUrl != null && filenameOrUrl.length() > 0) {
      final Properties props = buildCallRecordingProperties(format, publicKey, publicKeyUri);
      try {
        getASR().startCallRecording(filenameOrUrl.trim(), props);
      }
      catch (MrcpException e) {
        handleException(e, "start recording call");
      }
    }
  }
  
  public void stopCallRecording() {
    LOG.info(this + "->stopCallRecording()");
    //assertReady("stop recording call", Call.State.ANSWERING);
    try {
      getASR().stopCallRecording(buildCallRecordingProperties(null, null, null));
    }
    catch (MrcpException e) {
      handleException(e, "stop recording call");
    }
  }

  public Call transfer(final String to, final String from, final boolean answerOnMedia, final int timeout,
      final String ttsOrUrl, final int repeat, final String grammar) {
    LOG.info(this + "->transfer(" + to + ") [from:" + from + ",timeout:" + timeout + ",ttsOrUrl:" + ttsOrUrl
        + ",grammar:" + grammar + ",repeat:" + repeat + ",answerOnMedia:" + answerOnMedia + "]");

    assertReady("transfer", Call.State.ANSWERED);

    MrcpRTCListener listener = null;
    if (grammar != null && grammar.length() > 0) {
      listener = MrcpRTCListener.create(getASR(), GrammarFactory.createGrammar(grammar, Grammar.Type.SIMPLE,
          InputMode.dtmf), timeout);
      listener.start();
    }
    MrcpBackgroundAudioPlayer player = null;
    if (ttsOrUrl != null && ttsOrUrl.length() > 0) {
      player = MrcpBackgroundAudioPlayer.create(getTTS());
      player.start(ttsOrUrl, repeat);
    }

    final SimpleCall call;
    try {
      call = _callFactory.call(_invite, from, to, answerOnMedia, timeout, listener, null, null);
    }
    catch (final RuntimeException t) {
      LOG.error("Error creating outgoing call to " + this + " : " + t.getMessage(), t);
      throw t;
    }
    finally {
      if (listener != null) {
        listener.stop();
      }
      if (player != null) {
        player.stop();
      }
    }

    bindCall(call);
    call.lock();
    try {
      if (call.getState() != Call.State.ANSWERED) {
        unbindCall();
        throw new ErrorException("Outbound call can not complete.");
      }
      getASR().join(call.getASR(), new Properties());
      LOG.info(call + " has been transfered.");
      _isMixed = true;
      while (call.getState() == Call.State.ANSWERED) {
        call.await(timeout);
      }
      try {
        getASR().unjoin();
        _isMixed = false;
      }
      catch (final Throwable e3) {
        ;
      }
      LOG.info(call + " is completed.");
      return call;
    }
    catch (final InterruptedException e) {
      LOG.error("Error creating outgoing call to " + to + " : " + e.getMessage(), e);
      transferCleanup(call);
      throw new ErrorException(e);
    }
    catch (final MrcpException e) {
      LOG.error("Error creating outgoing call to " + to + " : " + e.getMessage(), e);
      transferCleanup(call);
      throw new FatalException(e);
    }
    finally {
      call.unlock();
    }
  }

  public String getCalledId() {
    return _calledId;
  }

  public String getCallerId() {
    return _callerId;
  }

  public String getCallerName() {
    return _callerName;
  }

  public String getCalledName() {
    return _calledName;
  }

  public MrcpTtsSession getTTS() {
    try {
      return getMrcpClient().getTtsSession();
    }
    catch (final MrcpException e) {
      throw new FatalException(e);
    }
  }

  public MrcpAsrSession getASR() {
    try {
      return getMrcpClient().getAsrSession();
    }
    catch (final MrcpException e) {
      throw new FatalException(e);
    }
  }

  public void lock() {
    _stateLock.lock();
  }

  public void unlock() {
    _stateLock.unlock();
  }

  public void await(final long time) throws InterruptedException {
    if (time < 0) {
      // _stateCondition.await(); // Do we have any use case to wait forever???
    }
    else {
      _stateCondition.await(time, TimeUnit.MILLISECONDS);
    }
  }

  public void signal(final Object message) {
    _stateCondition.signalAll();
  }

  public void log(final Object msg) {
    if (_sipSession != null && _sipSession.isValid()) {
      if (msg instanceof Throwable) {
        final Throwable t = (Throwable) msg;
        LOG.error(this + " : " + t.getMessage(), t);
      }
      else {
        LOG.info(this + " : " + msg);
      }

    }
    else {
      if (msg instanceof Throwable) {
        final Throwable t = (Throwable) msg;
        LOG.info(this + " : " + t.getMessage(), t);
      }
      else {
        LOG.info(this + " : " + msg);
      }
    }
  }

  public boolean isActive() {
    lock();
    try {
      return _state != State.DISCONNECTED && _state != State.FAILED && _state != State.REJECTED;
    }
    finally {
      unlock();
    }
  }

  public void block(final int milliSeconds) {
    LOG.info(this + "->block(" + milliSeconds + ")");
    if (milliSeconds > 0) {
      final long start = System.currentTimeMillis();
      long time = milliSeconds;
      lock();
      try {
        while (isActive() && time > 0) {
          time = milliSeconds - (System.currentTimeMillis() - start);
          try {
            _stateCondition.await(time, TimeUnit.MILLISECONDS);
          }
          catch (final InterruptedException e) {
            ;
          }
        }
      }
      finally {
        unlock();
      }
    }
    else {
      lock();
      try {
        while (isActive()) {
          try {
            _stateCondition.await();
          }
          catch (final InterruptedException e) {
            ;
          }
        }
      }
      finally {
        unlock();
      }
    }
  }

  public long getCreatedTime() {
    return _createdTime;
  }

  @Override
  public String toString() {
    return "Call[" + _callerId + "->" + _calledId + "]";
  }

  protected void stateChanged(final State oldState, final State newState) {
    if (oldState == newState) {
      return;
    }
    if (newState == Call.State.DISCONNECTED || newState == Call.State.REJECTING || newState == Call.State.FAILED
        || newState == Call.State.REDIRECTING) {
      _monitor.decCallCounter();
    }
    if (newState == Call.State.DISCONNECTED) {
      synchronized (_mrcpLock) {
        try {
          if (_mrcpClient != null && getTTS().getState() != MrcpSession.State.INVALID) {
            getTTS().disconnect();
            getTTS().invalidate();
          }
        }
        catch (final Throwable e) {
          // ignore;
        }
        try {
          if (_mrcpClient != null && getASR().getState() != MrcpSession.State.INVALID) {
            if (_isMixed) {
              getASR().unjoin();
            }
            getASR().disconnect();
            getASR().invalidate();
          }
        }
        catch (final Throwable e) {
          // ignore;
        }
        _mrcpClient = null;
      }
      if (_peerLeg != null) {
        try {
          _peerLeg.hangup();
        }
        catch (final Throwable e) {
          ;
        }
        finally {
          _peerLeg = null;
        }
      }
    }
  }

  protected void bindCall(final SimpleCall call) {
    this._peerLeg = call;
  }

  protected void unbindCall() {
    this._peerLeg = null;
  }

  protected void updateInvite(final SipServletRequest req) {
    _invite = req;
    _sipSession = req.getSession();
    _sipSession.setAttribute(CallImpl.INST, this);
  }

  protected void transferCleanup(final SimpleCall call) {
    if (_isMixed) {
      try {
        getASR().unjoin();
        _isMixed = false;

      }
      catch (final Throwable e) {
        ;
      }
    }
    unbindCall();
    if (call != null) {
      try {
        call.hangup();
      }
      catch (final Throwable t) {
        ;
      }
    }
  }

  protected MrcpClient initMrcpClient(final Endpoint ep, final SipApplicationSession session) throws FatalException {
    synchronized (_mrcpLock) {
      if (_mrcpClient == null) {
        final Properties props = new Properties();
        props.setProperty(MrcpConstants.X_VOXEO_ACCOUNT_ID, "-1");
        props.setProperty(MrcpConstants.X_VOXEO_APP_ID, "-1");
        props.setProperty(MrcpConstants.X_VOXEO_SESSION_ID, "-1");
        if (session.isValid()) {
          final ApplicationInstance ai = (ApplicationInstance) session.getAttribute(ApplicationInstance.INST);
          final String aid = ai == null ? "-1" : String.valueOf(ai.getApp().getAccountID());
          props.setProperty(MrcpConstants.X_VOXEO_ACCOUNT_ID, aid);
          props.setProperty(MrcpConstants.X_VOXEO_APP_ID, session.getApplicationName());
          props.setProperty(MrcpConstants.X_VOXEO_SESSION_ID, session.getId());
        }
        final Configuration c = Configuration.get();
        try {
          _mrcpClient = _mrcpFactory.createMrcpClient(ep, c.getMediaServer(), c.getMediaServerPort(), props);
        }
        catch (final Throwable t) {
          throw new FatalException(t);
        }
      }
      return _mrcpClient;
    }
  }

  protected MrcpClient getMrcpClient() {
    synchronized (_mrcpLock) {
      if (_mrcpClient == null) {
        throw new FatalException("MRCP client has not been initialized.");
      }
      return _mrcpClient;
    }
  }

  protected String extractId(final URI uri) {
    if (uri instanceof SipURI) {
      final SipURI suri = (SipURI) uri;
      return suri.getUser();
    }
    else if (uri instanceof TelURL) {
      final TelURL turi = (TelURL) uri;
      return turi.getPhoneNumber();
    }
    else {
      return uri.toString();
    }
  }

  // need call scope configuration instead of servlet scope configuration.
  protected Properties buildRecognitionProperties(final int timeout, final String confidence, final String mode) {
    final Properties properties = new Properties();
    properties.setProperty("recognizer-start-timers", Boolean.toString(false));
    properties.setProperty("dtmf-term-timeout", Configuration.get().getTermTimeout());
    properties.setProperty("dtmf-term-char", Configuration.get().getTermChar());
    properties.setProperty("No-Input-Timeout", String.valueOf(timeout));

    properties.setProperty("confidence-threshold", Integer.toString(Math.min(Math.round(Float
        .parseFloat(confidence == null || confidence.length() == 0 ? Configuration.get().getConfidenceLevel()
            : confidence) * 100f), 100)));
    properties.setProperty("sensitivity-level", Integer.toString(Math.min(Math.round(Float.parseFloat(Configuration
        .get().getSensitivity()) * 100f), 100)));
    properties.setProperty("speed-vs-accuracy", Integer.toString(Math.min(Math.round(Float.parseFloat(Configuration
        .get().getSpeedVsAccuracy()) * 100f), 100)));
    properties.setProperty("n-best-list-length", Integer.toString(1));
    properties.setProperty("Speech-Language", Configuration.get().getAsrSpeechLanguage());
    if ("dtmf".equals(mode)) {
      properties.setProperty("Voxeo-Input-Mode", "dtmf");
    }
    else if ("speech".equals(mode)) {
      properties.setProperty("Voxeo-Input-Mode", "voice");
    }
    else {
      properties.setProperty("Voxeo-Input-Mode", "dtmf voice");
    }
    return properties;
  }

  protected Properties buildSpeakProperties(final boolean bargin) {
    final Properties properties = new Properties();
    properties.setProperty("Speech-Language", Configuration.get().getTtsSpeechLanguage());
    if (bargin) {
      properties.setProperty("Kill-On-Barge-In", "true");
    }
    else {
      properties.setProperty("Kill-On-Barge-In", "false");
    }
    return properties;
  }

  protected Properties buildCallRecordingProperties(String format, String publicKey, String publicKeyUri) {
    Properties props = new Properties();
    if (format != null && format.length() > 0) {
      props.put("Voxeo-Record-Call-Format", format);
    }
    if (publicKey != null && publicKey.length() > 0) {
      props.put("Voxeo-Record-Call-Public-Key", publicKey);
    }
    if (publicKeyUri != null && publicKeyUri.length() > 0) {
      props.put("Voxeo-Record-Call-Public-Key-Uri", publicKeyUri);
    }
    return props;
  }
  
  protected void assertReady(final String action, final State readyState) {
    lock();
    try {
      if (getState() != readyState) {
        throw new FatalException(this + " cannot " + action + " when state is " + getState());
      }
    }
    finally {
      unlock();
    }
  }

  protected void handleException(final Throwable t, final String method) throws ErrorException, FatalException {
    if (t instanceof ErrorException) {
      throw (ErrorException) t;
    }
    else if (t instanceof FatalException) {
      LOG.error(this + "->" + method + "() got system error: " + t.getMessage());
      throw (FatalException) t;
    }
    else {
      LOG.error(this + "->" + method + "() got system error: " + t.getMessage());
      throw new FatalException(t);
    }
  }

  private Map<String, String> getReturnValue(final MrcpAsrResult result) {
    final Map<String, String> retval = new HashMap<String, String>();
    final String value = result.getValue();
    if (value != null) {
      retval.put("value", value);
    }
    if (result.getConfidence() >= 0) {
      retval.put("confidence", String.valueOf(result.getConfidence()));
    }
    final String xml = result.getXML();
    if (xml != null) {
      retval.put("xml", xml);
    }
    final String concept = result.getConcept();
    if (concept != null) {
      retval.put("concept", concept);
    }
    final String interpretation = result.getInterpretation();
    if (interpretation != null) {
      retval.put("interpretation", interpretation);
    }
    final String utterance = result.getUtterance();
    if (utterance != null) {
      retval.put("utterance", utterance);
    }
    if (result.getRecording() != null) {
      retval.put("recordURL", result.getRecording().getURL());
    }
    return retval;
  }

  public String getHeader(final String name) {
    return _invite.getHeader(name);
  }

  public String getId() {
    return _id;
  }

}
