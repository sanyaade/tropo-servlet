package com.voxeo.tropo.app;

import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.URI;

import com.voxeo.sipmethod.mrcp.client.MrcpFactory;

public interface ApplicationManager {
  SipFactory getSipFactory();
  MrcpFactory getMrcpFactory();
  void init(ServletContext context, Map<String, String> paras);
  Application get(URI uri) throws InvalidApplicationException, RedirectException;
  Application get(String token, Properties params) throws InvalidApplicationException, RedirectException;
  void dispose();
  String getBuildNo();
  String getVersionNo();
  String getBuildDate();
}
