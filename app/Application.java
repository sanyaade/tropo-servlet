package com.voxeo.tropo.app;

import java.io.IOException;
import java.util.Properties;

import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.sip.SipServletRequest;

public interface Application {

  ApplicationManager getManager();

  void dispose();

  ApplicationURL getURL();

  String getType();
  
  int getAccountID();
  
  String getApplicationID();
    
  Properties getParameters();

  void execute(SipServletRequest invite) throws ScriptException, IOException;

  void execute(HttpServletRequest invite) throws ScriptException, IOException;
}
