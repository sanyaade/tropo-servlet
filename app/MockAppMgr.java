package com.voxeo.tropo.app;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;

import org.apache.log4j.Logger;

public class MockAppMgr extends AbstractLocalApplicationManager implements ApplicationManager {
  private static final Logger LOG = Logger.getLogger(MockAppMgr.class);

  private String _appPosition = null;
  
  public void init(ServletContext context, Map<String, String> paras) {
    _appPosition = paras.remove("appPosition");
    if (_appPosition == null || _appPosition.length() < 1) {
      _appPosition = "http://127.0.0.1:8080/script/";
    }
    super.init(context, paras);
  }

  /**
   * The current logic looks for a script using SIP URI's user name as the
   * script file name. E.g., SIP URL user name is abc.js (or abc_js), it looks
   * for <sipmethod>/apps/tropo/script/abc.js as the application URL.
   */
  @Override
  protected Application findApplication(final URI uri) throws InvalidApplicationException, RedirectException {
    if (uri instanceof SipURI) {
      try {
        String name = ((SipURI) uri).getUser();
        return createApplication(name, 0, name, null);      
      }
      catch(Exception e) {
        LOG.warn("Unable to find the application for " + uri + ", but for test purpose, just use mock.js as test file name");        ;
      }
    }
    try {
      return createApplication(getURL("mock.js"), getCanonicalType("js"), 0, "mock.js", null);         
    }
    catch(Exception ex) { 
      throw new InvalidApplicationException(ex);
    }
  }

  public ApplicationURL createURL(String url, String method) throws MalformedURLException, IOException {
    return new JavaURL(new URL(getURL(url)), method);
  }

  @SuppressWarnings("unchecked")
  protected String getURL(final String name) {
    return _appPosition + name;
  }

  @Override
  protected Application findApplication(String token, Properties params) throws InvalidApplicationException, RedirectException {
    return createApplication(token, 0, token, null);      
  }
  
  @Override
  public String toString() {
    return "Testing Edition";
  }

}
