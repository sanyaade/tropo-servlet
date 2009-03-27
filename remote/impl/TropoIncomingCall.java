package com.voxeo.tropo.remote.impl;

import org.apache.thrift.TException;

import com.voxeo.tropo.remote.IncomingCall;
import com.voxeo.tropo.thrift.AlertStruct;
import com.voxeo.tropo.thrift.AuthenticationException;
import com.voxeo.tropo.thrift.SystemException;
import com.voxeo.tropo.thrift.TropoException;

public class TropoIncomingCall extends TropoCall implements IncomingCall {

  public TropoIncomingCall(TropoCloud tropo, AlertStruct alert) {
    super(tropo, alert);
    // TODO Auto-generated constructor stub
  }
  
  public void answer(int timeout) throws AuthenticationException, TropoException, SystemException, TException {
    _tropo.answer(_alert.getId(), timeout);
  }

}
