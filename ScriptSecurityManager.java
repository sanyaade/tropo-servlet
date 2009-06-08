package com.voxeo.tropo;

import java.awt.AWTPermission;
import java.io.File;
import java.io.FilePermission;
import java.net.SocketPermission;
import java.net.URL;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Permission;
import java.security.Security;
import java.security.SecurityPermission;
import java.security.Signature;
import java.util.HashSet;
import java.util.Map;
import java.util.PropertyPermission;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.net.ssl.SSLPermission;
import javax.security.auth.kerberos.DelegationPermission;
import javax.servlet.ServletContext;
import javax.sound.sampled.AudioPermission;

import org.apache.log4j.Logger;

import sun.security.util.SecurityConstants;

import com.voxeo.tropo.app.ApplicationInstance;
import com.voxeo.tropo.app.LocalApplication;
import com.voxeo.tropo.app.SimpleInstance;
import com.voxeo.tropo.util.Utils;

/**
 * 
 * all comparisons should be in lower case since getRealPath() will return in lower
 * case no matter whether there are capital letters in the pat or not
 */
public class ScriptSecurityManager extends SecurityManager {
  
  private static final Logger LOG = Logger.getLogger(ScriptSecurityManager.class);
  
  private String _base;
  private String _common;
  private String _shared;
  private String _server;
  private String _bin;
  private String _jdk;
  private String _jruby;
  private String _jython;
  private boolean _windows;
  private String[] _scriptCodeBases = new String[]{
      "org.jruby", "org.python", "com.ziclix.python", "org.mozilla.javascript",
      "org.codehaus.groovy", "groovy", "com.caucho",
  };
  /**
   * key-action
   * 
   * value- a set of all allowed target which starts with something in this set
   */
  private Map<String, Set<String>> _allow = null;

  /**
   * key-action
   * 
   * value- a set of all disallowed target which starts with something in this set
   */
  private Map<String, Set<String>> _forbid = null;

  
  public ScriptSecurityManager(ServletContext ctx) {
    _windows = System.getProperty("os.name").startsWith("Windows") ? true : false;
    _base = normalize(ctx.getRealPath("/")); // getRealPath() always return in lower case
    _common = _base.substring(0, _base.length() - 11) + "common" + File.separator;
    _shared = _base.substring(0, _base.length() - 11) + "shared" + File.separator;
    _server = _base.substring(0, _base.length() - 11) + "server" + File.separator;
    _bin = _base.substring(0, _base.length() - 11) + "bin" + File.separator;
    _jdk = normalize(System.getProperty("java.home"));
    _jdk = _jdk.substring(0, _jdk.lastIndexOf(File.separator));
    _jruby = normalize(Utils.rubyHome());
    _jython = normalize(Utils.pythonHome());
    LOG.info("Tropo base directory is " + _base);
    LOG.info("Tropo JDK directory is " + _jdk);
    LOG.info("Tropo JRuby directory is " + _jruby);
    LOG.info("Tropo Jython directory is " + _jython);
    _allow = Configuration.get().getSandboxAllow();
    _forbid = Configuration.get().getSandboxForbid();
    allowSysTempDirs();
    LOG.info("Allow preconfigured permissions:" + _allow);
    LOG.info("Forbid preconfigured permissions:" + _forbid);
    securityInitialization();
  }
  
  public void allowSysTempDirs() {
    Set<String> s = new HashSet();
    // for MAC OS
    String t = System.getenv("TMPDIR");
    if (t != null && t.length() > 0) {
      s.add(normalize(t));
    }
    // for Windows OS
    t = System.getenv("TEMP");
    if (t != null && t.length() > 0) {
      s.add(normalize(t));
    }
    t = System.getenv("TMP");
    if (t != null && t.length() > 0) {
      s.add(normalize(t));
    }

    // allowing all permissions
    addRestrictions(_allow, SecurityConstants.FILE_READ_ACTION, s);
    addRestrictions(_allow, SecurityConstants.FILE_WRITE_ACTION, s);
    addRestrictions(_allow, SecurityConstants.FILE_DELETE_ACTION, s);
    addRestrictions(_allow, SecurityConstants.FILE_EXECUTE_ACTION, s);
  }

  private void addRestrictions(Map<String, Set<String>> m, String key, Set<String> s) {
    Set<String> t = m.get(key);
    if (t == null) {
      m.put(key, s);
    }
    else {
      t.addAll(s);
    }
  }
  
  public boolean isPreconfiguredAllow(String action, String target) {
    Set<String> e = _allow.get(action);
    if (e != null) {
      for (String s : e) {
        if (target.startsWith(s)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isPreconfiguredForbid(String action, String target) {
    Set<String> e = _forbid.get(action);
    if (e != null) {
      for (String s : e) {
        if (target.startsWith(s)) {
          return true;
        }
      }
    }
    return false;
  }

  void securityInitialization() {
    try {
      Security.getProviders();
    }
    catch (Throwable t) {
      ; //ignore
    }
    Set<String> names = null;
    names = Security.getAlgorithms("Signature");
    for (String name : names) {
      try {
        Signature.getInstance(name);
      }
      catch (Throwable t) {
        ; //ignore
      }
    }
    names = Security.getAlgorithms("MessageDigest");
    for (String name : names) {
      try {
        MessageDigest.getInstance(name);
      }
      catch (Throwable t) {
        ; //ignore
      }
    }
    names = Security.getAlgorithms("Cipher");
    for (String name : names) {
      try {
        Cipher.getInstance(name);
      }
      catch (Throwable t) {
        ; //ignore
      }
    }
    names = Security.getAlgorithms("Mac");
    for (String name : names) {
      try {
        Mac.getInstance(name);
      }
      catch (Throwable t) {
        ; //ignore
      }
    }
    names = Security.getAlgorithms("KeyStore");
    for (String name : names) {
      try {
        KeyStore.getInstance(name);
      }
      catch (Throwable t) {
        ; //ignore
      }
    }
    
    try {
      new URL("https://www.verisign.com/").openStream();
    }
    catch (Throwable t) {
      ; //ignore
    }    
  }
      
  @Override
  public void checkExit(int code) {
    ApplicationInstance ai = currentApplicationInstance();
    if (ai != null) {      
      if (LOG.isDebugEnabled()) {
        LOG.debug("No exitVM RuntimePermisson.");
      }
      throw new SecurityException("No exitVM RuntimePermisson.");
    }
    super.checkExit(code);
   }
    
  @Override
  public void checkPermission(Permission p) {
    checkPermission(p, null);
  }
     
  @Override
  public void checkPermission(Permission p, Object ctx) {
    ApplicationInstance ai = currentApplicationInstance();
    if (ai != null) {      
      if (p instanceof FilePermission) {
        String target = normalize(p.getName());
        String action = p.getActions().toLowerCase();
        String abase = normalize(((LocalApplication)(ai.getApp())).getBaseDir());
        // check the forbid configuration in tropo.xml first
        if (isPreconfiguredForbid(action, target)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("No " + action + " FilePermission to " + target);
          }
          throw new SecurityException("No " + action + " FilePermission to " + target);
        }
        // check the allow configuration in tropo.xml
        if (isPreconfiguredAllow(action, target)) {
          return;
        }
        
        if (target.startsWith(abase)) {
          if (SecurityConstants.FILE_EXECUTE_ACTION.equals(action)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("No execute FilePermission to " + target);
            }
            throw new SecurityException("No execute FilePermission to " + target);
          }
          else {
            return;
          }
        }
        else if (isReservedTarget(target)) {
          if (SecurityConstants.FILE_READ_ACTION.equals(action)) {
            return;
          }
          else if ((target.startsWith(_jython) || target.startsWith(_jruby)) && target.endsWith(".class") && SecurityConstants.FILE_WRITE_ACTION.equals(action)) {
            return;
          }
          else {
            if (LOG.isDebugEnabled()) {
              LOG.debug("No " + action + " FilePermission to " + target);
            }
            throw new SecurityException("No " + action + " FilePermission to " + target);          
          }
        }
        else if (target.startsWith(_base)) {
          if (SecurityConstants.FILE_READ_ACTION.equals(action)) {
            return;
          }
          if (SecurityConstants.FILE_WRITE_ACTION.equals(action) && (target.startsWith(_base + "logs") || target.startsWith(_base + "slogs"))) {
            return;
          }
          else {
            if (LOG.isDebugEnabled()) {
              LOG.debug("No " + action + " FilePermission to " + target);
            }
            throw new SecurityException("No " + action + " FilePermission to " + target);
          }
        }
        else if (target.indexOf("__classpath__") >= 0) {
          if (SecurityConstants.FILE_READ_ACTION.equals(action)) {
            return;
          }
          else {
            if (LOG.isDebugEnabled()) {
              LOG.debug("No " + action + " FilePermission to " + target);
            }
            throw new SecurityException("No " + action + " FilePermission to " + target);
          }
        }
        else if (target.indexOf("groovy\\script") >= 0 
            || target.indexOf("/var/tmp/java3d/") >= 0 
            || target.indexOf("/groovy/script") >= 0) {
          //this is a temporary fix for running groovy
          return;
        }
        else if (target.toLowerCase().indexOf("c:\\documents and settings") >= 0 ) {
          //this is a temporary fix for running jruby  / require 'rest_client' , script/rest_client.rb
          return;
        }
        else {
          if (LOG.isDebugEnabled()) {
            LOG.debug("No " + action + " FilePermission to " + target);
          }
          throw new SecurityException("No " + action + " FilePermission to " + target);
        }
      }
      else if (p instanceof RuntimePermission) {
        String target = p.getName();
        if ("exitVM".equals(target) 
            || "stopThread".equals(target) 
            || "loadLibrary".equals(target) 
            || "setIO".equals(target) 
            || "shutdownHooks".equals(target)
            || "createSecurityManager".equals(target) 
            || "setSecurityManager".equals(target) 
            || "queuePrintJob".equals(target) 
            || "setFactory".equals(target)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("No RuntimePermission to " + target);
          }
          throw new SecurityException("No RuntimePermission to " + target);          
        }
      }
      else if (p instanceof AudioPermission) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("No AudioPermission to " + p.getName());
        }
        throw new SecurityException("No AudioPermission to " + p.getName());          
      }
      else if (p instanceof AWTPermission) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("No AWTPermission to " + p.getName());
        }
        throw new SecurityException("No AWTPermission to " + p.getName());          
      }
      else if (p instanceof DelegationPermission) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("No DelegationPermission to " + p.getName());
        }
        throw new SecurityException("No DelegationPermission to " + p.getName());
      }
      else if (p instanceof PropertyPermission) {
        String target = p.getName();
        String action = p.getActions();
        if (SecurityConstants.FILE_WRITE_ACTION.equals(action)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("No write PropertyPermission to " + target);
          }
          throw new SecurityException("No write PropertyPermission to " + target);                  
        }
        else if (SecurityConstants.FILE_READ_ACTION.equals(action)) {
          return;
        }
      }
      else if (p instanceof SecurityPermission) {
        if (p.getName().startsWith("getProperty")) {
          return;
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("No SecurityPermission to " + p.getName());
        }
        throw new SecurityException("No SecurityPermission to " + p.getName());
      }
      else if (p instanceof SSLPermission) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("No SSLPermission to " + p.getName());
        }
        throw new SecurityException("No SSLPermission to " + p.getName());
      }
      else if (p instanceof SocketPermission) {
        String action = p.getActions();
        if (SecurityConstants.SOCKET_ACCEPT_ACTION.equals(action) || SecurityConstants.SOCKET_LISTEN_ACTION.equals(action)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("No " + action + " SocketPermission to " + p.getName());
          }
          throw new SecurityException("No " + action + " SocketPermission to " + p.getName());
        }       
      }
    }
  }
 
  boolean isReservedTarget(String target) {
    return target.startsWith(_jdk)
        || target.startsWith(_common) 
        || target.startsWith(_jruby) 
        || target.startsWith(_jython) 
        || target.startsWith(_shared) 
        || target.startsWith(_server)
        || target.startsWith(_bin)  
        || target.endsWith("libkeychain.jnilib");
  }
  
  void printCalls() {
    for (Class<?> cls : getClassContext()) {
      System.out.println(cls);
    }
  }
  
  String normalize(String str) {
    if (_windows && str != null) {
      if (str.indexOf(File.separator) == 0) {
        str = str.substring(1);
      }
      return str.toLowerCase().replace("/", File.separator);
    } 
    return str.toLowerCase();
  }


  boolean isTrusted() {
    for (Class<?> cls : getClassContext()) {
      String name = cls.getName();
      if (isScriptCodeBase(name)) {
        return false;
      }
      else if (name.startsWith("com.voxoe.tropo")) {
        return true;
      }
    }
    return true;
  }
  
  boolean isScriptCodeBase(String name) {
    for (String s : _scriptCodeBases) {
      if (name.startsWith(s)) {
        return true;
      }
    }
    return false;
  }
  
  ApplicationInstance currentApplicationInstance() {
    String name = Thread.currentThread().getName();
    if (name.equals("Script")) {
      ApplicationInstance ai = SimpleInstance.getCurrentApplicationInstance();
      if (ai == null) {
        SecurityException e = new SecurityException("Invalid Script Thread.");
        LOG.error("Invalid Script Thread", e);
        throw e;         
      }
      return ai;
    }
    return null;
  }
}
