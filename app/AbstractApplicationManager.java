package com.voxeo.tropo.app;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.ServletContext;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.URI;

import org.apache.log4j.Logger;

import com.voxeo.sipmethod.mrcp.client.Endpoint;
import com.voxeo.sipmethod.mrcp.client.MrcpClient;
import com.voxeo.sipmethod.mrcp.client.MrcpFactory;
import com.voxeo.tropo.Configuration;
import com.voxeo.tropo.ServletContextConstants;
import com.voxeo.tropo.util.Utils;

public abstract class AbstractApplicationManager implements ApplicationManager {
  private static final Logger LOG = Logger.getLogger(AbstractApplicationManager.class);
  
  protected static ApplicationManager APP_MGR = null;

  protected SipFactory _sipFactory;

  protected MrcpFactory _mrcpFactory;

  protected ServletContext _context;

  protected Map<Object, Application> _cache;
  
  protected String _appJarUrl;
  
  protected String _tropoVersionNo;
  
  protected String _tropoBuildDate;
  
  protected String _tropoBuildNo;
  
  protected ThreadMXBean _tmx;;

  protected MemoryMXBean _mmx;

  protected Date _startTime;

  public void init(final ServletContext context, final Map<String, String> paras) {
    _startTime = new Date();
    _context = context;
    _sipFactory = (SipFactory) context.getAttribute(ServletContextConstants.SIP_FACTORY);
    _mrcpFactory = (MrcpFactory) context.getAttribute(ServletContextConstants.MRCP_FACTORY);
    _appJarUrl = _context.getRealPath("/") + "WEB-INF" + File.separator + "lib" + File.separator + "tropo.jar";
    _cache = Collections.synchronizedMap(new WeakHashMap<Object, Application>());
    _tmx = ManagementFactory.getThreadMXBean();
    _mmx = ManagementFactory.getMemoryMXBean();
    try {
      _tropoBuildDate = Utils.getManifestAttribute(_appJarUrl, "Build-Date");
    }
    catch (final IOException t) {
      LOG.error(t.toString(), t);
      _tropoBuildDate = "Unknown";
    }
    try {
      _tropoVersionNo = Utils.getManifestAttribute(_appJarUrl, "Version-No");
    }
    catch (final IOException t) {
      LOG.error(t.toString(), t);
      _tropoVersionNo = "Unknown";
    }
    try {
      _tropoBuildNo = Utils.getManifestAttribute(_appJarUrl, "Build-No");
    }
    catch (final IOException t) {
      LOG.error(t.toString(), t);
      _tropoBuildNo = "Unknown";
    }
    LOG.info(toString() + " / " + getVersionNo() + " / " + getBuildNo());
  }

  public SipFactory getSipFactory() {
    return _sipFactory;
  }

  public MrcpFactory getMrcpFactory() {
    return _mrcpFactory;
  }
  
  protected Application getCached(final Object key) {
    return _cache.get(key);
  }

  protected void putCached(final Object key, final Application app) {
    _cache.put(key, app);
  }
  
  protected void clearCached(final Application app) {
    for(Iterator<Map.Entry<Object, Application>> i = _cache.entrySet().iterator(); i.hasNext();) {
      Map.Entry<Object, Application> entry = i.next();
      if (app.equals(entry.getValue())) {
        i.remove();
      }
    }
  }

  public Application get(final URI uri) throws InvalidApplicationException, RedirectException {
    Application app = getCached(uri);
    if (LOG.isDebugEnabled()) {
      LOG.debug(toString() + " found app=" + app + " for url=" + uri + ",_cache.size=" + _cache.size());
    }
    if (app == null) {
      app = findApplication(uri);
      putCached(uri, app);
      if (LOG.isDebugEnabled()) {
        LOG.debug(toString() + " cached app=" + app + " for url=" + uri + ",_cache.size=" + _cache.size());
      }
    }
    else {
      Utils.setLogContext(app, null);
      LOG.info(app + " has been found.");
    }
    return app;
  }

  public Application get(final String token, final Properties params) throws InvalidApplicationException, RedirectException {
    Application app = getCached(token);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Mgr Found app=" + app + " for token=" + token + ",_cache.size=" + _cache.size());
    }
    if (app == null) {
      app = findApplication(token, params);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Mgr Cached app=" + app + " for token=" + token + ",_cache.size=" + _cache.size());
      }
    }
    else {
      Utils.setLogContext(app, null);
      LOG.info(app + " has been found.");
    }
    return app;
  }

  protected abstract Application findApplication(URI uri) throws InvalidApplicationException, RedirectException;

  protected abstract Application findApplication(String token, Properties params) throws InvalidApplicationException, RedirectException;

  public void dispose() {
    for (final Application app : _cache.values()) {
      app.dispose();
    }
    _cache.clear();
  }
  
  public ThreadInfo[] getDeadLocks() {
    final long[] ids = _tmx.findDeadlockedThreads();
    if (ids != null) {
      return _tmx.getThreadInfo(ids, true, true);
    }
    else {
      return null;
    }
  }

  public MemoryUsage getMemoryUsage() {
    return _mmx.getHeapMemoryUsage();
  }

  private AtomicLong _newCalls = new AtomicLong(0);

  private AtomicLong _endCalls = new AtomicLong(0);

  public void incCallCounter() {
    _newCalls.incrementAndGet();
  }

  public void decCallCounter() {
    _endCalls.incrementAndGet();
  }

  public long getActiveCalls() {
    return _newCalls.get() - _endCalls.get();
  }

  public long getTotalCalls() {
    return _newCalls.get();
  }

  public String getVersionNo() {
    return _tropoVersionNo;
  }

  public String getBuildNo() {
    return _tropoBuildNo;
  }

  public String getBuildDate() {
    return _tropoBuildDate;
  }

  // only the file name of the log, without path info
  public Collection<String> listTropoLogNames() {
    final File r = new File(_context.getRealPath("/") + "logs");
    final File[] fs = r.listFiles();
    final List<String> l = new ArrayList<String>();
    for (final File f : fs) {
      if (f.isFile()) {
        l.add(f.getName());
      }
    }
    return l;
  }

  public Collection<String> listSIPMethodLogNames() {
    final File r = new File(_context.getRealPath("/") + "slogs");
    final File[] fs = r.listFiles();
    final List<String> l = new ArrayList<String>();
    for (final File f : fs) {
      if (f.isFile()) {
        l.add(f.getName());
      }
    }
    return l;
  }

  public boolean isMrcpServerConnected() {
    try {
      final MrcpClient client = _mrcpFactory.createMrcpClient(new Endpoint("", 1, null), Configuration.get()
          .getMediaAddress(), Configuration.get().getMediaServerPort(), new Properties());
      client.getAsrSession();
      client.getAsrSession().unjoin();
      client.getAsrSession().disconnect();
      client.getAsrSession().invalidate();
      return true;
    }
    catch (final Throwable t) {
      return false;
    }
  }

  public Date getSystemStartTime() {
    return _startTime;
  }
  
  public static void load(Configuration c, ServletContext ctx) throws Exception {
    final Class<? extends ApplicationManager> a = c.getAppManager();
    Method m = a.getMethod("initialization", ServletContext.class);
    m.invoke(null, ctx);
   }

  public static synchronized void initialization(final ServletContext ctx) throws InstantiationException, IllegalAccessException {
    if (APP_MGR == null) {
      final Class<? extends ApplicationManager> a = Configuration.get().getAppManager();
      LOG.info("Initializing Application Manager [" + a + "] ...");
      APP_MGR = a.newInstance();
      APP_MGR.init(ctx, Configuration.get().getAppManagerParas());
      ctx.setAttribute(ServletContextConstants.APP_MANAGER, APP_MGR);
      if (APP_MGR instanceof ApplicationMonitor) {
        ctx.setAttribute(ServletContextConstants.APP_MONITOR, APP_MGR);
      }
    }
  }
}
