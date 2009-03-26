package com.voxeo.tropo.remote.impl;

import java.util.Map;
import java.util.Properties;

import com.voxeo.tropo.remote.Call;
import com.voxeo.tropo.remote.TropoEvent;
import com.voxeo.tropo.remote.TropoListener;
import com.voxeo.tropo.thrift.AlertStruct;
import com.voxeo.tropo.thrift.PromptStruct;
import com.voxeo.tropo.thrift.TropoService;

public class TropoCall implements Call {
  protected AlertStruct _alert;
  
  protected TropoService.Iface _tropo;
  
  protected String _key;
  
  protected TropoCall(TropoService.Iface tropo, String key, AlertStruct alert) {
    _alert = alert;
    _tropo = tropo;
    _key = key;
  }

  public TropoEvent ask(String tts, Properties props, TropoListener listener) {
    return prompt(tts, props, listener);
  }

  public TropoEvent prompt(String tts, Properties props, TropoListener listener) {
    PromptStruct prompt = null;
    if (props != null) {
       prompt = new PromptStruct();
      
    }
    try {
      Map<String, String> result = _tropo.prompt(_key, _alert.getId(), prompt);
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

}
