package com.voxeo.tropo.util;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import javax.script.ScriptException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.sip.ConvergedHttpSession;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPBodyElement;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.soap.SOAPBinding;

import org.apache.log4j.Logger;
import org.apache.log4j.MDC;

import com.micromethod.common.util.StringUtils;
import com.voxeo.Guido.Guido;
import com.voxeo.Guido.GuidoException;
import com.voxeo.logging.LoggingContext;
import com.voxeo.tropo.Configuration;
import com.voxeo.tropo.ServletContextConstants;
import com.voxeo.tropo.app.Application;
import com.voxeo.tropo.app.ApplicationInstance;

public class Utils {
  
  private static final Logger LOG = Logger.getLogger(Utils.class);
  
  protected static Pattern HttpURLPattern = Pattern.compile("[h|H][t|T][t|T][p|P]://\\S*");

  protected static Pattern FileURLPattern = Pattern.compile("[f|F][i|I][l|L][e|E]://\\S*");

  protected static Pattern FtpURLPattern = Pattern.compile("[f|F][t|T][p|P]://\\S*");

  protected static Pattern DtmfPattern = Pattern.compile("[d|D][t|T][m|M][f|F]:\\S*");

  public static final String HOSTNAME;

  private static final String _wsdlDocumentLocation = "http://evolution.voxeo.com/services/AccountManagement?wsdl";
  
  static {
    String hostname;
    try {
      hostname = java.net.InetAddress.getLocalHost().getCanonicalHostName();
    }
    catch (UnknownHostException e) {
      hostname = "Unknown";
    }
    HOSTNAME = hostname;
  }

  public static byte[] genSSML(final String tts, final boolean parse) {
    String retval = tts;
    retval = retval.trim();
    if (!retval.startsWith("<")) {
      if (parse) {
        retval = HttpURLPattern.matcher(retval).replaceAll("<audio src=\"$0\"/>");
        retval = FileURLPattern.matcher(retval).replaceAll("<audio src=\"$0\"/>");
        retval = FtpURLPattern.matcher(retval).replaceAll("<audio src=\"$0\"/>");
        retval = DtmfPattern.matcher(retval).replaceAll("<audio src=\"$0\"/>");
      }
      retval = MessageFormat.format("<?xml version=\"1.0\"?><speak>{0}</speak>", retval);
    }
    return retval.getBytes();
  }

  public static String prefixNumber(final String number) {
    if (number == null || number.length() == 0 || StringUtils.startsWithIgnoreCase(number, "sip:")
        || StringUtils.startsWithIgnoreCase(number, "tel:") || StringUtils.startsWithIgnoreCase(number, "sip:")) {
      return number;
    }
    if (number.startsWith("+")) {
      return "tel:" + number;
    }
    try {
      Long.parseLong(number);
      return "tel:" + number;
    }
    catch (final NumberFormatException e) {
      return "sip:" + number;
    }
  }

  public static String processFrom(final String number, final String ip) {
    String retval = prefixNumber(number);
    if (retval == null) {
      ;
    }
    else if (StringUtils.startsWithIgnoreCase(retval, "tel:")) {
      String local = ip;
      if (local == null) {
        local = Configuration.get().getLocalAddress();
      }
      retval = retval.replaceFirst("[t|T][e|E][l|L]:", "sip:") + "@" + local;
    }
    return retval;
  }

  public static String processTo(final String number) {
    String retval = prefixNumber(number);
    final String sbc = Configuration.get().getPhoneSBC();
    if (retval == null || sbc == null || sbc.length() == 0 || number.length() == 0) {
      ;
    }
    else if (StringUtils.startsWithIgnoreCase(retval, "sip:") || StringUtils.startsWithIgnoreCase(retval, "sips:")) {
      if (retval.indexOf("@10.") < 0) {
        retval = retval.replaceFirst("@", "!") + "@" + sbc;
      }
    }
    else if (StringUtils.startsWithIgnoreCase(retval, "tel:")) {
      retval = retval.replaceFirst("[t|T][e|E][l|L]:", "sip:") + "@" + sbc;
    }
    return retval;
  }

  public static String getManifestAttribute(final String jarUrl, final String attribute) throws IOException {
    final JarFile jar = new JarFile(jarUrl);
    final Attributes attr = jar.getManifest().getMainAttributes();
    final String value = attr.getValue(attribute);
    jar.close();
    return value;
  }
  
  public static void setLogContext(SipServletMessage msg) {
    ApplicationInstance ai = (ApplicationInstance) msg.getAttribute(ApplicationInstance.INST);
    if (ai != null) {
      setLogContext(ai, msg.getCallId());
    }
  }
  
   public static void setLogContext(Application app, ServletRequest req) {
    if (app == null) {
      return;
    }
    String aid = String.valueOf(app.getAccountID());
    String pid = null;
    String sid = null;
    String cid = "-1";
    if (req != null) {
      SipApplicationSession session = null;
      if (req instanceof SipServletRequest) {
        SipServletRequest request = (SipServletRequest) req;
        session = request.getSession().getApplicationSession();
        pid = request.getHeader("x-sid");
        cid = request.getCallId();
      }
      else if (req instanceof HttpServletRequest) {
        session = ((ConvergedHttpSession) ((HttpServletRequest) req).getSession()).getApplicationSession();
      }
      if (session != null && session.isValid()) {
        sid = (String) session.getAttribute(ServletContextConstants.GUID_SESSION_ID);
      }
    }
    setLogContext(aid, pid, sid, cid);
  }

  public static void setLogContext(ApplicationInstance app, String callID) {
    if (app == null) {
      return;
    }
    String aid = String.valueOf(app.getApp().getAccountID());
    String pid = app.getParentSessionId();
    String sid = null;
    SipApplicationSession session = app.getApplicationSession();
    if (session.isValid()) {
      sid = (String) session.getAttribute(ServletContextConstants.GUID_SESSION_ID);
    }
    if (callID == null || callID.length() == 0) {
      callID = "-1";
    }
    setLogContext(aid, pid, sid, callID);
  }

  public static void setLogContext(String aid, String pid, String sid, String callID) {
    final LoggingContext context = LoggingContext.get();
    if (context == null) {
      return;
    }
    context.setCommand("1");
    context.setAccountID(aid);
    context.setHost(Utils.HOSTNAME);
    context.setSessionGUID(pid == null ? "-1" : pid);
    context.setSessionNumber(sid == null ? "-1" : sid);
    MDC.put("CallID", callID);
  }
  
  public static String buildScriptExceptionMessage(Object obj, String action, ScriptException e) {
    String msg = obj.toString() + " has " + action + " errors: " + e.getMessage();
    if (e.getLineNumber() > 0) {
      msg += " at line #" + e.getLineNumber();
      if (e.getColumnNumber() > 0) {
        msg += " and column #" + e.getColumnNumber();
      }
    }
    if (e.getCause() != null) {
      msg += " with cause: " + e.getCause();
    }
    return msg;
  }

  public static String pythonHome() {
    String python = System.getProperty("python.home");
    if (python == null) {
      python = System.getenv("python.home");
    }
    if (python == null) {
      python = System.getenv("JYTHON_HOME");
    }
    if (python != null && new File(python).isDirectory()) {
      System.setProperty("python.home", python);
      LOG.info("python.home is " + python);
      return python;
    }
    LOG.error("JYTHON_HOME is NOT defined or the referred directory does not exist!");
    return null;
  }

  public static String rubyHome() {
    String jruby = System.getProperty("jruby.home");
    if (jruby == null) {
      jruby = System.getenv("jruby.home");
    }
    if (jruby == null) {
      jruby = System.getenv("JRUBY_HOME");
    }
    if (jruby != null && new File(jruby).isDirectory()) {
      System.setProperty("jruby.home", jruby);
      System.setProperty("com.sun.script.jruby.loadpath", jruby);
      System.setProperty("com.sun.script.jruby.terminator", "on");
      LOG.info("jruby.home is " + jruby);
      return jruby;
    }
    LOG.error("JRUBY_HOME is NOT defined or the referred directory does not exist!");
    return null;
  }

  public static String getAppDir() {
    String appDir = System.getProperty("tropo.app.home");
    if (appDir == null) {
      appDir = System.getenv("tropo.app.home");
    }
    if (appDir == null) {
      appDir = System.getenv("TROPO_APP_HOME");
    }
    if (appDir != null && new File(appDir).isDirectory()) {
      LOG.info("Tropo app dir is " + appDir);
      return appDir;
    }
    LOG.error("TROPO_APP_HOME is NOT defined or the referred directory does not exist!");
    return null;
  }

  public static String getGUID() {
    try {
      return new Guido(null).toString();
    }
    catch (GuidoException e) {
      LOG.error("Error generating GUID,", e);
      return "ERROR";
    }
  }
  

  public static String authenticate(String username, String password) throws SOAPException, MalformedURLException {
    // Qnames for service as defined in wsdl.
    QName serviceName = new QName("http://localhost/services", "AccountManagementService");

    // QName for Port As defined in wsdl.
    QName portName = new QName("http://localhost/services", "AccountManagement");

    // Create a dynamic Service instance
    Service service = Service.create(new URL(_wsdlDocumentLocation), serviceName);

    // Create a dispatch instance
    Dispatch<SOAPMessage> dispatch = service.createDispatch(portName, SOAPMessage.class, Service.Mode.MESSAGE);

    // Use Dispatch as BindingProvider
    BindingProvider bp = (BindingProvider) dispatch;

    // Optionally Configure RequestContext to send SOAPAction HTTP Header
    Map<String, Object> rc = bp.getRequestContext();
    rc.put(BindingProvider.SOAPACTION_USE_PROPERTY, Boolean.TRUE);
    rc.put(BindingProvider.SOAPACTION_URI_PROPERTY, "getAccessToken");

    // Obtain a preconfigured SAAJ MessageFactory
    MessageFactory factory = ((SOAPBinding) bp.getBinding()).getMessageFactory();

    // Create SOAPMessage Request
    SOAPMessage request = factory.createMessage();

    // Request Header
    SOAPHeader header = request.getSOAPHeader();

    // Request Body
    SOAPBody body = request.getSOAPBody();

    // Compose the soap:Body payload
    QName payloadName = new QName("http://localhost/services", "getAccessToken", "ns1");
    SOAPBodyElement payload = body.addBodyElement(payloadName);
    SOAPElement message = payload.addChildElement("arg0");
    message.addTextNode(username);

    message = payload.addChildElement("arg1");
    message.addTextNode(password);

    // Invoke the endpoint synchronously
    SOAPMessage reply = null;

    reply = dispatch.invoke(request);

    // process the reply
    body = reply.getSOAPBody();

    QName responseName = new QName("http://localhost/services", "getAccessTokenResponse");

    SOAPBodyElement bodyElement = (SOAPBodyElement) body.getChildElements(responseName).next();
    String message1 = ((SOAPBodyElement) body.getChildElements(responseName).next()).getTextContent();
    return message1;
  }
}
