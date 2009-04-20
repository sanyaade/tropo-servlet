package com.voxeo.tropo.util;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.sip.SipSession;

import com.micromethod.common.util.jmx.JmxHelper;
import com.micromethod.common.util.jmx.ObjectNameFactory;
import com.voxeo.tropo.core.CallImpl;

public class DumpHelper {

  private static MBeanServer SERVER = null;

  private static final ObjectName SESSION_MANAGER = ObjectNameFactory
      .create("com.micromethod.sipmethod:name=session,type=server.service.sip.manager");

  public static void initialization(final MBeanServer server) {
    SERVER = server;
  }

  @SuppressWarnings("unchecked")
  public static List<String> dumpCall() {
    final List<String> retval = new ArrayList<String>();
    final Collection<SipSession> sessions = (Collection<SipSession>) JmxHelper.getMBeanAttribute(SERVER,
        SESSION_MANAGER, "Sessions");
    int index = 0;
    for (final SipSession session : sessions) {
      index = index + 1;
      CallImpl call = null;
      if (session.isValid()) {
        try {
          call = (CallImpl) session.getAttribute(CallImpl.INST);
        }
        catch (final Throwable t) {
          ;
        }
      }
      String message = MessageFormat.format("{0}. ID={1}, State={2}, Call={3}", index, session.getId(), session
          .getState(), call);
      if (call != null) {
        message = MessageFormat.format(message + ", CallState={0}, CallCreatedTime={1}", call.getState(), new Date(call
            .getCreatedTime()));
      }
      message = message + ";";
      retval.add(message);
    }
    return retval;

  }
}
