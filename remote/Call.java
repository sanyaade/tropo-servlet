package com.voxeo.tropo.remote;

import java.util.Properties;

public interface Call {
  String getCallerID();
  String getCallerName();
  String getCalledID();
  String getCalledName();
  TropoEvent say(String tts);
  TropoEvent ask(String tts, Properties props, TropoListener listener);
  TropoEvent prompt(String tts, Properties props, TropoListener listener);
}
