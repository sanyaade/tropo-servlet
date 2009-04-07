package com.voxeo.tropo.app;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.script.ScriptException;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.TelURL;
import javax.servlet.sip.URI;
import javax.xml.ws.soap.SOAPFaultException;

import org.apache.log4j.Logger;
import org.apache.thrift.TException;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;

import com.micromethod.common.util.StringUtils;
import com.voxeo.tropo.core.CallImpl;
import com.voxeo.tropo.thrift.AlertStruct;
import com.voxeo.tropo.thrift.AuthenticationException;
import com.voxeo.tropo.thrift.BindException;
import com.voxeo.tropo.thrift.BindStruct;
import com.voxeo.tropo.thrift.HangupStruct;
import com.voxeo.tropo.thrift.PromptStruct;
import com.voxeo.tropo.thrift.SystemException;
import com.voxeo.tropo.thrift.TransferStruct;
import com.voxeo.tropo.thrift.TropoException;
import com.voxeo.tropo.thrift.TropoService;
import com.voxeo.tropo.util.Utils;
import com.voxeo.webcore.dns.URLByNumberGet;
import com.voxeo.webcore.dns.URLByTokenGet;
import com.voxeo.webcore.dns.VoxeoDNSException;

public class ThriftAppMgr extends AbstractRemoteApplicationManager implements Runnable, TropoService.Iface {
  private static final Logger LOG = Logger.getLogger(ThriftAppMgr.class);
  
  static final String DEAULT_APPLICATION_ID = "*";
  
  protected ConcurrentMap<Integer, ConcurrentMap<String, RemoteApplication>> _apps;
  
  protected Map<String, RemoteApplication> _index;
  
  protected Map<String, ApplicationInstance> _insts;
    
  protected TServer _server;
  
  protected TServerTransport _transport;
  
  protected List<SipURI> _contacts;
  
  protected int _serverPort = 9090;
  
  protected ApplicationCollector _collector;
  
  protected String _authenticationServer = "http://evolution-internal.voxeo.com/services/AccountManagement?wsdl";

  @Override
  protected Application findApplication(URI uri) throws InvalidApplicationException, RedirectException {
    String name = null;
    if (uri instanceof SipURI) {
        name = ((SipURI) uri).getUser();
    }
    else if (uri instanceof TelURL) {
        name = ((TelURL) uri).getPhoneNumber();
    }
    else {
        throw new InvalidApplicationException("Only SIP URI or Tel URL is supported: " + uri);
    }
    
    try {
      URLByNumberGet info = new URLByNumberGet();
      info.execute(name);
      RemoteApplication app = null;
      try {
        app = getApplication(info.getAccountID(), info.getApplicationID());
      }
      catch(Exception e) {
        throw new InvalidApplicationException(e);
      }
      
      if (app != null) {
        if (!app.isProxy()) {
          LOG.info(this + " found remote " + app + " for " + uri);
          return app;
        }
        else {
          throw new RedirectException(app.getContacts());
        }
      }
      throw new InvalidApplicationException("No application has been bound for " + uri);
    }
    catch(VoxeoDNSException e) {
      LOG.error(e.toString(), e);
      throw new InvalidApplicationException("Unable to retrieve DNS records for " + uri);
    }
  }
  
  @Override
  public String toString() {
    return "Tropo(Remote Edition)";
  }

  @Override
  protected Application findApplication(String token, Properties params) throws InvalidApplicationException, RedirectException {
    URLByTokenGet info = new URLByTokenGet();
    info.execute(token);
    RemoteApplication app = null;
    try {
      app = getApplication(info.getAccountID(), info.getApplicationID());
    }
    catch(Exception e) {
      throw new InvalidApplicationException(e);
    }
    if (app != null) {
      if (!app.isProxy()) {
        LOG.info(this + " found remote " + app + " for " + token);
        return app;
      }
      else {
        throw new RedirectException(app.getContacts());
      }
    }
    throw new InvalidApplicationException("No application has been bound for " + token);
  }
    
  @SuppressWarnings("unchecked")
  @Override
  public void init(final ServletContext context, final Map<String, String> paras) {
    String wsdl = paras.remove("authenticationServer");
    if (wsdl != null && wsdl.length() > 0) {
      _authenticationServer = wsdl.trim();
    }
    String sport = paras.remove("thriftServicePort");
    if (sport != null && sport.length() > 0) {
      _serverPort = Integer.parseInt(sport);
    }
    super.init(context, paras);
    _contacts = (List<SipURI>) context.getAttribute("javax.servlet.sip.outboundInterfaces");
    _apps = new ConcurrentHashMap<Integer, ConcurrentMap<String, RemoteApplication>>();
    _index = new ConcurrentHashMap<String, RemoteApplication>();
    try {
      _transport = new TServerSocket(_serverPort);
      _server = new TThreadPoolServer(new TropoService.Processor(this), _transport);
      new Thread(this, "Thrift").start();
      _collector = new ApplicationCollector();
      new Thread(_collector, "ApplicationCollector").start();
    }
    catch(Exception e) {
      LOG.error(e.toString(), e);
      throw new RuntimeException(e);
    }
  }
  
  @Override
  public void dispose() {
    _transport.close();
    _server.stop();
    _collector.stop();
    _apps.clear();
    super.dispose();
  }

  public static synchronized void initialization(final ServletContext ctx) throws InstantiationException, IllegalAccessException {
    AbstractApplicationManager.initialization(ctx);
  }

  public void run() {
    LOG.info("Starting Thrift service on port " + _serverPort);
    _server.serve();
    LOG.info("Stopping Thrift service on port " + _serverPort);
  }

  public void answer(String key, String id, int timeout) throws AuthenticationException, TropoException, SystemException, TException {
    RemoteApplication app = _index.get(key);
    if (app != null && app instanceof ThriftApplication) {
      ((ThriftApplication)app).answer(id, timeout);
    }
    else {
      throw new AuthenticationException("Invalid key: " + key);
    }
  }
  
  protected void authenticate(BindStruct bind) throws AuthenticationException, SystemException {
    if (bind.getAccountId() <= 0 || StringUtils.isEmpty(bind.getApplicationId())) {
      throw new SystemException("Invalid account or application.");
    }
    try {
      String token = Utils.authenticate(_authenticationServer, bind.getUser(), bind.getPassword());
    }
    catch (SOAPFaultException e) {
      if (e.getMessage().contains("Invalid login")) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Invalid username[" + bind.getUser() + "] or password[" + bind.getPassword() + "]");
        }
        throw new AuthenticationException("Invalid username[" + bind.getUser() + "] or password[" + bind.getPassword() + "]");
      }
      else {
        LOG.error(e.toString(), e);
        throw new SystemException(e.getMessage());
      }
    }
    catch (Exception e) {
      LOG.error(e.toString(), e);
      throw new SystemException(e.getMessage());
    }
  }

  public String bind(BindStruct bind) throws AuthenticationException, BindException, TException, SystemException {
    Utils.setLogContext(String.valueOf(bind.getAccountId()), "-1", "-1", "-1");
    if (LOG.isDebugEnabled()) {
      LOG.debug("bind-->" + bind.toString());
    }
    authenticate(bind);
    int accountId = bind.getAccountId();
    String applicationId = bind.getApplicationId();
    RemoteApplication oldapp = getApplication(accountId, applicationId);
    RemoteApplication newapp = new ThriftApplication(this, new ThriftURL(bind), accountId, applicationId, _contacts);
    LOG.info(newapp.toString() + " has been created by " + bind.toString());
    putApplication(newapp);
    if (oldapp != null) {
      removeApplication(oldapp);
      oldapp.dispose();      
    }
    return newapp.getApplicationKey();
  }
  
  protected RemoteApplication getApplication(int accountId, String applicationId) throws AuthenticationException, SystemException, TException {
    ConcurrentMap<String, RemoteApplication> map = _apps.get(accountId);
    if (map == null) {
      map = new ConcurrentHashMap<String, RemoteApplication>();
      _apps.putIfAbsent(accountId, map);
    }
    RemoteApplication app =  map.get(applicationId);
    if (app == null) {
      app = map.get(DEAULT_APPLICATION_ID);
      if (app != null && app instanceof ThriftApplication) {
        app = new ThriftApplication((ThriftApplication)app, applicationId);
      }
    }
    return app;
  }

  protected void putApplication(RemoteApplication app) {
    ConcurrentMap<String, RemoteApplication> map = _apps.get(app.getAccountID());
    if (map == null) {
      map = new ConcurrentHashMap<String, RemoteApplication>();
      _apps.putIfAbsent(app.getAccountID(), map);
    }
    map.put(app.getApplicationID(), app);
    _index.put(app.getApplicationKey(), app);
    clearCached(app);
  }
  
  protected void removeApplication(RemoteApplication app) {
    ConcurrentMap<String, RemoteApplication> map = _apps.get(app.getAccountID());
    if (map != null) {      
      map.remove(app.getApplicationID(), app);
    }
    _index.remove(app.getApplicationKey());
    clearCached(app);
  }
  
  public Map<String, String> prompt(String key, String id, PromptStruct prompt) throws AuthenticationException, TropoException, SystemException, TException {
    RemoteApplication app = _index.get(key);
    if (app != null && app instanceof ThriftApplication) {
      return ((ThriftApplication)app).prompt(id, prompt);
    }
    else {
      throw new AuthenticationException("Invalid key: " + key);
    }
  }

  public void redirect(String key, String id, String number) throws TropoException, AuthenticationException, SystemException, TException {
    RemoteApplication app = _index.get(key);
    if (app != null && app instanceof ThriftApplication) {
      ((ThriftApplication)app).redirect(id, number);
    }
    else {
      throw new AuthenticationException("Invalid key: " + key);
    }
  }

  public void reject(String key, String id) throws AuthenticationException, TropoException, SystemException, TException {
    RemoteApplication app = _index.get(key);
    if (app != null && app instanceof ThriftApplication) {
      ((ThriftApplication)app).reject(id);
    }
    else {
      throw new AuthenticationException("Invalid key: " + key);
    }
  }

  public void unbind(String token) throws TException {
    RemoteApplication app = _index.get(token);
    if (app != null) {
      Utils.setLogContext(app, null);
      LOG.info(app + " is disconnecting.");
      removeApplication(app);
      app.dispose();
    }
  }

  public void heartbeat(String token) throws AuthenticationException, SystemException, TException {
    RemoteApplication app = _index.get(token);
    if (app == null) {
      throw new AuthenticationException();
    }
  }

  public void execute(SipServletRequest req, RemoteApplication app) throws IOException, ScriptException {
    if (app instanceof ThriftApplication) {
      try {
        ((ThriftApplication)app)._execute(req);
      }
      catch(TException e) {
        removeApplication(app);
        throw new IOException(e);
      }
      catch(AuthenticationException e) {
        throw new ScriptException(e);
      }
      catch(SystemException e) {
        throw new ScriptException(e);
      }
      catch(TropoException e) {
        throw new ScriptException(e);        
      }
    }
  }

  public void execute(HttpServletRequest req, RemoteApplication app) throws IOException, ScriptException {
    if (app instanceof ThriftApplication) {
      try {
        ((ThriftApplication)app)._execute(req);
      }
      catch(TException e) {
        removeApplication(app);
        throw new IOException(e);
      }
      catch(AuthenticationException e) {
        throw new ScriptException(e);
      }
      catch(SystemException e) {
        throw new ScriptException(e);
      }
      catch(TropoException e) {
        throw new ScriptException(e);        
      }
    }
  }
  
  class ApplicationCollector implements Runnable {
    boolean _stop = false;
    Thread _current;

    public void run() {
      _current = Thread.currentThread();
      while(!_stop) {
        Collection<RemoteApplication> dead = new LinkedList<RemoteApplication>();
        for (RemoteApplication app : _index.values()) {
          if (!app.isActive()) {
            dead.add(app);
          }
        }
        if (dead.size() > 0) {
          for (RemoteApplication app : dead) {
            LOG.info("Removing dead " + app);
            app.dispose();
            removeApplication(app);
          }
        }
        try {
          synchronized(this) {
            this.wait(5000);
          }
        }
        catch(InterruptedException e) {
          //ignore
        }
      }
    }
    
    void stop() {
      _stop = true;
      _current.interrupt();
    }
    
  }

  public void block(String key, String id, int timeout) throws AuthenticationException, TropoException, SystemException, TException {
    RemoteApplication app = _index.get(key);
    if (app != null && app instanceof ThriftApplication) {
      ((ThriftApplication)app).block(id, timeout);
    }
    else {
      throw new AuthenticationException("Invalid key: " + key);
    }
  }

  public AlertStruct call(String key, String from, String to, boolean answerOnMedia, int timeout) throws AuthenticationException, TropoException, SystemException, TException {
    RemoteApplication app = _index.get(key);
    if (app != null && app instanceof ThriftApplication) {
      CallImpl call = ((ThriftApplication)app).call(from, to, answerOnMedia, timeout);
      return new AlertStruct(call.getId(), app.getApplicationID(), call.getCallerId(), call.getCallerName(), call.getCalledId(), call.getCalledName());
    }
    else {
      throw new AuthenticationException("Invalid key: " + key);
    }
  }

  public HangupStruct hangup(String key, String id) throws AuthenticationException, TropoException, SystemException, TException {
    RemoteApplication app = _index.get(key);
    if (app != null && app instanceof ThriftApplication) {
      return ((ThriftApplication)app).hangup(id);
    }
    else {
      throw new AuthenticationException("Invalid key: " + key);
    }
  }

  public void log(String key, String id, String msg) throws AuthenticationException, TropoException, SystemException, TException {
    RemoteApplication app = _index.get(key);
    if (app != null && app instanceof ThriftApplication) {
      ((ThriftApplication)app).log(id, msg);
    }
    else {
      throw new AuthenticationException("Invalid key: " + key);
    }
  }

  public AlertStruct transfer(String key, String id, TransferStruct t) throws AuthenticationException, TropoException, SystemException, TException {
    RemoteApplication app = _index.get(key);
    if (app != null && app instanceof ThriftApplication) {
      CallImpl call = ((ThriftApplication)app).transfer(id, t);
      return new AlertStruct(call.getId(), app.getApplicationID(), call.getCallerId(), call.getCallerName(), call.getCalledId(), call.getCalledName());
    }
    else {
      throw new AuthenticationException("Invalid key: " + key);
    }
  }
}
