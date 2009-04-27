package com.voxeo.tropo.transport.reversetcp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;

/**
 * 1. It is ONLY able to get connection from a specified remote host and remote
 * port<br>
 * 2. it is ONLY able to return ONE TSocket instance and then block for ever for
 * the second invocation on accept() method.
 */
public class TServerSocket_R extends TServerTransport {

  private static final Logger LOGGER = Logger.getLogger(TServerSocket_R.class.getName());

  /**
   * Underlying serversocket object
   */
  protected TSocket tSocket_ = null;

  protected boolean isAccepted_ = false;

  protected Object lock_ = new Object();

  protected boolean isRunning_ = false;

  protected String uuid_ = null;

  public TServerSocket_R(TSocket socket, String uuid) throws IOException {
    tSocket_ = socket;
    uuid_ = uuid;
  }

  public TServerSocket_R(int remotePort, String remoteHost, String uuid) {
    tSocket_ = new TSocket(remoteHost, remotePort);
    uuid_ = uuid;
  }

  public TServerSocket_R(int remotePort, String remoteHost, int clientTimeout, String uuid) {
    tSocket_ = new TSocket(remoteHost, remotePort, clientTimeout);
    uuid_ = uuid;
  }

  public TServerSocket_R(int localPort, String localHost, int remotePort, String remoteHost, int clientTimeout, String uuid) throws IOException {
    InetAddress addr = null;
    if (localHost == null) {
      addr = InetAddress.getLocalHost();
    }
    else {
      addr = InetAddress.getByName(localHost);
    }
    tSocket_ = new TSocket(remoteHost, remotePort, clientTimeout);
    tSocket_.getSocket().bind(new InetSocketAddress(addr, localPort));
    uuid_ = uuid;

    // try {
    // } catch (IOException ioe) {
    // tSocket_ = null;
    // throw new TTransportException("Could not create ServerSocket on address "
    // + bindAddr.toString() + ".");
    // }
  }

  public int getPort() {
    return tSocket_.getSocket().getLocalPort();
  }

  public void listen() throws TTransportException {
    if (tSocket_ != null) {
      if (!tSocket_.isOpen()) {
        tSocket_.open();
      }
      TBinaryProtocol binaryProtocol = new TBinaryProtocol(tSocket_);
      BindListener.Client client = new BindListener.Client(binaryProtocol);
      try {
        client.bind(uuid_);
      }
      catch (TException e) {
        new TTransportException(TTransportException.UNKNOWN, e);
      }
      LOGGER.log(Level.INFO, tSocket_.getSocket() + " is connectioned.");
      isRunning_ = true;
    }
  }

  /**
   * Only return ONE TSocket instance and then block for ever for the second
   * invocation on this method.
   */
  protected TSocket acceptImpl() throws TTransportException {
    if (tSocket_ == null) {
      throw new TTransportException(TTransportException.NOT_OPEN, "No underlying server socket.");
    }
    if (isAccepted_) {
      // throw new TTransportException(TTransportException.ALREADY_OPEN,
      // "Connection is full for this server socket:" + tSocket_);
      synchronized (lock_) {
        while (isRunning_) {
          try {
            lock_.wait();
          }
          catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }
      }
      //throw new TTransportException(TTransportException.NOT_OPEN, "Connection has been closed for " + tSocket_);\
      return null;
    }
    else {
      isAccepted_ = true;
      return tSocket_;
    }
  }

  public void close() {
    if (tSocket_ != null) {
      tSocket_.close();
      isRunning_ = false;
      synchronized (lock_) {
        lock_.notifyAll();
      }
    }
  }

  public void interrupt() {
    // The thread-safeness of this is dubious, but Java documentation suggests
    // that it is safe to do this from a different thread context
    close();
  }

}
