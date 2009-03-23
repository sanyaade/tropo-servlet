package com.voxeo.tropo.app;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import com.voxeo.tropo.thrift.BindStruct;

public class ThriftURL implements ApplicationURL {
  String _host;
  int _port;
  String _secrete;

  ThriftURL(BindStruct bind) {
    _host = bind.getLocalHost();
    _port = bind.getLocalPort();
    _secrete = bind.getSecret();
  }
  
  public String getAuthority() {
    return _secrete;
  }

  public String getFile() {
    // TODO Auto-generated method stub
    return null;
  }

  public String getHost() {
    return _host;
  }

  public long getLastAccessedTime() {
    // TODO Auto-generated method stub
    return 0;
  }

  public String getPath() {
    // TODO Auto-generated method stub
    return null;
  }

  public int getPort() {
    return _port;
  }

  public String getProtocol() {
    return "thrift";
  }

  public String getQuery() {
    // TODO Auto-generated method stub
    return null;
  }

  public String getRef() {
    // TODO Auto-generated method stub
    return null;
  }

  public String getRequestMethod() {
    // TODO Auto-generated method stub
    return null;
  }

  public String getUserInfo() {
    // TODO Auto-generated method stub
    return null;
  }

  public InputStream openStream(boolean force) throws IOException, UnmodifiedException {
    throw new UnsupportedOperationException();
  }

  public URL toURL() {
    throw new UnsupportedOperationException();
  }

}
