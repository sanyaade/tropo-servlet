package com.voxeo.tropo.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.sip.SipServletRequest;

import org.apache.log4j.Logger;

import com.voxeo.tropo.Configuration;
import com.voxeo.tropo.util.ChainedReader;
import com.voxeo.tropo.util.Utils;

@SuppressWarnings("serial")
public class SimpleApplication extends AbstractApplication implements LocalApplication {
  private static final Logger LOG = Logger.getLogger(SimpleApplication.class);
  
  protected CompiledScript _compiledScript = null;

  protected String _baseDir;

  protected Object _waitLock = new Object();

  // characters for each script languages
  protected static Map<String, String> CommentCharacterMap = new HashMap<String, String>();
  static {
    CommentCharacterMap.put("js", "    //"); // add some space for readable
    CommentCharacterMap.put("jruby", "    #");
    CommentCharacterMap.put("jython", "    #");
    CommentCharacterMap.put("groovy", "    //");
    CommentCharacterMap.put("php", "    //");
  }

  public SimpleApplication(final LocalApplicationManager mgr, final ApplicationURL url, final String type, final int aid, final String appId, 
      final Properties params) throws InvalidApplicationException {
    super(mgr, url, type, aid, appId, params);
    _baseDir = Utils.getAppDir() + System.getProperty("file.separator") + String.valueOf(getAccountID());
//        + System.getProperty("file.separator");
    /*
     * TODO: when to delete this directory ???. Considering in hosting with a
     * cluster.
     */
    File dir = new File(_baseDir);
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        LOG.error("Erron in creating directory: " + _baseDir);
      }
    }
    setLogContext(null);
//    _engine = mgr.getScriptEngine(type);
//    if (_engine == null) {
//      LOG.error("No scripting engine is available for type :" + getType());
//      throw new InvalidApplicationException("No scripting engine is available for type :" + getType());
//    }
    LOG.info(toString() + " has been created.");
  }

  public void execute(final SipServletRequest invite) throws ScriptException, IOException {
    setLogContext(invite);
    try {
      final CompiledScript script = getCompiledScript();
      final ApplicationInstance inst = new SimpleInstance(invite, script, this);
      ((LocalApplicationManager)_mgr).execute(inst);
    }
    catch (final ScriptException e) {
      LOG.error(Utils.buildScriptExceptionMessage(this, "compilation", e), e);
      throw e;
    }
    catch (final FileNotFoundException e) {
      LOG.error(this + " can not find the script: " + e.getMessage(), e);
      throw e;
    }
    catch (final IOException e) {
      LOG.error(this + " can not read the script: " + e.getMessage(), e);
      throw e;
    }
    catch (final Throwable e) {
      LOG.error(this + " has unknown errors: " + e.getMessage(), e);
      throw new RuntimeException(e);
    }
  }

  public void execute(final HttpServletRequest req) throws ScriptException, IOException {
    setLogContext(req);
    try {
      final CompiledScript script = getCompiledScript();
      final ApplicationInstance inst = new SimpleInstance(req, script, this);
      ((LocalApplicationManager)_mgr).execute(inst);
    }
    catch (final ScriptException e) {
      LOG.error(Utils.buildScriptExceptionMessage(this, "compilation", e), e);
      throw e;
    }
    catch (final FileNotFoundException e) {
      LOG.error(this + " can not find the script: " + e.getMessage(), e);
      throw e;
    }
    catch (final IOException e) {
      LOG.error(this + " can not read the script: " + e.getMessage(), e);
      throw e;
    }
    catch (final Throwable e) {
      LOG.error(this + " has unknown errors: " + e.getMessage(), e);
      throw new RuntimeException(e);
    }
  }

  public synchronized CompiledScript getCompiledScript() throws ScriptException, IOException {
    if (_compiledScript == null) {
      try {
        _compiledScript = createScript(true);
      }
      catch (final ApplicationURL.UnmodifiedException e) {
        throw new RuntimeException(); // should never happen
      }
    }
    else {
      try {
        _compiledScript = createScript(false);
      }
      catch (final ApplicationURL.UnmodifiedException e) {
        // if (LOG.isDebugEnabled()) {
        // LOG.debug("Script is unchanged.");
        // }
      }
    }
    return _compiledScript;
  }

  protected CompiledScript createScript(final boolean force) throws ScriptException, IOException,
      ApplicationURL.UnmodifiedException {
    LocalApplicationManager mgr = (LocalApplicationManager)getManager();
    final String source = getScriptContent(force);
    ScriptEngine engine = mgr.getScriptEngine(_type);
    CompiledScript compiledScript = null;
    if (engine instanceof Compilable) {
      compiledScript = ((Compilable) engine).compile(source);
    }
    else {
      compiledScript = new SimulatedCompiledScript(mgr, source, getType());
    }
    mgr.putScriptEngine(_type, engine);
    return compiledScript;
  }

  public void dispose() {
    _compiledScript = null;
    
  }

  public Reader getScriptReader(final boolean force) throws IOException, ApplicationURL.UnmodifiedException {
    final Reader header = new InputStreamReader(Application.class.getClassLoader().getResourceAsStream(
        Configuration.get().getScriptHeader(getType())));
    return new ChainedReader(header, new InputStreamReader(getURL().openStream(force)));
  }

  protected String getScriptContent(final boolean force) throws IOException, ApplicationURL.UnmodifiedException {
    final StringBuffer b = new StringBuffer();
    final BufferedReader r = new BufferedReader(getScriptReader(force));
    long lineNo = 0;
    try {
      String l = r.readLine();
      boolean userCode = false;
      while (l != null) {
        ++lineNo;
        b.append(l.concat("\r\n"));
        if (userCode) {
          LOG.info(l.concat(CommentCharacterMap.get(_type)).concat(" line ").concat(String.valueOf(lineNo)));
        }
        if (!userCode && l.indexOf("end shim of") > -1) {
          userCode = true;
        }
        l = r.readLine();
      }
    }
    finally {
      r.close();
    }
    return b.toString();
  }

  public String getBaseDir() {
    return _baseDir;
  }

}