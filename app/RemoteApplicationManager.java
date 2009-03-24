package com.voxeo.tropo.app;

import java.io.IOException;

import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.sip.SipServletRequest;

public interface RemoteApplicationManager extends ApplicationManager {
  void execute(SipServletRequest req, RemoteApplication ap) throws IOException, ScriptException;
  void execute(HttpServletRequest req, RemoteApplication ap) throws IOException, ScriptException;
}
