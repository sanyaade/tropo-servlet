package com.voxeo.tropo.app;

import java.util.List;

import javax.servlet.sip.SipURI;

@SuppressWarnings("serial")
public class RedirectException extends Exception {
  List<SipURI> _contacts;

  public RedirectException(List<SipURI> contacts) {
    super();
    _contacts = contacts;
  }
  
  public List<SipURI> getContacts() {
    return _contacts;
  }
}
