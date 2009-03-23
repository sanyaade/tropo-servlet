package com.voxeo.tropo.util;

import java.io.IOException;
import java.io.Reader;

import org.apache.log4j.Logger;

public class ChainedReader extends Reader {
    private static final Logger LOG = Logger.getLogger(ChainedReader.class);
//  private CharArrayWriter _cache;
    
    protected final Reader _header;
    protected final Reader _next;
    protected boolean _completed = false;

    public ChainedReader(Reader reader, Reader chain) {
        _header = reader;
        _next = chain;
//    _cache = new CharArrayWriter();
    }

    public void close() throws IOException {
//      if (LOG.isDebugEnabled()) {
//          LOG.debug(_cache.toString());
//      }
        _header.close();
        _next.close();
    }

    public int read(char[] cbuf, int off, int len) throws IOException {
        int r = 0;
        if (!_completed) {
            int i = _header.read(cbuf, off, len);
            if (i < 0) {
                _completed = true;
                int x = _next.read(cbuf, off, len);
                r = x;
            } 
            else {
                r = i;
            }
        } 
        else {
            int i = _next.read(cbuf, off, len);
            r = i;
        }
//      if (r > 0 && LOG.isDebugEnabled()) {
//        _cache.write(cbuf, off, r);
//      }
        return r;
    }

    public int read() throws IOException {
        int r = -1;
        if (!_completed) {
            r = _header.read();
            if (r == -1) {
                _completed = true;
                r =  _next.read();
            }
        } 
        else {
            r = _next.read();
        }
//      if (r > 0 && LOG.isDebugEnabled()) {
//        _cache.write(r);
//      }
        return r;
    }

    public int read(char[] cbuf) throws IOException {
        int r = 0;
        if (!_completed) {
            int i = _header.read(cbuf);
            if (i < 0) {
                _completed = true;
                int x = _next.read(cbuf);
        r = x;
            } 
            else {
                r = i;
            }
        } 
        else {
            r = _next.read(cbuf);
        }
//      if (r > 0 && LOG.isDebugEnabled()) {
//        _cache.write(cbuf, 0, r);
//      }
        return r;
    }

  public String toString() {
//    if (_cache != null) {
//      return _cache.toString();
//    }
    return super.toString();
    }
}
