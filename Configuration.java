package com.voxeo.tropo;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;

import org.apache.log4j.Logger;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import com.micromethod.common.util.CodeUtils;
import com.micromethod.common.util.NetworkUtils;
import com.micromethod.common.util.annotation.StringPart;
import com.voxeo.tropo.app.ApplicationManager;
import com.voxeo.tropo.app.MockAppMgr;
import com.voxeo.tropo.util.ConfigurationItem;
import com.voxeo.tropo.util.Utils;

public class Configuration {
  public static final Logger LOG = Logger.getLogger(Configuration.class);

  private static Configuration INSTANCE = null;

  private static String LOCAL_ADDRESS = NetworkUtils.getLocalAddress().getHostAddress();

  @StringPart
  private String _mediaServer = "127.0.0.1";

  @StringPart
  private int _mediaServerPort = 9974;

  @StringPart
  @ConfigurationItem
  private int maxTimeSpeak = 300000;

  @StringPart
  @ConfigurationItem
  private int maxTimeListen = 60000;

  @StringPart
  @ConfigurationItem
  private String termTimeout = "0";

  @StringPart
  @ConfigurationItem
  private String termChar = "#";

  @StringPart
  @ConfigurationItem
  private String noInputTimeout = "10000";

  @StringPart
  @ConfigurationItem
  private String confidenceLevel = "0.3";

  @StringPart
  @ConfigurationItem
  private String sensitivity = "0.5";

  @StringPart
  @ConfigurationItem
  private String speedVsAccuracy = "0.5";

  @StringPart
  @ConfigurationItem
  private String asrSpeechLanguage = "en-us-prophecy";

  @StringPart
  @ConfigurationItem
  private String ttsSpeechLanguage = "en-US";

  @StringPart
  @ConfigurationItem
  private boolean killOnBargeIn = true;

  @StringPart
  @ConfigurationItem
  private String beepURL = "http://127.0.0.1:8080/beep.wav";

  @StringPart
  @ConfigurationItem
  private String phoneSBC = "sbc-staging-internal.orl.voxeo.net";

  @StringPart
  @ConfigurationItem
  private boolean parseSpeechText = true;

  @StringPart
  @ConfigurationItem
  private boolean hackSDP = true;

  @StringPart
  @ConfigurationItem
  private String mediaAddress = null;

  @StringPart
  private Map<String, String> _scriptHeaders = new HashMap<String, String>();

  @StringPart
  private Map<String, Integer> _enginePoolSizes = new HashMap<String, Integer>();

  @StringPart
  private Class<? extends ApplicationManager> _appManager = MockAppMgr.class;

  @StringPart
  private Map<String, String> _appManagerParas = new HashMap<String, String>();

  @StringPart
  @ConfigurationItem
  private boolean enableSecurityManager = true;

  @StringPart
  @ConfigurationItem
  private long appMonitorTime = 60000;

  @StringPart
  @ConfigurationItem
  private int threadSize = 400;

  public static Configuration get() {
    if (INSTANCE == null) {
      throw new IllegalArgumentException("Tropo configuratrion has not been initialized.");
    }
    return INSTANCE;
  }

  public static void init(final ServletConfig config) throws JDOMException, IOException, ClassNotFoundException {
    String xml = config.getInitParameter("configuration");
    if (xml == null) {
      xml = "/tropo.xml";
    }
    final InputStream i = Configuration.class.getResourceAsStream(xml);
    if (i == null) {
      throw new IOException("Unable to find configuration XML: " + xml);
    }
    try {
      INSTANCE = new Configuration(i);
      String home = Utils.pythonHome();
      if (home != null) {
        Configuration.LOG.info("python.home is " + home);
      }
      else {
        Configuration.LOG.error("JYTHON_HOME is NOT defined or the referred directory does not exist!");
      }

      home = Utils.rubyHome();
      if (home != null) {
        Configuration.LOG.info("jruby.home is " + home);
      }
      else {
        Configuration.LOG.error("JRUBY_HOME is NOT defined or the referred directory does not exist!");
      }

      home = Utils.getAppDir();
      if (home != null) {
        Configuration.LOG.info("Tropo app dir is " + home);
      }
      else {
        Configuration.LOG.error("TROPO_APP_HOME is NOT defined or the referred directory does not exist!");
      }
    }
    finally {
      i.close();
    }
  }

  @Override
  public String toString() {
    return CodeUtils.toStringByAnnotation(this);
  }

  @SuppressWarnings("unchecked")
  private Configuration(final InputStream i) throws JDOMException, IOException, ClassNotFoundException {
    _scriptHeaders.put("js", "com/voxeo/tropo/javascript/tropo.js");
    _scriptHeaders.put("groovy", "com/voxeo/tropo/groovy/tropo.groovy");
    _scriptHeaders.put("jython", "com/voxeo/tropo/jython/tropo.jy");
    _scriptHeaders.put("php", "com/voxeo/tropo/php/tropo.php");
    _scriptHeaders.put("jruby", "com/voxeo/tropo/jruby/tropo.rb");
    final SAXBuilder b = new SAXBuilder();
    b.setValidation(false);
    final Element f = b.build(i).getRootElement();

    enableSecurityManager = Boolean.parseBoolean(f.getAttributeValue("enableSecurityManager"));

    final String threads = f.getAttributeValue("threadSize");
    if (threads != null && threads.length() > 0) {
      threadSize = Integer.parseInt(threads);
    }

    // media server configurations
    final Element m = f.getChild("mediaServer");
    if (m != null) {
      if (m.getAttributeValue("host") != null) {
        _mediaServer = m.getAttributeValue("host");
      }
      if (m.getAttributeValue("port") != null) {
        _mediaServerPort = Integer.parseInt(m.getAttributeValue("port"));
      }
      quickSetValue(this, m);
    }
    if (mediaAddress == null || mediaAddress.length() == 0) {
      if (_mediaServer.equals("127.0.0.1")) {
        mediaAddress = LOCAL_ADDRESS;
      }
      else {
        mediaAddress = _mediaServer;
      }
    }
    // app manager configurations
    final Element a = f.getChild("appManager");
    if (a != null) {
      _appManager = (Class<? extends ApplicationManager>) this.getClass().getClassLoader().loadClass(
          a.getAttributeValue("class"));
      final List<Element> ls = a.getChildren("para");
      for (final Element p : ls) {
        _appManagerParas.put(p.getAttributeValue("name"), p.getAttributeValue("value"));
      }
    }
    // scripts configurations
    final Element s = f.getChild("scripts");
    if (s != null) {
      final List<Element> ls = s.getChildren("script");
      for (final Element p : ls) {
        String type = p.getAttributeValue("type");
        _scriptHeaders.put(type, p.getAttributeValue("file"));
        String size = p.getAttributeValue("enginePoolSize");
        if (size != null && size.length() > 0) {
          _enginePoolSizes.put(type, Integer.valueOf(size));
        }
        else {
          _enginePoolSizes.put(type, Integer.valueOf(20));
        }
      }
    }

    // debug loggging
    if (LOG.isDebugEnabled()) {
      LOG.debug("Loaded config " + toString());
    }
  }

  /**
   * @param _mediaServer
   *          the _mediaServer to set
   */
  public void setMediaServer(final String mediaServer) {
    this._mediaServer = mediaServer;
  }

  /**
   * @return the _mediaServer
   */
  public String getMediaServer() {
    return _mediaServer;
  }

  /**
   * @param _mediaServerPort
   *          the _mediaServerPort to set
   */
  public void setMediaServerPort(final int mediaServerPort) {
    this._mediaServerPort = mediaServerPort;
  }

  /**
   * @return the _mediaServerPort
   */
  public int getMediaServerPort() {
    return _mediaServerPort;
  }

  /**
   * @param _appManager
   *          the _appManager to set
   */
  public void setAppManager(final Class<? extends ApplicationManager> appManager) {
    this._appManager = appManager;
  }

  /**
   * @return the _appManager
   */
  public Class<? extends ApplicationManager> getAppManager() {
    return _appManager;
  }

  /**
   * @param _appManagerParas
   *          the _appManagerParas to set
   */
  public void setAppManagerParas(final Map<String, String> appManagerParas) {
    this._appManagerParas = appManagerParas;
  }

  /**
   * @return the _appManagerParas
   */
  public Map<String, String> getAppManagerParas() {
    return _appManagerParas;
  }

  public String getScriptHeader(final String type) {
    return _scriptHeaders.get(type);
  }
  
  public Map<String, Integer> getEnginePoolSizes() {
    return _enginePoolSizes;
  }
  
  public int getMaxTimeSpeak() {
    return maxTimeSpeak;
  }

  public int getMaxTimeListen() {
    return maxTimeListen;
  }

  public String getTermTimeout() {
    return termTimeout;
  }

  public String getTermChar() {
    return termChar;
  }

  public String getNoInputTimeout() {
    return noInputTimeout;
  }

  public String getConfidenceLevel() {
    return confidenceLevel;
  }

  public String getSensitivity() {
    return sensitivity;
  }

  public String getSpeedVsAccuracy() {
    return speedVsAccuracy;
  }

  public String getAsrSpeechLanguage() {
    return asrSpeechLanguage;
  }

  public String getTtsSpeechLanguage() {
    return ttsSpeechLanguage;
  }

  public boolean isKillOnBargeIn() {
    return killOnBargeIn;
  }

  public String getBeepURL() {
    return beepURL;
  }

  public String getPhoneSBC() {
    return phoneSBC;
  }

  public boolean isParseSpeechText() {
    return parseSpeechText;
  }

  public boolean getHackSDP() {
    return hackSDP;
  }

  public String getMediaAddress() {
    return mediaAddress;
  }

  public String getLocalAddress() {
    return LOCAL_ADDRESS;
  }

  public long getApplicationMonitorTime() {
    return appMonitorTime;
  }

  public void setApplicationMonitorTime(final long time) {
    appMonitorTime = time;
  }

  public int getThreadSize() {
    return threadSize;
  }

  private static void quickSetValue(final Object o, final Element m) {
    for (final Field field : o.getClass().getDeclaredFields()) {
      if (field.isAnnotationPresent(ConfigurationItem.class)) {
        final String name = field.getName();
        String value = m.getChildText(name);
        if (value != null) {
          value = value.trim();
          final boolean accessibility = field.isAccessible();
          try {
            field.setAccessible(true);
            final Class<?> type = field.getType();
            if (type == int.class) {
              field.set(o, Integer.parseInt(value));
            }
            else if (type == long.class) {
              field.set(o, Long.parseLong(value));
            }
            else if (type == boolean.class) {
              field.set(o, Boolean.parseBoolean(value));
            }
            else {
              field.set(o, value);
            }
          }
          catch (final Throwable t) {
            LOG.error("", t);
          }
          finally {
            field.setAccessible(accessibility);
          }
        }
      }
    }
  }

  /**
   * @return the enableSecurityManager
   */
  public boolean isEnableSecurityManager() {
    return enableSecurityManager;
  }

}
