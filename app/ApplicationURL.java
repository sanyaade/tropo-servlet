package com.voxeo.tropo.app;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * ApplicationURL tries to encapsulate a HTTP client implementation
 * (e.g. java.net.URL) to make sure
 * 1. Multiple proxy servers are properly handled
 * 2. Redirections are properly handled
 * 3. Cache control is properly handled
 * 4. If-Modified-Since is properly handled.
 */
public interface ApplicationURL {
    long getLastAccessedTime();
    String getAuthority();
    String getFile();
    String getHost();
    String getPath();
    int getPort();
    String getProtocol();
    String getQuery();
    String getRef();
    String getUserInfo();
    String getRequestMethod();
    URL toURL();
    InputStream openStream(boolean force) throws IOException, UnmodifiedException;
    
    // TODO: proxy server settings
    // TODO: SSL settings
    // TODO: cache settings
    // TODO: timeout settings
    
    @SuppressWarnings("serial")
    public class UnmodifiedException extends Exception {
        public UnmodifiedException() {
        }

        public UnmodifiedException(final String arg0) {
            super(arg0);
        }

        public UnmodifiedException(final Throwable arg0) {
            super(arg0);
        }

        public UnmodifiedException(final String arg0, final Throwable arg1) {
            super(arg0, arg1);
        }
    }
}
