package com.voxeo.tropo.remote.impl;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.voxeo.tropo.remote.Call;
import com.voxeo.tropo.remote.CallListener;
import com.voxeo.tropo.remote.TropoEvent;
import com.voxeo.tropo.thrift.AlertStruct;
import com.voxeo.tropo.thrift.HangupStruct;
import com.voxeo.tropo.thrift.PromptStruct;

public class TropoCall implements Call {
  protected static Map<String, Call> _calls = new ConcurrentHashMap<String, Call>();
  
  protected AlertStruct _alert;
  
  protected TropoCloud _tropo;
  
  protected TropoCall(TropoCloud tropo, AlertStruct alert) {
    _alert = alert;
    _tropo = tropo;
    _calls.put(_alert.getId(), this);
  }

  public TropoEvent ask(String tts, Properties props, CallListener listener) {
    return prompt(tts, props, listener);
  }

  public TropoEvent prompt(String tts, Properties props, CallListener listener) {
    PromptStruct prompt = null;
    if (props != null) {
       prompt = new PromptStruct();
      
    }
    try {
      Map<String, String> result = _tropo.prompt(_alert.getId(), prompt);
      TropoEvent event; // TODO: generating event
      if (listener != null) {
        //TODO call listener
      }
    }
    catch(Exception e) {
      if (listener != null) {
        listener.onError();
      }
    }
    return null;
  }

  public TropoEvent say(String tts) {
    return prompt(tts, null, null);
  }

  public String getCalledID() {
    return _alert.getCalledID();
  }

  public String getCalledName() {
    return _alert.getCalledName();
  }

  public String getCallerID() {
    return _alert.getCallerID();
  }

  public String getCallerName() {
    return _alert.getCallerName();
  }

  public void hangup() {
    try {
      HangupStruct info = _tropo.hangup(_alert.getId());
      _calls.remove(this);
    }
    catch(Exception e) {
      //TODO
    }
    
  }

}
