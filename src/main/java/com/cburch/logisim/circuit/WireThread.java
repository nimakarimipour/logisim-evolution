/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit;

import java.util.concurrent.CopyOnWriteArraySet;

class WireThread {
  private WireThread parent;
  private final CopyOnWriteArraySet<CircuitWires.ThreadBundle> bundles =
      new CopyOnWriteArraySet<>();

  WireThread() {
    parent = this;
  }

  WireThread find() {
    com.cburch.logisim.circuit.WireThread ret = this;
    if (ret.parent != ret) {
      do ret = ret.parent;
      while (ret.parent != ret);
      this.parent = ret;
    }
    return ret;
  }

  CopyOnWriteArraySet<CircuitWires.ThreadBundle> getBundles() {
    return bundles;
  }

  void unite(WireThread other) {
    final com.cburch.logisim.circuit.WireThread group = this.find();
    final com.cburch.logisim.circuit.WireThread group2 = other.find();
    if (group != group2) group.parent = group2;
  }
}
