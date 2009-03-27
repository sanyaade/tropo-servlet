package com.voxeo.tropo.remote.impl;

import java.util.Map;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import com.voxeo.tropo.thrift.AlertStruct;
import com.voxeo.tropo.thrift.AuthenticationException;
import com.voxeo.tropo.thrift.BindException;
import com.voxeo.tropo.thrift.BindStruct;
import com.voxeo.tropo.thrift.PromptStruct;
import com.voxeo.tropo.thrift.SystemException;
import com.voxeo.tropo.thrift.TransferStruct;
import com.voxeo.tropo.thrift.TropoException;
import com.voxeo.tropo.thrift.TropoService;

public class TropoCloud implements Cloneable {
  String _host;
  int _port;
  TTransport _transport;
  TropoService.Iface _tropo;
  String _key;
  
  public TropoCloud(String host, int port, BindStruct bind) throws AuthenticationException, SystemException, TException {
    _transport = new TSocket(host, port);
    _tropo = new TropoService.Client(new TBinaryProtocol(_transport));
    _transport.open();
    try {
      _key = _tropo.bind(bind);      
    }
    catch(BindException e) {
      // rebind
    }
  }
  
  protected TropoCloud(String host, int port, String key) throws TTransportException {
    _host = host;
    _port = port;
    _transport = new TSocket(_host, _port);
    _transport.open();
    _tropo = new TropoService.Client(new TBinaryProtocol(_transport));;
    _key = key;
  }

  public void answer(String id, int timeout) throws AuthenticationException, TropoException, SystemException, TException {
    _tropo.answer(_key, id, timeout);
  }

  public String bind(BindStruct bind) throws AuthenticationException, BindException, SystemException, TException {
    return _tropo.bind(bind);
  }

  public void block(String id, int timeout) throws AuthenticationException, TropoException, SystemException, TException {
    _tropo.block(_key, id, timeout);
  }

  public AlertStruct call(String from, String to, boolean answerOnMedia, int timeout) throws AuthenticationException, TropoException, SystemException, TException {
    return _tropo.call(_key, from, to, answerOnMedia, timeout);
  }

  public void hangup(String id) throws AuthenticationException, TropoException, SystemException, TException {
    _tropo.hangup(_key, id);
  }

  public void heartbeat() throws AuthenticationException, SystemException, TException {
    _tropo.heartbeat(_key);
  }

  public void log(String id, String msg) throws AuthenticationException, TropoException, SystemException, TException {
    _tropo.log(_key, id, msg);
  }

  public Map<String, String> prompt(String id, PromptStruct prompt) throws AuthenticationException, TropoException, SystemException, TException {
    return _tropo.prompt(_key, id, prompt);
  }

  public void redirect(String id, String number) throws AuthenticationException, TropoException, SystemException, TException {
    _tropo.redirect(_key, id, number);
  }

  public void reject(String id) throws AuthenticationException, TropoException, SystemException, TException {
    _tropo.reject(_key, id);
  }

  public AlertStruct transfer(String id, TransferStruct t) throws AuthenticationException, TropoException, SystemException, TException {
    return _tropo.transfer(_key, id, t);
  }

  public void unbind() throws TException {
    _tropo.unbind(_key);
  }
  
  @Override
  public Object clone() {
    try {
      return new TropoCloud(_host, _port, _key);
    }
    catch(Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void disconnect() {
    _transport.close();
  }
}
