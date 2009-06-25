package com.voxeo.tropo.core;

import java.util.Map;

public interface Call {
  enum State {
    RINGING, ANSWERING, ANSWERED, REJECTING, REJECTED, DISCONNECTED, FAILED, REDIRECTING, REDIRECTED
  }

  Map<String, String> prompt(String ttsOrUrl, boolean bargein, String grammar, String confidence, String mode, int wait);

  void startCallRecording(String filenameOrUrl, String format, String publicKey, String publicKeyUri);
  
  void stopCallRecording();
  
  void hangup();

  Call transfer(String to, String from, boolean answerOnMedia, int timeout, String ttrOrUrl, int repeat, String grammar);

  String getCallerId();

  String getCalledId();

  String getCallerName();

  String getCalledName();

  void log(Object msg);

  void block(int milliseconds);
  
  String getHeader(String name);
}
