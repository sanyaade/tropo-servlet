package com.voxeo.tropo.app;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.annotation.SipListener;

import org.apache.log4j.Logger;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

import com.voxeo.tropo.ErrorException;
import com.voxeo.tropo.FatalException;
import com.voxeo.tropo.core.Call;
import com.voxeo.tropo.core.CallImpl;
import com.voxeo.tropo.core.IncomingCall;
import com.voxeo.tropo.core.SimpleCallFactory;
import com.voxeo.tropo.core.SimpleIncomingCall;
import com.voxeo.tropo.thrift.AuthenticationException;
import com.voxeo.tropo.thrift.BindException;
import com.voxeo.tropo.thrift.NetworkException;
import com.voxeo.tropo.thrift.PromptStruct;
import com.voxeo.tropo.thrift.ShimCall;
import com.voxeo.tropo.thrift.ShimService;
import com.voxeo.tropo.thrift.SystemException;
import com.voxeo.tropo.thrift.UserException;
import com.voxeo.tropo.util.Utils;

@SipListener
public class ThriftApplication extends AbstractApplication implements RemoteApplication, Serializable {
  private static final Logger LOG = Logger.getLogger(ThriftApplication.class);

  private static final long serialVersionUID = 6698570731791945838L;
  
  public static final String CALL_FACTORY = "com.voxeo.tropo.thrift.callFactory";
  
  protected List<SipURI> _contacts;
  
  protected transient ThriftURL _url;
  
  protected String _key;
  
  protected transient ShimService.Client _shim;
  
  protected transient TTransport _transport;
    
  protected transient Map<String, Call> _calls;
  
  protected transient String _token;
  
  protected transient Map<String, SipApplicationSession> _sessions;
  

  public ThriftApplication(final RemoteApplicationManager mgr, final ThriftURL url, final int aid, final String appId, final List<SipURI> contacts) {
    super(mgr, url, "Thrift", aid, appId, null);
    _contacts = contacts;
    _calls = new ConcurrentHashMap<String, Call>();
    _key = Utils.getGUID(); 
  }
  
  public void dispose() {
    for(Call call : _calls.values()) {
      call.hangup();
    }
    _calls = null;
    _transport.close();
    _shim = null;
  }
  
  public synchronized ShimService.Client getShimService() throws NetworkException, AuthenticationException, UserException, SystemException, TException {
    if (_shim == null) {
      _transport = new TSocket(_url.getHost(), _url.getPort());
      TBinaryProtocol binaryProtocol = new TBinaryProtocol(_transport);
      ShimService.Client shim = new ShimService.Client(binaryProtocol);
      try {
        _token = shim.bind(_url.getAuthority());
        _shim = shim;
        LOG.info(shim + " is connectioned.");
      }
      catch(BindException e) {
        //TODO: rebind
      }
    }
    return _shim;
  }

  public void execute(SipServletRequest invite) throws ScriptException, IOException {
    SipApplicationSession appSession = invite.getApplicationSession();
    SimpleCallFactory factory = new SimpleCallFactory(this, appSession);
    appSession.setAttribute(CALL_FACTORY, factory);
    CallImpl call = new SimpleIncomingCall(factory, invite, this);
    try {
      ShimService.Client shim = getShimService();
      ShimCall shimCall = new ShimCall(call.getId(), call.getCallerId(), call.getCallerName(), call.getCalledId(), call.getCalledName());
      shim.alert(_token, shimCall);
      _calls.put(call.getId(), call);
      _sessions.put(appSession.getId(), appSession);
      LOG.info(shim + " is alerted.");
    }
    catch(NetworkException e) {
      throw new IOException(e);
    }
    catch(TException e) {
      throw new IOException(e);
    }
    catch(SystemException e) {
      throw new ScriptException(e);
    }
    catch(UserException e) {
      throw new ScriptException(e);
    }
    catch(AuthenticationException e) {
      throw new IOException(e);
    }
  }

  public void execute(HttpServletRequest invite) throws ScriptException, IOException {
  }

  public boolean isProxy() {
    return _mgr == null;
  }

  public List<SipURI> getContacts() {
    return _contacts;
  }

  public String getApplicationKey() {
    return _key;
  }
  
  void answer(String id, int timeout) throws UserException, SystemException {
    Call call = _calls.get(id);
    if (call == null) {
      throw new UserException("Invalid call id: " + id);
    }
    if (!(call instanceof IncomingCall)) {
      throw new UserException(call + " can not be answered.");
    }
    try {
      ((IncomingCall)call).answer(timeout);
    }
    catch(ErrorException e) {
      throw new UserException(e.toString());
    }
    catch(FatalException e) {
      throw new SystemException(e.toString());
    }
  }
  
  void reject(String id) throws UserException, SystemException {
    Call call = _calls.get(id);
    if (call == null) {
      throw new UserException("Invalid call id: " + id);
    }
    if (!(call instanceof IncomingCall)) {
      throw new UserException(call + " can not be answered.");
    }
    try {
      ((IncomingCall)call).reject();
    }
    catch(ErrorException e) {
      throw new UserException(e.toString());
    }
    catch(FatalException e) {
      throw new SystemException(e.toString());
    }    
  }
  
  Map<String, String> prompt(String id, PromptStruct prompt) throws UserException, SystemException {
    Call call = _calls.get(id);
    if (call == null) {
      throw new UserException("Invalid call id: " + id);
    }
    try {
      return ((IncomingCall)call).prompt(prompt.getTtsOrUrl(), prompt.isBargein(), prompt.getGrammar(), prompt.getConfidence(), prompt.getMode(), prompt.getWait());
    }
    catch(ErrorException e) {
      throw new UserException(e.toString());
    }
    catch(FatalException e) {
      throw new SystemException(e.toString());
    }    
  }

  public boolean isActive() {
    // TODO Auto-generated method stub
    return false;
  }
}
