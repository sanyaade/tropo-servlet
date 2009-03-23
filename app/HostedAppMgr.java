package com.voxeo.tropo.app;

import java.util.Properties;

import javax.servlet.sip.SipURI;
import javax.servlet.sip.TelURL;
import javax.servlet.sip.URI;

import org.apache.log4j.Logger;

import com.voxeo.webcore.dns.URLByNumberGet;
import com.voxeo.webcore.dns.URLByTokenGet;
import com.voxeo.webcore.dns.VoxeoDNSException;

public class HostedAppMgr extends AbstractLocalApplicationManager implements ApplicationManager {
  private static final Logger LOG = Logger.getLogger(HostedAppMgr.class);

  /**
   * The current logic looks from DNS server for a script file URL based on the
   * SIP URL user name. If the script file name is abc.js (or abc_js), it
   * creates an application of JavaScript type
   */
  @Override
  protected Application findApplication(final URI uri) throws InvalidApplicationException, RedirectException {
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
      Application a = createApplication(info.getURL(), info.getAccountID(), info.getApplicationID(), null);
      LOG.info(this + " found the hosted " + a + " for " + uri);
      return a;
    }
    catch(VoxeoDNSException e) {
      LOG.error(e.toString(), e);
      throw new InvalidApplicationException("Unable to retrieve DNS records for " + uri);
    }
  }

  @Override
  protected Application findApplication(String token, Properties params) throws InvalidApplicationException, RedirectException {
    URLByTokenGet info = new URLByTokenGet();
    info.execute(token);
    Application a = createApplication(info.getURL(), info.getAccountID(), info.getApplicationID(), params);
    LOG.info(this + " found the hosted " + a + " for " + token);
    return a;
  }
  
  @Override
  public String toString() {
    return "Tropo(Hosting Edition)";
  }
}
