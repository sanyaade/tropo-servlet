package com.voxeo.tropo.util;

import java.util.Properties;

import com.mot.mrcp.MrcpException;
import com.voxeo.tropo.Configuration;
import com.voxeo.sipmethod.mrcp.client.MrcpSession;
import com.voxeo.sipmethod.mrcp.client.MrcpTtsResult;
import com.voxeo.sipmethod.mrcp.client.MrcpTtsSession;

public class MrcpBackgroundAudioPlayer {

  public static MrcpBackgroundAudioPlayer create(final MrcpTtsSession session) {
    return new MrcpBackgroundAudioPlayer(session);
  }

  protected MrcpTtsSession _session;

  protected AudioThread _audioThread;

  private MrcpBackgroundAudioPlayer(final MrcpTtsSession session) {
    _session = session;
  }

  public synchronized void start(final String tts, final int repeat) {
    if (_audioThread == null) {
      _audioThread = new AudioThread(tts, repeat);
      _audioThread.setName(Thread.currentThread().getName() + "-Player");
      _audioThread.start();
    }
  }

  public synchronized void stop() {
    if (_audioThread != null) {
      _audioThread.close();
      _audioThread = null;
    }
  }

  protected Properties buildSpeakProperties() {
    final Properties properties = new Properties();
    properties.setProperty("Speech-Language", Configuration.get().getTtsSpeechLanguage());
    properties.setProperty("Kill-On-Barge-In", String.valueOf(true));
    return properties;
  }

  private class AudioThread extends Thread {
    private final String _ttsOrUrl;

    private int _repeat = 1;

    private boolean _isStarted = false;

    AudioThread(final String url, final int repeat) {
      _ttsOrUrl = url;
      _repeat = repeat;
    }

    public void close() {
      if (_isStarted) {
        _isStarted = false;
        try {
          if (_session.getState() == MrcpSession.State.CONNECTED) {
            _session.stop(new Properties());
          }
        }
        catch (final MrcpException e) {
          ;
        }
      }
    }

    @Override
    public void run() {
      if (_isStarted) {
        return;
      }
      try {
        _isStarted = true;
        MrcpTtsResult result = null;
        for (int count = 0; (result == null || result.getReturnCode() == MrcpTtsResult.OK && count < _repeat)
            && _isStarted && _session.getState() == MrcpSession.State.CONNECTED; count++) {
          result = _session.speak(Utils.genSSML(_ttsOrUrl, Configuration.get().isParseSpeechText()),
              buildSpeakProperties(), Configuration.get().getMaxTimeSpeak());
        }
      }
      catch (final Exception e) {
        ;
      }
    }
  }
}
