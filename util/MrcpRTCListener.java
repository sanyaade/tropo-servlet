package com.voxeo.tropo.util;

import java.util.Properties;

import com.mot.mrcp.MrcpException;
import com.mot.mrcp.ec.ConfidenceMode;
import com.voxeo.sipmethod.mrcp.client.Grammar;
import com.voxeo.sipmethod.mrcp.client.GrammarFactory;
import com.voxeo.sipmethod.mrcp.client.MrcpAsrResult;
import com.voxeo.sipmethod.mrcp.client.MrcpAsrSession;
import com.voxeo.sipmethod.mrcp.client.MrcpSession;
import com.voxeo.sipmethod.mrcp.client.Grammar.InputMode;
import com.voxeo.sipmethod.mrcp.client.MrcpAsrResult.Type;

public class MrcpRTCListener {

  public static MrcpRTCListener create(final MrcpAsrSession session, final int timeout) {
    return create(session, GrammarFactory.createGrammar("*, #", Grammar.Type.SIMPLE, InputMode.dtmf), timeout);
  }

  public static MrcpRTCListener create(final MrcpAsrSession session, final Grammar grammar, final int timeout) {
    return new MrcpRTCListener(session, grammar, timeout);
  }

  protected final Grammar _grammar;

  protected final int _timeout;

  protected final MrcpAsrSession _session;

  protected MrcpRTCCallback _callback = null;

  protected boolean _isStarted = false;

  protected WaitResultThread _waitThread = null;

  private MrcpRTCListener(final MrcpAsrSession session, final Grammar grammar, final int timeout) {
    _session = session;
    _grammar = grammar;
    _timeout = timeout;
  }

  public void setCallback(final MrcpRTCCallback callback) {
    _callback = callback;
  }

  public synchronized boolean isStarted() {
    return _isStarted;
  }

  public synchronized void start() {
    if (_waitThread == null) {
      _isStarted = true;
      _waitThread = new WaitResultThread();
      _waitThread.setName(Thread.currentThread().getName() + "-Listener");
      _waitThread.start();
    }
  }

  public synchronized void stop() {
    if (_waitThread != null) {
      _isStarted = false;
      _waitThread.close();
      _waitThread = null;
    }
  }

  protected Properties buildRecognitionProperties(final int noInputTimeout, final Grammar.InputMode mode) {
    final Properties properties = new Properties();
    properties.setProperty("recognizer-start-timers", Boolean.toString(false));
    // properties.setProperty("dtmf-term-timeout", String.valueOf(0));
    // properties.setProperty("dtmf-term-char", "#");
    properties.setProperty("No-Input-Timeout", String.valueOf(noInputTimeout));
    properties.setProperty("confidence-threshold", String.valueOf(100));
    properties.setProperty("sensitivity-level", String.valueOf(50));
    properties.setProperty("speed-vs-accuracy", String.valueOf(50));
    properties.setProperty("n-best-list-length", String.valueOf(1));
    properties.setProperty("Speech-Language", "en-us-prophecy");
    String vim = "dtmf voice";
    if (mode == InputMode.dtmf) {
      vim = "dtmf";
    }
    else if (mode == InputMode.voice) {
      vim = "voice";
    }
    properties.setProperty("Voxeo-Input-Mode", vim);
    return properties;
  }

  protected class WaitResultThread extends Thread {

    public void close() {
      try {
        if (_session.getState() == MrcpSession.State.CONNECTED) {
          _session.stop(new Properties());
        }
      }
      catch (final MrcpException e) {
        ; //ignore
      }
    }

    @Override
    public void run() {
      try {
        while (_isStarted && _session.getState() == MrcpSession.State.CONNECTED) {
          final MrcpAsrResult asrResult = _session.recognize(_grammar, buildRecognitionProperties(_timeout, _grammar
              .getInputMode()), _timeout, ConfidenceMode.INPUT);
          if (asrResult.getType() == Type.SUCCESS && asrResult.getValue() != null && asrResult.getValue().length() > 0) {
            synchronized (this) {
              _isStarted = false;
            }
            if (_callback != null) {
              _callback.invoke();
            }
          }
        }
      }
      catch (final Exception e) {
        return;
      }
    }
  }

}
