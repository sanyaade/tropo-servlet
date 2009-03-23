package com.voxeo.tropo.core;

import java.io.IOException;

import javax.servlet.sip.SipServletRequest;

public interface OutgoingCall extends Call {
  boolean isAnswerOnMedia();

  void update(SipServletRequest invite) throws IOException;
}
