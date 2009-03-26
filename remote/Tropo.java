package com.voxeo.tropo.remote;

import java.net.InetAddress;
import java.util.Properties;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

import com.voxeo.tropo.thrift.AlertStruct;
import com.voxeo.tropo.thrift.AuthenticationException;
import com.voxeo.tropo.thrift.BindException;
import com.voxeo.tropo.thrift.BindStruct;
import com.voxeo.tropo.thrift.Notifier;
import com.voxeo.tropo.thrift.SystemException;
import com.voxeo.tropo.thrift.TropoService;
import com.voxeo.tropo.thrift.Notifier.Processor;
import com.voxeo.tropo.util.Utils;

public class Tropo implements Notifier.Iface, Runnable {
  public static final String ACCOUNT_ID = "accountID";
  public static final String APPLICATION_ID = "applicationID";
  public static final String REMOTE_HOST = "remoteHost";
  public static final String REMOTE_PORT = "remotePort";
  public static final String LOCAL_HOST = "localHost";
  public static final String LOCAL_PORT = "localPort";
  public static final String BLOCKING = "blocking";
  public static final String AUTO_RESTART = "auto_restart";
  public static final String USER = "username";
  public static final String PASSWORD = "password";
  
  protected BindStruct _bind;
  
  protected TTransport _transport;
  
  protected TServerTransport _listener;
  
  protected TServer _server;
  
  protected String _key;
  
  protected TropoService.Client _tropo;
  
  protected String _token;
  
  protected TropoProperties _properties;
  
  protected boolean _shutdown = false;
  
  protected Processor _notifier;
  
  protected int _heartbeats = 0;
  
  protected Object _systemWaiter;

  protected Object _monitorWaiter;

  public synchronized void startup(Properties props) {
    _properties = new TropoProperties(props);
    _notifier = new Notifier.Processor(this);
    
    restart();    
    System.out.println("Successfully connected to Tropo clouds.");
    
    new Thread(new Monitor(), "Tropo Monitor").start();
    
    if (_properties.getPropertyAsBoolean(BLOCKING)) {
      _systemWaiter = new Object();
      while(!_shutdown) {
        synchronized(_systemWaiter) {
          try {
            _systemWaiter.wait();
          }
          catch(InterruptedException e) {
            // ignore
          }
        }
      }
    }
  }

  public synchronized void shutdown() {
    _shutdown = true;
    try {
      _tropo.unbind(_key);
      _transport.close();
      _server.stop();
      _listener.close();
    }
    catch(Exception e) {
      //ignore
    }
    if (_systemWaiter != null) {
      _systemWaiter.notifyAll();
    }
    if (_monitorWaiter != null) {
      _monitorWaiter.notifyAll();
    }
  }
  
  synchronized void restart() {
    _bind = new BindStruct();
    _bind.setAccountId(_properties.getPropertyAsInt(ACCOUNT_ID));
    _bind.setApplicationId(_properties.getProperty(APPLICATION_ID));
    _bind.setUser(_properties.getProperty(USER));
    _bind.setPassword(_properties.getProperty(PASSWORD));
    _bind.setHost(_properties.getProperty(LOCAL_HOST));
    _bind.setPort(_properties.getPropertyAsInt(LOCAL_PORT));
    _bind.setSecret(Utils.getGUID());
    
    _token = Utils.getGUID();
    String host = _properties.getProperty(REMOTE_HOST);
    int port = _properties.getPropertyAsInt(REMOTE_PORT);
    
    try {
      try {
        _transport = new TSocket(host, port);
        _tropo = new TropoService.Client(new TBinaryProtocol(_transport));
        _transport.open();
        _key = _tropo.bind(_bind);      
      }
      catch(BindException e) {
        // rebind
      }
      _listener = new TServerSocket(_bind.getPort());
      _server = new TThreadPoolServer(_notifier, _listener);
      new Thread(this, "Thrift").start();
    }
    catch(Exception e) {
      throw new RuntimeException(e);
    }
  }
    
  @SuppressWarnings("serial")
  static class TropoProperties extends Properties {
    static final Properties DEFAULTS = new Properties();
    static {
      DEFAULTS.put(REMOTE_HOST, "127.0.0.1");
      DEFAULTS.put(REMOTE_PORT, "9090");
      try {
        DEFAULTS.put(LOCAL_HOST, InetAddress.getLocalHost().toString());
      }
      catch(Exception e) {
        DEFAULTS.put(LOCAL_HOST, "127.0.0.1");        
      }
      DEFAULTS.put(LOCAL_PORT, "9091");
      DEFAULTS.put(BLOCKING, "true");
      DEFAULTS.put(AUTO_RESTART, "true");
    }
    
    public TropoProperties(Properties props) {
      super(DEFAULTS);
      putAll(props);
    }
    
    public String getProperty(String key) {
      String value = super.getProperty(key);
      if (value == null) {
        throw new IllegalArgumentException("Invalid " + key + " value: " + value);
      }
      return value;
    }
    
    public int getPropertyAsInt(String key) {
      String value = getProperty(key);
      try {
        return Integer.parseInt(value);
      }
      catch(Exception e) {
        throw new IllegalArgumentException("Invalid " + key + " value: " + value);
      }
    }
    
    public boolean getPropertyAsBoolean(String key) {
      String value = getProperty(key);
      try {
        return Boolean.parseBoolean(value);
      }
      catch(Exception e) {
        throw new IllegalArgumentException("Invalid " + key + " value: " + value);
      }
    }    
  }
  
  class Monitor implements Runnable {
    public void run() {
      _monitorWaiter = new Object();
      while(!_shutdown) {
        try {
          _tropo.heartbeat(_key);
          _heartbeats = 0;
        }
        catch(Throwable t) {
          _heartbeats ++;
          if (_heartbeats > 2) {
            if (_properties.getPropertyAsBoolean(AUTO_RESTART)) {
              System.out.println("Disconnected from Tropo clouds. Reconnecting...");
              try {
                restart();
                System.out.println("Successfully reconnected to Tropo clouds.");
              }
              catch(Throwable x) {
                System.out.println("Unable to reconnect to Tropo clouds. Retry in 5 seconds.");
              }
            }
            else {
              System.out.println("Disconnected from Tropo clouds. Please restart the system.");
            }
          }
        }
        synchronized(_monitorWaiter) {
          try {
            _monitorWaiter.wait(5000);
          }
          catch(InterruptedException e) {
            // ignore
          }
        }
      }
      
    }
  }

  public void alert(String token, AlertStruct alert) throws TException {
    if (_token.equals(token)) {
      
    }
  }

  public String bind(String secrete) throws AuthenticationException, BindException, SystemException, TException {
    if (_bind.getSecret().equals(secrete)) {
      return _token;
    }
    throw new AuthenticationException();
  }

  public void heartbeat(String token) throws AuthenticationException, SystemException, TException {
    if (_token.equals(token)) {
      return;
    }
    throw new AuthenticationException();
  }

  public void unbind(String token) throws AuthenticationException, SystemException, TException {
    if (_token.equals(token)) {
      System.out.println("Disconnected by Tropo clouds.");
      _tropo.unbind(_key);
      _key = null;
      return;
    }
    throw new AuthenticationException();
  }

  public void run() {
    try {
      _server.serve();
    }
    catch(Throwable t) {
      t.printStackTrace();
    }
  }
}
