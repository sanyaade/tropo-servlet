package com.voxeo.tropo.app;

import javax.servlet.sip.SipURI;
import javax.servlet.sip.TelURL;
import javax.servlet.sip.URI;

import org.apache.log4j.Logger;

public class MockThriftAppMgr extends ThriftAppMgr {
  private static final Logger LOG = Logger.getLogger(MockThriftAppMgr.class);
  
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
    
    RemoteApplication app = getApplication(1, name);
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

}
