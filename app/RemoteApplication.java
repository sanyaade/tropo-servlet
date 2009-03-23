package com.voxeo.tropo.app;

import java.util.List;

import javax.servlet.sip.SipURI;

public interface RemoteApplication extends Application {
  String getApplicationKey();
  boolean isProxy();
  boolean isActive();
  List<SipURI> getContacts();
}
