package com.voxeo.tropo.app;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class JavaURL implements ApplicationURL {
    static {
        HttpURLConnection.setFollowRedirects(true);
    }
    
    URL _url;
    String _method;
//  HttpURLConnection _connection;
    long _lastAccessedTime;
    
    public JavaURL(URL url, String method) throws IOException {
        _url = url;
        _method = method;
//      _connection = (HttpURLConnection)_url.openConnection();
//      _connection.setRequestMethod(method);
        _lastAccessedTime = 0;
    }

    public String getAuthority() {
        return _url.getAuthority();
    }

    public String getFile() {
        return _url.getFile();
    }

    public String getHost() {
        return _url.getHost();
    }

    public long getLastAccessedTime() {
        return _lastAccessedTime;
    }

    public String getPath() {
        return _url.getPath();
    }

    public int getPort() {
        return _url.getPort();
    }

    public String getProtocol() {
        return _url.getProtocol();
    }

    public String getQuery() {
        return _url.getQuery();
    }

    public String getRef() {
        return _url.getRef();
    }

    public String getRequestMethod() {
        return _method;
    }

    public String getUserInfo() {
        return _url.getUserInfo();
    }

    public InputStream openStream(boolean force) throws IOException, UnmodifiedException {
      HttpURLConnection connection = (HttpURLConnection)_url.openConnection();
      connection.setRequestMethod(_method);
        if (!force) {
            connection.setIfModifiedSince(_lastAccessedTime);
        }
        int code = connection.getResponseCode();
        if (!force && code == HttpURLConnection.HTTP_NOT_MODIFIED) {
            throw new UnmodifiedException();
        }
    if (connection.getLastModified() > 0) {
      _lastAccessedTime = connection.getLastModified();
    }
    else {
      _lastAccessedTime = System.currentTimeMillis();
    }
        return connection.getInputStream();
    }
    
    public String toString() {
      return _url.toString();
    }
    
    public URL toURL() {
      return _url;
    }
    
    public boolean equals(Object o) {
      if (o == null) {
        return false;
      }
      if (!(o instanceof ApplicationURL)) {
        return false;
      }
      ApplicationURL right = (ApplicationURL)o;
      if (!right.toURL().equals(toURL())) {
        return false;
      }
      return true;
    }
    
    public int hashCode() {
      return _url.hashCode();
    }

}
