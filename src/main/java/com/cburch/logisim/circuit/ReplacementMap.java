/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit;

import com.cburch.logisim.comp.Component;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplacementMap {

  static final Logger logger = LoggerFactory.getLogger(ReplacementMap.class);

  private boolean frozen;
  private final HashMap<Component, HashSet<Component>> map;
  private final HashMap<Component, HashSet<Component>> inverse;

  public ReplacementMap() {
    this(new HashMap<>(), new HashMap<>());
  }

  public ReplacementMap(Component oldComp, Component newComp) {
    this(new HashMap<>(), new HashMap<>());
    final java.util.HashSet<com.cburch.logisim.comp.Component> oldSet = new HashSet<Component>(3);
    oldSet.add(oldComp);
    final java.util.HashSet<com.cburch.logisim.comp.Component> newSet = new HashSet<Component>(3);
    newSet.add(newComp);
    map.put(oldComp, newSet);
    inverse.put(newComp, oldSet);
  }

  private ReplacementMap(
      HashMap<Component, HashSet<Component>> map, HashMap<Component, HashSet<Component>> inverse) {
    this.map = map;
    this.inverse = inverse;
  }

  public void add(Component comp) {
    if (frozen) {
      throw new IllegalStateException("cannot change map after frozen");
    }
    inverse.put(comp, new HashSet<>(3));
  }

  void append(ReplacementMap next) {
    for (final java.util.Map.Entry<com.cburch.logisim.comp.Component,java.util.HashSet<com.cburch.logisim.comp.Component>> e : next.map.entrySet()) {
      final com.cburch.logisim.comp.Component b = e.getKey();
      final java.util.HashSet<com.cburch.logisim.comp.Component> cs = e.getValue(); // what b is replaced by
      java.util.HashSet<com.cburch.logisim.comp.Component> as = this.inverse.remove(b); // what was replaced
      // to get b
      if (as == null) { // b pre-existed replacements so
        as = new HashSet<>(3); // we say it replaces itself.
        as.add(b);
      }

      for (final com.cburch.logisim.comp.Component a : as) {
        final java.util.HashSet<com.cburch.logisim.comp.Component> aDst = this.map.computeIfAbsent(a, k -> new HashSet<>(cs.size()));
        aDst.remove(b);
        aDst.addAll(cs);
      }

      for (final com.cburch.logisim.comp.Component c : cs) {
        java.util.HashSet<com.cburch.logisim.comp.Component> cSrc = this.inverse.get(c); // should always
        // be null
        if (cSrc == null) {
          cSrc = new HashSet<>(as.size());
          this.inverse.put(c, cSrc);
        }
        cSrc.addAll(as);
      }
    }

    for (final java.util.Map.Entry<com.cburch.logisim.comp.Component,java.util.HashSet<com.cburch.logisim.comp.Component>> e : next.inverse.entrySet()) {
      final com.cburch.logisim.comp.Component c = e.getKey();
      if (!inverse.containsKey(c)) {
        final java.util.HashSet<com.cburch.logisim.comp.Component> bs = e.getValue();
        if (!bs.isEmpty()) {
          logger.error("Internal error: component replaced but not represented");
        }
        inverse.put(c, new HashSet<>(3));
      }
    }
  }

  void freeze() {
    frozen = true;
  }

  public Collection<? extends Component> getAdditions() {
    return inverse.keySet();
  }

  public Collection<Component> getReplacementsFor(Component a) {
    return map.get(a);
  }

  public Collection<Component> getReplacedBy(Component b) {
    return inverse.get(b);
  }

  ReplacementMap getInverseMap() {
    return new ReplacementMap(inverse, map);
  }

  public Collection<? extends Component> getRemovals() {
    return map.keySet();
  }

  public boolean isEmpty() {
    return map.isEmpty() && inverse.isEmpty();
  }

  public void print(PrintStream out) {
    boolean found = false;
    for (final com.cburch.logisim.comp.Component comp : getRemovals()) {
      if (!found) out.println("  removals:");
      found = true;
      out.println("    " + comp.toString());
      for (final com.cburch.logisim.comp.Component b : map.get(comp)) out.println("     `--> " + b.toString());
    }
    if (!found) out.println("  removals: none");

    found = false;
    for (final com.cburch.logisim.comp.Component b : getAdditions()) {
      if (!found) out.println("  additions:");
      found = true;
      out.println("    " + b.toString());
      for (final com.cburch.logisim.comp.Component a : inverse.get(b)) out.println("     ^-- " + a.toString());
    }
    if (!found) out.println("  additions: none");
  }

  public void put(Component a, Collection<? extends Component> bs) {
    if (frozen) throw new IllegalStateException("cannot change map after frozen");

    final java.util.HashSet<com.cburch.logisim.comp.Component> oldBs = map.computeIfAbsent(a, k -> new HashSet<>(bs.size()));
    oldBs.addAll(bs);

    for (final com.cburch.logisim.comp.Component b : bs) {
      final java.util.HashSet<com.cburch.logisim.comp.Component> oldAs = inverse.computeIfAbsent(b, k -> new HashSet<>(3));
      oldAs.add(a);
    }
  }

  public void remove(Component a) {
    if (frozen) throw new IllegalStateException("cannot change map after frozen");
    map.put(a, new HashSet<>(3));
  }

  public void replace(Component a, Component b) {
    put(a, Collections.singleton(b));
  }

  public void reset() {
    map.clear();
    inverse.clear();
  }

  @Override
  public String toString() {
    final java.io.ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (final java.io.PrintStream p = new PrintStream(out, true, StandardCharsets.UTF_8)) {
      print(p);
    } catch (Exception ignored) {
      // Do nothing.
    }
    return out.toString(StandardCharsets.UTF_8);
  }
}
