package com.voxeo.tropo;

public interface ServletContextConstants {
  String APP_MANAGER = "com.voxeo.tropo.ApplicationManager";
  String APP_MONITOR = "com.voxeo.tropo.ApplicationMonitor";
  String ENGINE_MANAGER = "com.voxeo.tropo.ScriptEngineManager";
  String SIP_FACTORY = "javax.servlet.sip.SipFactory";
  String MRCP_FACTORY = "com.voxeo.mrcp.client.MrcpFactory";

  String CONTENT_TYPE_SDP = "application/sdp";
  
  String GUID_SESSION_ID = "com.voxeo.tropo.session.id";
  String GUID_PARENT_SESSION_ID = "com.voxeo.tropo.session.parent.id";
}
