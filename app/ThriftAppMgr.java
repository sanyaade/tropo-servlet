package com.voxeo.tropo.app;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletContext;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.TelURL;
import javax.servlet.sip.URI;

import org.apache.log4j.Logger;
import org.apache.thrift.TException;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;

import com.micromethod.common.util.StringUtils;
import com.voxeo.tropo.thrift.AuthenticationException;
import com.voxeo.tropo.thrift.BindException;
import com.voxeo.tropo.thrift.BindStruct;
import com.voxeo.tropo.thrift.NetworkException;
import com.voxeo.tropo.thrift.PromptStruct;
import com.voxeo.tropo.thrift.SystemException;
import com.voxeo.tropo.thrift.TropoService;
import com.voxeo.tropo.thrift.UserException;
import com.voxeo.webcore.dns.URLByNumberGet;
import com.voxeo.webcore.dns.URLByTokenGet;
import com.voxeo.webcore.dns.VoxeoDNSException;

public class ThriftAppMgr extends AbstractRemoteApplicationManager implements Runnable, TropoService.Iface {
  private static final Logger LOG = Logger.getLogger(ThriftAppMgr.class);
  
  protected Map<Integer, Map<String, RemoteApplication>> _apps;
  
  protected Map<String, RemoteApplication> _index;
  
  protected Map<String, ApplicationInstance> _insts;
    
  protected TServer _server;
  
  protected List<SipURI> _contacts;
  
  protected int _serverPort = 9090;

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
      RemoteApplication app = getApplication(info.getAccountID(), info.getApplicationID());
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
    RemoteApplication app = getApplication(info.getAccountID(), info.getApplicationID());
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
    String sport = paras.remove("thriftServicePort");
    if (sport != null && sport.length() > 0) {
      _serverPort = Integer.parseInt(sport);
    }
    super.init(context, paras);
    _contacts = (List<SipURI>) context.getAttribute("javax.servlet.sip.outboundInterfaces");
    _apps = new ConcurrentHashMap<Integer, Map<String, RemoteApplication>>();
    _index = new ConcurrentHashMap<String, RemoteApplication>();
    try {
      _server = new TThreadPoolServer(new TropoService.Processor(this), new TServerSocket(_serverPort));
      new Thread(this, "Thrift").start();
    }
    catch(Exception e) {
      LOG.error(e.toString(), e);
      throw new RuntimeException(e);
    }
  }
  
  @Override
  public void dispose() {
    _server.stop();
    _apps = null;
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

  public void answer(String key, String id, int timeout) throws NetworkException, AuthenticationException, UserException, SystemException, TException {
    RemoteApplication app = _index.get(key);
    if (app != null && app instanceof ThriftApplication) {
      ((ThriftApplication)app).answer(id, timeout);
    }
    else {
      throw new AuthenticationException("Invalid key: " + key);
    }
  }

  public String bind(BindStruct bind) throws NetworkException, AuthenticationException, BindException, TException, UserException, SystemException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("bind-->" + bind.toString());
    }
    int accountId = bind.getAccountId();
    String applicationId = bind.getApplicationId();
    if (accountId <= 0 || StringUtils.isEmpty(applicationId)) {
      throw new UserException("Invalid account or application.");
    }
    // authentication
    Application oldapp = getApplication(accountId, applicationId);
    RemoteApplication newapp = new ThriftApplication(this, new ThriftURL(bind), accountId, applicationId, _contacts);
    putApplication(newapp);
    if (oldapp != null) {
      oldapp.dispose();      
    }
    return newapp.getApplicationKey();
  }
  
  RemoteApplication getApplication(int accountId, String applicationId) {
    Map<String, RemoteApplication> map = _apps.get(accountId);
    if (map == null) {
      map = new ConcurrentHashMap<String, RemoteApplication>();
      _apps.put(accountId, map);
    }
    return map.get(applicationId);
  }

  void putApplication(RemoteApplication app) {
    Map<String, RemoteApplication> map = _apps.get(app.getAccountID());
    if (map == null) {
      map = new ConcurrentHashMap<String, RemoteApplication>();
      _apps.put(app.getAccountID(), map);
    }
    map.put(app.getApplicationID(), app);
    _index.put(app.getApplicationKey(), app);
  }
  
  void removeApplication(RemoteApplication app) {
    Map<String, RemoteApplication> map = _apps.get(app.getAccountID());
    if (map != null) {
      map.remove(app.getApplicationID());
    }
    _index.remove(app.getApplicationKey());
    for(Iterator<Map.Entry<Object, Application>> i = _cache.entrySet().iterator(); i.hasNext();) {
      Map.Entry<Object, Application> entry = i.next();
      if (app.equals(entry.getValue())) {
        i.remove();
      }
    }
  }
  
  public Map<String, String> prompt(String key, String id, PromptStruct prompt) throws NetworkException, AuthenticationException, UserException, SystemException, TException {
    RemoteApplication app = _index.get(key);
    if (app != null && app instanceof ThriftApplication) {
      return ((ThriftApplication)app).prompt(id, prompt);
    }
    else {
      throw new AuthenticationException("Invalid key: " + key);
    }
  }

  public void redirect(String key, String id, String number) throws NetworkException, AuthenticationException, UserException, SystemException, TException {
    // TODO Auto-generated method stub
    
  }

  public void reject(String key, String id) throws NetworkException, AuthenticationException, UserException, SystemException, TException {
    RemoteApplication app = _index.get(key);
    if (app != null && app instanceof ThriftApplication) {
      ((ThriftApplication)app).reject(id);
    }
    else {
      throw new AuthenticationException("Invalid key: " + key);
    }
  }

  public void unbind(String token) throws NetworkException, AuthenticationException, UserException, SystemException, TException {
    RemoteApplication app = _index.get(token);
    if (app != null) {
      removeApplication(app);
    }
    app.dispose();
  }
}
