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
import com.cburch.logisim.data.Location;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

class WireRepair extends CircuitTransaction {

  private static class MergeSets {
    private final HashMap<Wire, ArrayList<Wire>> map = new HashMap<>();

    Collection<ArrayList<Wire>> getMergeSets() {
      IdentityHashMap<ArrayList<Wire>, Boolean> lists;
      lists = new IdentityHashMap<>();
      for (final java.util.ArrayList<com.cburch.logisim.circuit.Wire> list : map.values()) {
        lists.put(list, Boolean.TRUE);
      }
      return lists.keySet();
    }

    void merge(Wire a, Wire b) {
      java.util.ArrayList<com.cburch.logisim.circuit.Wire> set0 = map.get(a);
      java.util.ArrayList<com.cburch.logisim.circuit.Wire> set1 = map.get(b);
      if (set0 == null && set1 == null) {
        set0 = new ArrayList<>(2);
        set0.add(a);
        set0.add(b);
        map.put(a, set0);
        map.put(b, set0);
      } else if (set0 == null && set1 != null) {
        set1.add(a);
        map.put(a, set1);
      } else if (set0 != null && set1 == null) {
        set0.add(b);
        map.put(b, set0);
      } else if (set0 != set1) { // neither is null, and they are
        // different
        if (set0.size() > set1.size()) { // ensure set1 is the larger
          final java.util.ArrayList<com.cburch.logisim.circuit.Wire> temp = set0;
          set0 = set1;
          set1 = temp;
        }
        set1.addAll(set0);
        for (Wire w : set0) {
          map.put(w, set1);
        }
      }
    }
  }

  private final Circuit circuit;

  public WireRepair(Circuit circuit) {
    this.circuit = circuit;
  }

  private void doMerges(CircuitMutator mutator) {
    final com.cburch.logisim.circuit.WireRepair.MergeSets sets = new MergeSets();
    for (final com.cburch.logisim.data.Location loc : circuit.wires.points.getSplitLocations()) {
      Collection<?> at = circuit.getComponents(loc);
      if (at.size() == 2) {
        Iterator<?> atit = at.iterator();
        Object at0 = atit.next();
        Object at1 = atit.next();
        if (at0 instanceof Wire w0 && at1 instanceof Wire w1) {
          if (w0.isParallel(w1)) {
            sets.merge(w0, w1);
          }
        }
      }
    }

    final com.cburch.logisim.circuit.ReplacementMap repl = new ReplacementMap();
    for (final java.util.ArrayList<com.cburch.logisim.circuit.Wire> mergeSet : sets.getMergeSets()) {
      if (mergeSet.size() > 1) {
        final java.util.ArrayList<com.cburch.logisim.data.Location> locs = new ArrayList<Location>(2 * mergeSet.size());
        for (final com.cburch.logisim.circuit.Wire w : mergeSet) {
          locs.add(w.getEnd0());
          locs.add(w.getEnd1());
        }
        Collections.sort(locs);
        final com.cburch.logisim.data.Location e0 = locs.get(0);
        final com.cburch.logisim.data.Location e1 = locs.get(locs.size() - 1);
        final com.cburch.logisim.circuit.Wire wnew = Wire.create(e0, e1);
        Collection<Wire> wset = Collections.singleton(wnew);

        for (final com.cburch.logisim.circuit.Wire w : mergeSet) {
          if (!w.equals(wnew)) {
            repl.put(w, wset);
          }
        }
      }
    }
    mutator.replace(circuit, repl);
  }

  private void doMergeSet(ArrayList<Wire> mergeSet, ReplacementMap replacements, Set<Location> splitLocs) {
    final java.util.TreeSet<com.cburch.logisim.data.Location> ends = new TreeSet<Location>();
    for (final com.cburch.logisim.circuit.Wire w : mergeSet) {
      ends.add(w.getEnd0());
      ends.add(w.getEnd1());
    }
    final com.cburch.logisim.circuit.Wire whole = Wire.create(ends.first(), ends.last());

    final java.util.TreeSet<com.cburch.logisim.data.Location> mids = new TreeSet<Location>();
    mids.add(whole.getEnd0());
    mids.add(whole.getEnd1());
    for (final com.cburch.logisim.data.Location loc : whole) {
      if (splitLocs.contains(loc)) {
        for (final com.cburch.logisim.comp.Component comp : circuit.getComponents(loc)) {
          if (!mergeSet.contains(comp)) {
            mids.add(loc);
            break;
          }
        }
      }
    }

    ArrayList<Wire> mergeResult = new ArrayList<>();
    if (mids.size() == 2) {
      mergeResult.add(whole);
    } else {
      Location e0 = null;
      for (final com.cburch.logisim.data.Location e1 : mids) {
        if (e0 != null)
          mergeResult.add(Wire.create(e0, e1));
        e0 = e1;
      }
    }

    for (Wire w : mergeSet) {
      final java.util.ArrayList<com.cburch.logisim.comp.Component> wRepl = new ArrayList<Component>(2);
      for (final com.cburch.logisim.circuit.Wire w2 : mergeResult) {
        if (w2.overlaps(w, false)) {
          wRepl.add(w2);
        }
      }
      replacements.put(w, wRepl);
    }
  }

  /*
   * for debugging: private void printWires(String prefix, PrintStream out) {
   * boolean first = true; for (Wire w : circuit.getWires()) { if (first) {
   * out.println(prefix + ": " + w); first = false; } else {
   * out.println("      " + w); } } out.println(prefix + ": none"); }
   */

  private void doOverlaps(CircuitMutator mutator) {
    final java.util.HashMap<com.cburch.logisim.data.Location,java.util.ArrayList<com.cburch.logisim.circuit.Wire>> wirePoints = new HashMap<Location, ArrayList<Wire>>();
    for (final com.cburch.logisim.circuit.Wire w : circuit.getWires()) {
      for (final com.cburch.logisim.data.Location loc : w) {
        final java.util.ArrayList<com.cburch.logisim.circuit.Wire> locWires = wirePoints.computeIfAbsent(loc, k -> new ArrayList<>(3));
        locWires.add(w);
      }
    }

    final com.cburch.logisim.circuit.WireRepair.MergeSets mergeSets = new MergeSets();
    for (final java.util.ArrayList<com.cburch.logisim.circuit.Wire> locWires : wirePoints.values()) {
      if (locWires.size() > 1) {
        for (int i = 0, n = locWires.size(); i < n; i++) {
          final com.cburch.logisim.circuit.Wire w0 = locWires.get(i);
          for (int j = i + 1; j < n; j++) {
            final com.cburch.logisim.circuit.Wire w1 = locWires.get(j);
            if (w0.overlaps(w1, false))
              mergeSets.merge(w0, w1);
          }
        }
      }
    }

    final com.cburch.logisim.circuit.ReplacementMap replacements = new ReplacementMap();
    final java.util.Set<com.cburch.logisim.data.Location> splitLocs = circuit.wires.points.getSplitLocations();
    for (final java.util.ArrayList<com.cburch.logisim.circuit.Wire> mergeSet : mergeSets.getMergeSets()) {
      if (mergeSet.size() > 1) {
        doMergeSet(mergeSet, replacements, splitLocs);
      }
    }
    mutator.replace(circuit, replacements);
  }

  private void doSplits(CircuitMutator mutator) {
    final java.util.Set<com.cburch.logisim.data.Location> splitLocs = circuit.wires.points.getSplitLocations();
    final com.cburch.logisim.circuit.ReplacementMap repl = new ReplacementMap();
    for (final com.cburch.logisim.circuit.Wire w : circuit.getWires()) {
      final com.cburch.logisim.data.Location w0 = w.getEnd0();
      final com.cburch.logisim.data.Location w1 = w.getEnd1();
      ArrayList<Location> splits = null;
      for (final com.cburch.logisim.data.Location loc : splitLocs) {
        if (w.contains(loc) && !loc.equals(w0) && !loc.equals(w1)) {
          if (splits == null) splits = new ArrayList<>();
          splits.add(loc);
        }
      }
      if (splits != null) {
        splits.add(w1);
        Collections.sort(splits);
        com.cburch.logisim.data.Location e0 = w0;
        final java.util.ArrayList<com.cburch.logisim.circuit.Wire> subs = new ArrayList<Wire>(splits.size());
        for (final com.cburch.logisim.data.Location e1 : splits) {
          subs.add(Wire.create(e0, e1));
          e0 = e1;
        }
        repl.put(w, subs);
      }
    }
    mutator.replace(circuit, repl);
  }

  @Override
  protected Map<Circuit, Integer> getAccessedCircuits() {
    return Collections.singletonMap(circuit, READ_WRITE);
  }

  @Override
  protected void run(CircuitMutator mutator) {
    doMerges(mutator);
    doOverlaps(mutator);
    doSplits(mutator);
  }
}
