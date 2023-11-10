/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CircuitLocker {
  private static class CircuitComparator implements Comparator<Circuit> {
    @Override
    public int compare(Circuit a, Circuit b) {
      int an = a.getLocker().serialNumber;
      int bn = b.getLocker().serialNumber;
      return an - bn;
    }
  }

  static Map<Circuit, Lock> acquireLocks(CircuitTransaction xn, CircuitMutatorImpl mutator) {
    final java.util.Map<com.cburch.logisim.circuit.Circuit,java.lang.Integer> requests = xn.getAccessedCircuits();
    final java.util.HashMap<com.cburch.logisim.circuit.Circuit,java.util.concurrent.locks.Lock> circuitLocks = new HashMap<Circuit, Lock>();
    // Acquire locks in serial-number order to avoid deadlock
    final com.cburch.logisim.circuit.Circuit[] lockOrder = requests.keySet().toArray(new Circuit[0]);
    Arrays.sort(lockOrder, new CircuitComparator());
    try {
      for (final com.cburch.logisim.circuit.Circuit circ : lockOrder) {
        final java.lang.Integer access = requests.get(circ);
        final com.cburch.logisim.circuit.CircuitLocker locker = circ.getLocker();
        if (access == CircuitTransaction.READ_ONLY) {
          final java.util.concurrent.locks.Lock lock = locker.circuitLock.readLock();
          lock.lock();
          circuitLocks.put(circ, lock);
        } else if (access == CircuitTransaction.READ_WRITE) {
          final java.lang.Thread curThread = Thread.currentThread();
          if (locker.mutatingThread == curThread) { // nothing to do - thread already has lock
          } else {
            final java.util.concurrent.locks.Lock lock = locker.circuitLock.writeLock();
            lock.lock();
            circuitLocks.put(circ, lock);
            locker.mutatingThread = Thread.currentThread();
            if (mutator == null) {
              mutator = new CircuitMutatorImpl();
            }
            locker.mutatingMutator = mutator;
          }
        }
      }
    } catch (RuntimeException t) {
      releaseLocks(circuitLocks);
      throw t;
    }
    return circuitLocks;
  }

  static void releaseLocks(Map<Circuit, Lock> locks) {
    final java.lang.Thread curThread = Thread.currentThread();
    for (final java.util.Map.Entry<com.cburch.logisim.circuit.Circuit,java.util.concurrent.locks.Lock> entry : locks.entrySet()) {
      final com.cburch.logisim.circuit.Circuit circ = entry.getKey();
      final java.util.concurrent.locks.Lock lock = entry.getValue();
      final com.cburch.logisim.circuit.CircuitLocker locker = circ.getLocker();
      if (locker.mutatingThread == curThread) {
        locker.mutatingThread = null;
        locker.mutatingMutator = null;
      }
      lock.unlock();
    }
  }

  private static final AtomicInteger nextSerialNumber = new AtomicInteger(0);
  private final int serialNumber;

  private final ReadWriteLock circuitLock;

  private Thread mutatingThread;

  private CircuitMutatorImpl mutatingMutator;

  CircuitLocker() {
    serialNumber = nextSerialNumber.getAndIncrement();
    circuitLock = new ReentrantReadWriteLock();
    mutatingThread = null;
    mutatingMutator = null;
  }

  public int getSerialNumber() {
    return serialNumber;
  }

  void checkForWritePermission(String operationName, Circuit circuit) {
    if (mutatingThread != Thread.currentThread()) {
      throw new LockException(
          operationName + " outside transaction",
          circuit,
          serialNumber,
          mutatingThread,
          mutatingMutator);
    }
  }

  public void execute(CircuitTransaction xn) {
    if (mutatingThread == Thread.currentThread()) {
      xn.run(mutatingMutator);
    } else {
      xn.execute();
    }
  }

  CircuitMutatorImpl getMutator() {
    return mutatingMutator;
  }

  public boolean hasWriteLock() {
    return mutatingThread == Thread.currentThread();
  }

  public static class LockException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    private final Circuit circuit;
    private final int serialNumber;
    private final transient Thread mutatingThread;
    private final CircuitMutatorImpl mutatingMutator;

    public LockException(
        String msg, Circuit circ, int serial, Thread thread, CircuitMutatorImpl mutator) {
      super(msg);
      circuit = circ;
      serialNumber = serial;
      mutatingThread = thread;
      mutatingMutator = mutator;
    }

    public Circuit getCircuit() {
      return circuit;
    }

    public int getSerialNumber() {
      return serialNumber;
    }

    public Thread getMutatingThread() {
      return mutatingThread;
    }

    public CircuitMutatorImpl getCircuitMutator() {
      return mutatingMutator;
    }
  }
}
