package com.voxeo.tropo.app;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.TProcessorFactory;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.transport.TTransportFactory;

import com.voxeo.tropo.thrift.AlertStruct;
import com.voxeo.tropo.thrift.AuthenticationException;
import com.voxeo.tropo.thrift.BindException;
import com.voxeo.tropo.thrift.HangupStruct;
import com.voxeo.tropo.thrift.Notifier;
import com.voxeo.tropo.thrift.SystemException;

public class ThrifNotifyService extends TServer implements Runnable {

  private static final Logger LOG = Logger.getLogger(ThriftApplication.class);

  // Executor service for handling client connections
  private ExecutorService _executorService;

  // Flag for stopping the server
  private volatile boolean _stopped;

  protected Map<String, ThriftApplication> _index;

  protected int _port;

  public ThrifNotifyService(Map index, int port) throws TTransportException {

    this((Notifier.Processor) null, new TServerSocket(port), new TTransportFactory(), new TTransportFactory(), new TBinaryProtocol.Factory(), new TBinaryProtocol.Factory());
    // processorFactory_ = new TProcessorFactory(new Notifier.Processor(this));
    _index = index;
    _port = port;
  }

  public ThrifNotifyService(TProcessor processor, TServerTransport serverTransport, TTransportFactory inputTransportFactory, TTransportFactory outputTransportFactory,
      TProtocolFactory inputProtocolFactory, TProtocolFactory outputProtocolFactory) {
    this(new TProcessorFactory(processor), serverTransport, inputTransportFactory, outputTransportFactory, inputProtocolFactory, outputProtocolFactory);
  }

  public ThrifNotifyService(TProcessorFactory processorFactory, TServerTransport serverTransport, TTransportFactory inputTransportFactory, TTransportFactory outputTransportFactory,
      TProtocolFactory inputProtocolFactory, TProtocolFactory outputProtocolFactory) {
    super(processorFactory, serverTransport, inputTransportFactory, outputTransportFactory, inputProtocolFactory, outputProtocolFactory);
    _executorService = Executors.newCachedThreadPool();
  }

  public void run() {
    LOG.info("Starting Thrift Reverse TCP service on port " + _port);
    serve();
    LOG.info("Stopping Thrift Reverse TCP service on port " + _port);

  }

  public void serve() {
    try {
      serverTransport_.listen();
    }
    catch (TTransportException ttx) {
      LOG.error("Error occurred during listening.", ttx);
      return;
    }

    _stopped = false;
    while (!_stopped) {
      int failureCount = 0;
      try {
        TSocket client = (TSocket) serverTransport_.accept();
        WorkerProcess wp = new WorkerProcess(client);
        _executorService.execute(wp);
      }
      catch (TTransportException ttx) {
        if (!_stopped) {
          ++failureCount;
          LOG.warn("Transport error occurred during acceptance of message.", ttx);
        }
      }
    }
    _executorService.shutdown();
  }

  public void stop() {
    _stopped = true;
    serverTransport_.interrupt();
    serverTransport_.close();
  }

  private class WorkerProcess implements Runnable, Notifier.Iface {

    /**
     * Client that this services.
     */
    private TSocket _client;

    /**
     * Default constructor.
     * 
     * @param client
     *          Transport to process
     */
    private WorkerProcess(TSocket client) {
      _client = client;
    }

    /**
     * Loops on processing a client forever
     */
    public void run() {
      TProcessor processor = null;
      TTransport inputTransport = null;
      TTransport outputTransport = null;
      TProtocol inputProtocol = null;
      TProtocol outputProtocol = null;
      try {
        processor = new Notifier.Processor(this);
        inputTransport = inputTransportFactory_.getTransport(_client);
        outputTransport = outputTransportFactory_.getTransport(_client);
        inputProtocol = inputProtocolFactory_.getProtocol(inputTransport);
        outputProtocol = outputProtocolFactory_.getProtocol(outputTransport);
        // we check _stopped first to make sure we're not supposed to be
        // shutting
        // down. this is necessary for graceful shutdown.
        if (!_stopped) {
          processor.process(inputProtocol, outputProtocol);
        }
      }
      catch (TTransportException ttx) {
        // Assume the client died and continue silently
      }
      catch (TException tx) {
        LOG.error("Thrift error occurred during processing of message.", tx);
      }
      catch (Exception x) {
        LOG.error("Error occurred during processing of message.", x);
      }

      // if (inputTransport != null) {
      // inputTransport.close();
      // }
      //
      // if (outputTransport != null) {
      // outputTransport.close();
      // }
    }

    public void alert(String token, AlertStruct alert) throws TException {
      // TODO Auto-generated method stub

    }

    public String bind(String secrete) throws AuthenticationException, BindException, SystemException, TException {
      TBinaryProtocol binaryProtocol = new TBinaryProtocol(_client);
      Notifier.Client notifier = new Notifier.Client(binaryProtocol);
      ThriftApplication app = _index.get(secrete);
      if (app == null) {
        throw new BindException("Can not find app for application key=" + secrete);
      }
      app.setNotifier(notifier);
      // set the remote client's IP and port to the app
      ThriftURL url = (ThriftURL) app.getURL();
      url.setHost(_client.getSocket().getInetAddress().getHostAddress());
      url.setPort(_client.getSocket().getPort());
      LOG.info(app + " binded by secrectKey=" + secrete);
      return secrete;
    }

    public void hangup(String token, String id, HangupStruct hangup) throws TException {
    }

    public void heartbeat(String token) throws AuthenticationException, SystemException, TException {
    }

    public void unbind(String token) throws TException {
    }
  }
}
