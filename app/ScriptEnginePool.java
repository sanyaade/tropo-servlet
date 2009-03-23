package com.voxeo.tropo.app;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.apache.log4j.Logger;

public class ScriptEnginePool {
  private static final Logger LOG = Logger.getLogger(ScriptEnginePool.class);

  protected ScriptEngineManager _engMgr;

  protected Map<String, Integer> _sizes;

  Map<String, Queue<ScriptEngine>> _pool = new HashMap<String, Queue<ScriptEngine>>();

  public ScriptEnginePool(ScriptEngineManager engMgr, Map<String, Integer> initSize) {
    _engMgr = engMgr;
    _sizes = initSize;
    for (String type : _sizes.keySet()) {
      int size = _sizes.get(type);
      LOG.info("Initializing " + size + " script engine for " + type + " ...");
      ArrayBlockingQueue<ScriptEngine> queue = new ArrayBlockingQueue<ScriptEngine>(size);
      _pool.put(type, queue);
      for (int i = 0; i < size; i++) {
        ScriptEngine eng = _engMgr.getEngineByName(type);
        if (eng == null) {
          LOG.error("Unsupported script type :" + type);
        }
        else {
          queue.offer(eng);
        }
      }
    }
    LOG.info("Done with the initialization of script engine pool.");
  }

  /**
   * null if still empty after waitMiliseconds
   * 
   * @param type
   * @return
   */
  public ScriptEngine get(String type) {
    return get(type, 10000);
  }

  public void put(String type, ScriptEngine eng) {
    Queue<ScriptEngine> queue = _pool.get(type);
    queue.offer(eng);
  }

  /**
   * null if still empty after waitMiliseconds
   * 
   * @param type
   * @return
   */
  public ScriptEngine get(String type, int waitMiliseconds) {
    ArrayBlockingQueue<ScriptEngine> queue = (ArrayBlockingQueue<ScriptEngine>) _pool.get(type);
    try {
      return queue.poll(waitMiliseconds, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException e1) {
      return queue.poll();
    }
  }

  public Map<String, Queue<ScriptEngine>> getPoolMap() {
    return _pool;
  }

}
