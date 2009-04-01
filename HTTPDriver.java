package com.voxeo.tropo;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.Properties;

import javax.annotation.Resource;
import javax.script.ScriptException;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.sip.ConvergedHttpSession;
import javax.servlet.sip.SipFactory;

import org.apache.log4j.Logger;

import com.voxeo.tropo.app.AbstractApplicationManager;
import com.voxeo.tropo.app.Application;
import com.voxeo.tropo.app.ApplicationManager;
import com.voxeo.tropo.app.InvalidApplicationException;
import com.voxeo.tropo.app.RedirectException;
import com.voxeo.tropo.util.Utils;

@SuppressWarnings("serial")
public class HTTPDriver extends HttpServlet {
  private static final Logger LOG = Logger.getLogger(SIPDriver.class);
  
  protected ApplicationManager _appMgr;
  
  @Resource
  protected SipFactory _sipFactory;
    
  @Override
  public void init() {
    final ServletContext ctx = getServletContext();
    try {
      Configuration.init(this.getServletConfig());
      AbstractApplicationManager.load(Configuration.get(), ctx);
    }
    catch (final Exception e) {
      LOG.error("Unable to initialize Tropo:", e);
      throw new RuntimeException(e);
    }

    _appMgr = (ApplicationManager)ctx.getAttribute(ServletContextConstants.APP_MANAGER);
  }
  
  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
    
  @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      String action = req.getParameter("action");
      if (action == null || !"create".equalsIgnoreCase(action)) {
        resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        return;
      }
      String token = req.getParameter("token");
      if (token == null) {
      resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE);
        return;
      }
      String vars = req.getParameter("vars");
      LOG.info("A new token[" + token + "] is received" + (vars == null ? "." : (" with vars=" + vars)));
      Properties params = null;
      if (vars != null) {
        params = new Properties();
        vars = URLDecoder.decode(vars, "UTF-8");
        String[] nvps = vars.split("&");
        for (String nvp : nvps) {
          String[] nv = nvp.split("=");
          if (nv.length > 1) {
            params.put(nv[0], URLDecoder.decode(nv[1], "UTF-8"));
          }
        }
      }
    try {
      // set guid session id for logging
      ((ConvergedHttpSession) req.getSession()).getApplicationSession().setAttribute(ServletContextConstants.GUID_SESSION_ID, Utils.getGUID());
      final Application app = _appMgr.get(token, params);
      Utils.setLogContext(app, req);
      app.execute(req);
      resp.setStatus(HttpServletResponse.SC_OK);
    }
    catch (final RedirectException e) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.toString());      
    }
    catch (final InvalidApplicationException e) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.toString());
    }
    catch (final ScriptException e) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.toString());
    }
    catch (final IOException e) {      
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.toString());
   }
    }   
    
}
