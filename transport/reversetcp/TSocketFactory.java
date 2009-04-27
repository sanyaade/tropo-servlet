package com.voxeo.tropo.transport.reversetcp;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.thrift.TException;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

/*
 * make sure to call shutdown when existing the application
 */
public class TSocketFactory implements Runnable, BindListener.Iface {
  private static final Logger LOG = Logger.getLogger(TSocketFactory.class);

  private static Map<String, TSocketFactory> FAC = new HashMap<String, TSocketFactory>();

  TServerSocket _serverSocket = null;
  TServer _server = null;
  int _port = 0;
  private Map<String, TTransport> _sockets = new HashMap<String, TTransport>();

  public synchronized static void shutdown() {
    Collection<TSocketFactory> s = FAC.values();
    for (TSocketFactory f : s) {
      try {
        f._serverSocket.close();
      }
      catch (Throwable t) {
        ;
      }
      try {
        f._server.stop();
      }
      catch (Throwable t) {
        ;
      }
    }

  }

  public synchronized static TSocketFactory getInstance(int localport) throws TTransportException {
    String key = "" + localport;
    TSocketFactory f = FAC.get(key);
    if (f == null) {
      f = new TSocketFactory(localport);
      FAC.put(key, f);
    }
    return f;
  }

  public synchronized static TSocketFactory getInstance(String localhost, int localport) throws TTransportException {
    String key = localhost + ":" + localport;
    TSocketFactory f = FAC.get(key);
    if (f == null) {
      f = new TSocketFactory(localhost, localport);
      FAC.put(key, f);
    }
    return f;
  }

  /**
   * 
   * @param host
   *          local host addre or name to connect to the remote socket
   * @param port
   *          local port used to connect to the remote socket
   * @throws TTransportException
   */
  public TSocketFactory(String host, int port) throws TTransportException {
    InetAddress addr = null;
    if (host == null) {
      throw new TTransportException(TTransportException.UNKNOWN, "Host address is null");
    }
    else {
      try {
        addr = InetAddress.getByName(host);
      }
      catch (UnknownHostException e) {
        throw new TTransportException(TTransportException.UNKNOWN, "Host address [" + host + "] is invalid.");
      }
    }
    _serverSocket = new TServerSocket(new InetSocketAddress(addr, port));
    _port = port;
    init();
  }

  /**
   * 
   * @param port
   *          local port used to connect to the remote socket
   */

  public TSocketFactory(int port) throws TTransportException {
    _serverSocket = new TServerSocket(port);
    _port = port;
    init();
  }

  protected void init() {
    _server = new BindServer(new BindListener.Processor(this), _serverSocket);
    new Thread(this, "thrift").start();
  }

  public TTransport getTransport(String uuid) throws TTransportException {
    TTransport t = _sockets.get(uuid);
    if (t == null) {
      throw new TTransportException(TTransportException.NOT_OPEN, "Socket is not connected for token[" + uuid + "].");
    }
    return t;
  }

  public String bind(String secrete) throws TException {
    return secrete;
  }

  public String bind(String secrete, TTransport t) throws TException {
    _sockets.put(secrete, t);
    LOG.info("A new connection is established :" + secrete + "=>" + ((TSocket) t).getSocket().getRemoteSocketAddress() + "->" + ((TSocket) t).getSocket().getLocalSocketAddress());
    return secrete;
  }

  public void run() {
    LOG.info("Starting reverse TCP  service on port " + _port);
    _server.serve();
    LOG.info("Stopped reverse TCP  service on port " + _port);
  }
}
