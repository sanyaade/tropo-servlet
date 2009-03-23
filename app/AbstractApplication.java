package com.voxeo.tropo.app;

import java.io.Serializable;
import java.util.Properties;

import javax.servlet.ServletRequest;
import javax.servlet.sip.SipFactory;

import com.voxeo.tropo.util.Utils;
import com.voxeo.sipmethod.mrcp.client.MrcpFactory;

public abstract class AbstractApplication implements Application, Serializable {

  protected String _type;

  protected int _accountId;
  
  protected String _applicationId;

  protected ApplicationURL _url;

  protected transient ApplicationManager _mgr;

  protected Properties _params;
  
  public AbstractApplication(final ApplicationManager mgr, final ApplicationURL url, final String type, final int aid, final String appId, final Properties params) {
    _mgr = mgr;
    _url = url;
    _type = type;
    _params = params;
    _accountId = aid;
    _applicationId = appId;
  }
  
  public int getAccountID() {
    return _accountId;
  }

  public String getApplicationID() {
    return _applicationId;
  }

  public ApplicationManager getManager() {
    return _mgr;
  }

  public Properties getParameters() {
    return _params;
  }

  public String getType() {
    return _type;
  }

  public ApplicationURL getURL() {
    return _url;
  }
  
  public SipFactory getSipFactory() {
    return _mgr.getSipFactory();
  }

  public MrcpFactory getMrcpFactory() {
    return _mgr.getMrcpFactory();
  }


  @Override
  public String toString() {
    return "Application[" + _url + "] ver(" + getManager().getVersionNo() + ")";
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null) {
      return false;
    }
    if (!(o instanceof Application)) {
      return false;
    }
    final Application right = (Application) o;
    if (right.getAccountID() != getAccountID()) {
      return false;
    }
    ApplicationURL url = getURL();
    if (url != null) {
      if (!url.equals(right.getURL())) {
        return false;
      }      
    }
    String type = getType();
    if (type != null) {
      if (!type.equals(right.getType())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return getAccountID() + getURL().hashCode();
  }

  public void setLogContext(final ServletRequest req) {
    Utils.setLogContext(this, req);
  }

}
