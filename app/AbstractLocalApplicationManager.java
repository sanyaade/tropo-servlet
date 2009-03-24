package com.voxeo.tropo.app;

import java.io.IOException;
import java.io.Serializable;
import java.lang.management.ThreadInfo;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import javax.script.Compilable;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.servlet.ServletContext;

import org.apache.log4j.Logger;

import com.voxeo.tropo.Configuration;
import com.voxeo.tropo.ScriptSecurityManager;
import com.voxeo.tropo.ServletContextConstants;
import com.voxeo.tropo.util.ScriptThreadPoolExecutor;
import com.voxeo.tropo.util.Utils;

public abstract class AbstractLocalApplicationManager extends AbstractApplicationManager implements LocalApplicationManager, LocalApplicationMonitor, Runnable {
  private static final Logger LOG = Logger.getLogger(AbstractLocalApplicationManager.class);

  protected static ScriptEngineManager SCRIPT_MGR = null;

  protected static ScriptSecurityManager SECURITY_MGR = null;

  protected ScriptEnginePool _engPool;

  protected static Map<String, String> CANONICAL_TYPES = new HashMap<String, String>();

   class TInfo {
    long _lastCpuTime = 0;

    long _lastTime = System.currentTimeMillis();

    int _tags = 0;

    float _percentage = 0;

    @Override
    public String toString() {
      return "TInfo[" + _percentage + ", " + _tags + "]";
    }
  }

  protected int _loop;

  protected Object _loopLock = new Object();

  protected Map<Long, TInfo> _tinfos;

  protected Map<Long, Thread> _threads;

  protected ExecutorService _pool = new ScriptThreadPoolExecutor(Configuration.get().getThreadSize() / 4, Configuration
      .get().getThreadSize(), new ThreadFactory() {
    public Thread newThread(final Runnable r) {
      final Thread t = new Thread(new ThreadGroup("Scripts"), r, "Script", 0);
      if (t.isDaemon()) {
        t.setDaemon(true);
      }
      if (t.getPriority() != Thread.NORM_PRIORITY) {
        t.setPriority(Thread.NORM_PRIORITY);
      }
      _threads.put(t.getId(), t);
      return t;
    }
  });

  static {
    CANONICAL_TYPES.put("javascript", "js");
    CANONICAL_TYPES.put("Javascript", "js");
    CANONICAL_TYPES.put("js", "js");
    CANONICAL_TYPES.put("es", "js");
    CANONICAL_TYPES.put("groovy", "groovy");
    CANONICAL_TYPES.put("Groovy", "groovy");
    CANONICAL_TYPES.put("Jython", "jython");
    CANONICAL_TYPES.put("jython", "jython");
    CANONICAL_TYPES.put("py", "jython");
    CANONICAL_TYPES.put("Py", "jython");
    CANONICAL_TYPES.put("Jy", "jython");
    CANONICAL_TYPES.put("jy", "jython");
    CANONICAL_TYPES.put("php", "php");
  }

  public static String getCanonicalType(final String type) {
    return CANONICAL_TYPES.get(type);
  }

  public void dispose() {
    super.dispose();
    setLoopDectionTime(-1);
    _pool.shutdown();
  }


  public void init(final ServletContext context, final Map<String, String> paras) {
    super.init(context, paras);
    ScriptEngineManager engMgr = (ScriptEngineManager) context.getAttribute(ServletContextConstants.ENGINE_MANAGER);
    _engPool = new ScriptEnginePool(engMgr, Configuration.get().getEnginePoolSizes());
    CANONICAL_TYPES.putAll(paras);
    _tinfos = new HashMap<Long, TInfo>();
    _threads = new ConcurrentHashMap<Long, Thread>();
    _loop = 2;
    new Thread(this, "ApplicationMonitor").start();
  }

  public ScriptEnginePool getScriptEnginePool() {
    return _engPool;
  }

  public ScriptEngine getScriptEngine(final String type) {
    return _engPool.get(type);
  }

  public void putScriptEngine(final String type, ScriptEngine eng) {
    if (eng == null) {
      return;
    }
    _engPool.put(type, eng);
  }

  public void execute(final ApplicationInstance inst) {
    _pool.execute(inst);
  }

  public ApplicationURL createURL(final String url, final String method) throws MalformedURLException, IOException {
    return new JavaURL(new URL(url), method);
  }

  protected Application createApplication(final String name, final String type, final int account, final String app,
      final Properties params) throws MalformedURLException, IOException, InvalidApplicationException {
    final ApplicationURL url = createURL(name, "GET");
    return new SimpleApplication(this, url, type, account, app, params);
  }

  protected Application createApplication(final String url, final int accountId, final String app, final Properties params)
      throws InvalidApplicationException {
    Utils.setLogContext(String.valueOf(accountId), "-1", "-1", "-1");
    final String[] urlAndType = getURLandTypeBasedOnExtension(url);
    try {
      return createApplication(urlAndType[0], urlAndType[1], accountId, app, params);
    }
    catch (final Exception e) {
      LOG.error("Unable to create application: " + e.getMessage(), e);
      throw new InvalidApplicationException(e);
    }
  }

  // simple util method
  protected static String[] getURLandTypeBasedOnExtension(final String url) throws InvalidApplicationException {
    final String name = url.substring(url.lastIndexOf('/') + 1);
    final String[] splits = name.split("[.]");
    if (splits.length < 2) {
      throw new InvalidApplicationException("Unable to find application type based extension: " + name);
    }
    final String type = getCanonicalType(splits[1]);
    if (type == null) {
      throw new InvalidApplicationException("Unable to support application type: " + splits[1]);
    }
    splits[0] = url;
    splits[1] = type;
    if (LOG.isDebugEnabled()) {
      LOG.debug("Trying to create application for URL=" + splits[0] + " and type=" + splits[1]);
    }
    return splits;
  }

  public static synchronized void initialization(final ServletContext ctx) throws InstantiationException, IllegalAccessException {
    if (SCRIPT_MGR == null) {
      Utils.pythonHome();
      Utils.rubyHome();
      Utils.getAppDir();
      
      // initialize script engine manager
      SCRIPT_MGR = new ScriptEngineManager();
      ctx.setAttribute(ServletContextConstants.ENGINE_MANAGER, SCRIPT_MGR);

      LOG.info("Initializing Script Engine Manager [" + SCRIPT_MGR + "] ...");
      for (final ScriptEngineFactory f : SCRIPT_MGR.getEngineFactories()) {
        final ScriptEngine e = f.getScriptEngine();
        final StringBuffer log = new StringBuffer("Engine=" + f.getEngineName() + "[" + f.getEngineVersion()
            + "], lang=" + f.getLanguageName() + "[" + f.getLanguageVersion() + "], alias=" + f.getNames());
        log.append(e instanceof Compilable ? ", compilable=true" : ", compiable=false");
        log.append(e instanceof Invocable ? ", invocable=true" : ", invocable=false");
        log.append(e instanceof Serializable ? ", serializable=true" : ", serializable=false.");
        LOG.info(log.toString());
      }
    }
    
    if (SECURITY_MGR == null && Configuration.get().isEnableSecurityManager()) {
      SECURITY_MGR = new ScriptSecurityManager(ctx);
      System.setSecurityManager(SECURITY_MGR);
    }
    
    AbstractApplicationManager.initialization(ctx);
  }

  public void run() {
    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
    while (_tmx.isThreadCpuTimeEnabled()) {
      if (getLoopDectionTime() > 0) {
        try {
          loopDection();
        }
        catch (final Throwable t) {
          LOG.error(t.toString(), t);
        }
      }
      synchronized (_loopLock) {
        try {
          final long time = getLoopDectionTime() * 60000L;
          if (time > 0) {
            _loopLock.wait(time);
          }
          else {
            _loopLock.wait();
          }
        }
        catch (final InterruptedException e) {
          ;
        }
      }
      try {
        for (final Iterator<Long> i = _tinfos.keySet().iterator(); i.hasNext();) {
          final long id = i.next();
          if (_tmx.getThreadInfo(id) == null) {
            i.remove();
          }
        }
      }
      catch (final Throwable t) {
        LOG.error(t.toString(), t);
      }
    }
  }

  void loopDection() {
    LOG.info("Starts loop detection.");
    int active = 0;
    for (final long id : _tmx.getAllThreadIds()) {
      final ThreadInfo info = _tmx.getThreadInfo(id);
      if (info == null) {
        continue;
      }
      if (!"Script".equals(info.getThreadName())) {
        continue;
      }
      if (Thread.State.RUNNABLE != info.getThreadState()) {
        continue;
      }
      active++;

      TInfo ti = _tinfos.get(id);
      if (ti != null) {
        final long nowCpuTime = _tmx.getThreadCpuTime(id);
        if (nowCpuTime == -1) {
          _tinfos.remove(id);
          continue;
        }
        final long cpu = nowCpuTime - ti._lastCpuTime;
        final long now = System.nanoTime();
        final long time = now - ti._lastTime;
        ti._lastCpuTime = nowCpuTime;
        ti._lastTime = now;
        ti._percentage = (float) cpu / (float) time;
      }
      else {
        ti = new TInfo();
        ti._lastCpuTime = _tmx.getThreadCpuTime(id);
        if (ti._lastCpuTime == -1) {
          continue;
        }
        ti._lastTime = System.nanoTime();
        _tinfos.put(id, ti);
      }
    }
    final float threshold = (float) (active > 50 ? 0.25 : active > 10 ? 0.5 : 0.75);
    for (final long id : _tinfos.keySet()) {
      final TInfo ti = _tinfos.get(id);
      final Thread t = _threads.get(id);
      if (t == null || ti == null) {
        continue;
      }
      LOG.info("Thread[" + id + "] cpu info :" + ti);
      if (ti._percentage > threshold) {
        ti._tags++;
        if (ti._tags > 2) {
          LOG.error("*WARNING* Thread[" + id + "] has taken most of CPU time out of " + active
              + " script threads and will be set with the lowest priority.");
          t.setPriority(Thread.MIN_PRIORITY);
        }
      }
      else {
        ti._tags = 0;
        t.setPriority(Thread.NORM_PRIORITY);
      }

    }
    LOG.info("Ends loop detection.");
  }

  public void setLoopDectionTime(final int x) {
    synchronized (_loopLock) {
      _loop = x;
      _loopLock.notifyAll();
    }
  }

  public int getLoopDectionTime() {
    synchronized (_loopLock) {
      return _loop;
    }
  }
}
