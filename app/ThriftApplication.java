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
import com.voxeo.tropo.thrift.AlertStruct;
import com.voxeo.tropo.thrift.AuthenticationException;
import com.voxeo.tropo.thrift.BindException;
import com.voxeo.tropo.thrift.HangupStruct;
import com.voxeo.tropo.thrift.Notifier;
import com.voxeo.tropo.thrift.PromptStruct;
import com.voxeo.tropo.thrift.SystemException;
import com.voxeo.tropo.thrift.TransferStruct;
import com.voxeo.tropo.thrift.TropoException;
import com.voxeo.tropo.util.Utils;

@SipListener
public class ThriftApplication extends AbstractApplication implements RemoteApplication, Serializable {
  private static final Logger LOG = Logger.getLogger(ThriftApplication.class);

  private static final long serialVersionUID = 6698570731791945838L;
  
  public static final String CALL_FACTORY = "com.voxeo.tropo.thrift.callFactory";
  
  protected List<SipURI> _contacts;
    
  protected String _key;
  
  protected transient Notifier.Client _notifier;
  
  protected transient TTransport _transport;
    
  protected transient Map<String, Call> _calls;
  
  protected transient String _token;
  
  protected transient int _beats;    

  public ThriftApplication(final RemoteApplicationManager mgr, final ThriftURL url, final int aid, final String appId, final List<SipURI> contacts) {
    super(mgr, url, "Thrift", aid, appId, null);
    _contacts = contacts;
    _calls = new ConcurrentHashMap<String, Call>();
    _key = Utils.getGUID(); 
  }
  
  public ThriftApplication(final ThriftApplication shared, final String appId) throws AuthenticationException, SystemException, TException {
    super(shared.getManager(), shared.getURL(), "Thrift", shared.getAccountID(), appId, null);
    _contacts = shared.getContacts();
    _calls = new ConcurrentHashMap<String, Call>();
    _key = Utils.getGUID();
    _notifier = shared.getNotifier();
  }

  public synchronized void dispose() {
    if (_calls != null) {
      for(Call call : _calls.values()) {
        call.hangup();
        if (_notifier != null) {
          try {
            _notifier.hangup(_token, ((CallImpl)call).getId(), new HangupStruct());
          }
          catch(Exception e) {
            //ignore
          }
        }
      }
      _calls = null;
    }
    if (_transport != null && _notifier  != null) {
      try {
        _notifier.unbind(_token);
      }
      catch(Throwable t) {
        //ignore
      }
      _notifier = null;
      _transport.close();
    }
  }
  
  protected synchronized Notifier.Client getNotifier() throws AuthenticationException, SystemException, TException {
    if (_notifier == null) {
      _transport = new TSocket(_url.getHost(), _url.getPort());
      TBinaryProtocol binaryProtocol = new TBinaryProtocol(_transport);
      Notifier.Client notifier = new Notifier.Client(binaryProtocol);
      _transport.open();
      try {
        _token = notifier.bind(_url.getAuthority());
        _notifier = notifier;
        LOG.info(notifier + " is connectioned.");
      }
      catch(BindException e) {
        //TODO: rebind
      }
    }
    return _notifier;
  }

  public void execute(SipServletRequest invite) throws ScriptException, IOException {
    ((RemoteApplicationManager)getManager()).execute(invite, this);
  }
  
  protected void _execute(SipServletRequest invite) throws TException, TropoException, SystemException, AuthenticationException {
    SipApplicationSession appSession = invite.getApplicationSession();
    SimpleCallFactory factory = new SimpleCallFactory(this, appSession);
    appSession.setAttribute(CALL_FACTORY, factory);
    CallImpl call = new SimpleIncomingCall(factory, invite, this);
    AlertStruct alert = new AlertStruct(call.getId(), getApplicationID(), call.getCallerId(), call.getCallerName(), call.getCalledId(), call.getCalledName());
    _calls.put(call.getId(), call);
    getNotifier().alert(_token, alert);
  }

  public void execute(HttpServletRequest invite) throws ScriptException, IOException {
    ((RemoteApplicationManager)getManager()).execute(invite, this);
  }
  
  protected void _execute(HttpServletRequest invite) throws TException, TropoException, SystemException, AuthenticationException {
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
  
  Call getCall(String id) throws TropoException {
    Call call = _calls.get(id);
    if (call == null) {
      throw new TropoException("Invalid call id: " + id);
    }
    return call;
  }
  
  void answer(String id, int timeout) throws TropoException, SystemException {
    Call call = getCall(id);
    try {
      ((IncomingCall)call).answer(timeout);
    }
    catch(ClassCastException e) {
      throw new TropoException(call + " can not be answered.");     
    }
    catch(ErrorException e) {
      throw new TropoException(e.toString());
    }
    catch(FatalException e) {
      throw new SystemException(e.toString());
    }
  }
  
  void reject(String id) throws TropoException, SystemException {
    Call call = getCall(id);
    try {
      ((IncomingCall)call).reject();
    }
    catch(ClassCastException e) {
      throw new TropoException(call + " can not be rejected.");     
    }
    catch(ErrorException e) {
      throw new TropoException(e.toString());
    }
    catch(FatalException e) {
      throw new SystemException(e.toString());
    }    
  }
  
  HangupStruct hangup(String id) throws TropoException, SystemException {
    Call call = getCall(id);
    try {
      call.hangup();
      return new HangupStruct(); //TODO: 
    }
    catch(ErrorException e) {
      throw new TropoException(e.toString());
    }
    catch(FatalException e) {
      throw new SystemException(e.toString());
    }    
  }

  void log(String id, String msg) throws TropoException, SystemException {
    Call call = getCall(id);
    try {
      call.log(msg);
    }
    catch(ErrorException e) {
      throw new TropoException(e.toString());
    }
    catch(FatalException e) {
      throw new SystemException(e.toString());
    }    
  }
  
  CallImpl transfer(String id, TransferStruct t) throws TropoException, SystemException {
    Call call = getCall(id);
    try {
      return (CallImpl)call.transfer(t.getTo(), t.getFrom(), t.isAnswerOnMedia(), t.getTimeout(), t.getTtsOrUrl(), t.getRepeat(), t.getGrammar());
    }
    catch(ErrorException e) {
      throw new TropoException(e.toString());
    }
    catch(FatalException e) {
      throw new SystemException(e.toString());
    }    
  }
  
  Map<String, String> prompt(String id, PromptStruct prompt) throws TropoException, SystemException {
    Call call = getCall(id);
    try {
      Map<String, String> result = call.prompt(prompt.getTtsOrUrl(), prompt.isBargein(), prompt.getGrammar(), prompt.getConfidence(), prompt.getMode(), prompt.getWait());
      // System.out.println(result);
      if (LOG.isDebugEnabled()) {
        LOG.debug(result.toString());
      }
      return result;
    }
    catch(ErrorException e) {
      System.out.println(e.toString());
      throw new TropoException(e.toString());
    }
    catch(FatalException e) {
      throw new SystemException(e.toString());
    }    
  }
  
  void redirect(String id, String number) throws TropoException, SystemException {
    Call call = getCall(id);
    try {
      ((IncomingCall)call).redirect(number);
    }
    catch(ClassCastException e) {
      throw new TropoException(call + " can not be answered.");     
    }
    catch(ErrorException e) {
      throw new TropoException(e.toString());
    }
    catch(FatalException e) {
      throw new SystemException(e.toString());
    }        
  }

  void block(String id, int timeout) throws TropoException, SystemException {
    Call call = getCall(id);
    try {
      call.block(timeout);
    }
    catch(ErrorException e) {
      throw new TropoException(e.toString());
    }
    catch(FatalException e) {
      throw new SystemException(e.toString());
    }            
  }
  
  CallImpl call(String from, String to, boolean answerOnMedia, int timeout) throws TropoException, SystemException {
    SipApplicationSession appSession = getSipFactory().createApplicationSession();
    SimpleCallFactory factory = new SimpleCallFactory(this, appSession);
    appSession.setAttribute(CALL_FACTORY, factory);
    CallImpl call = factory.call(from, to, answerOnMedia, timeout);
    _calls.put(call.getId(), call);
    return call;
  }
  
  public boolean isActive() {
    try {
      getNotifier().heartbeat(_token);
      _beats = 0;
      return true;
    }
    catch(Throwable e) {
      _beats++;
      LOG.error(e);
      return _beats > 2? false : true;
    }
  }
}
