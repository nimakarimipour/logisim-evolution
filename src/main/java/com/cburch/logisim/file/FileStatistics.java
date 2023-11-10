/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FileStatistics {
  public static final class Count {
    private Library library;
    private final ComponentFactory factory;
    private int simpleCount;
    private int uniqueCount;
    private int recursiveCount;

    private Count(ComponentFactory factory) {
      this.library = null;
      this.factory = factory;
      this.simpleCount = 0;
      this.uniqueCount = 0;
      this.recursiveCount = 0;
    }

    public ComponentFactory getFactory() {
      return factory;
    }

    public Library getLibrary() {
      return library;
    }

    public int getRecursiveCount() {
      return recursiveCount;
    }

    public int getSimpleCount() {
      return simpleCount;
    }

    public int getUniqueCount() {
      return uniqueCount;
    }
  }

  private final List<Count> counts;
  private final Count totalWithout;
  private final Count totalWith;

  private FileStatistics(List<Count> counts, Count totalWithout, Count totalWith) {
    this.counts = Collections.unmodifiableList(counts);
    this.totalWithout = totalWithout;
    this.totalWith = totalWith;
  }

  public static FileStatistics compute(LogisimFile file, Circuit circuit) {
    final java.util.HashSet<com.cburch.logisim.circuit.Circuit> include = new HashSet<>(file.getCircuits());
    final java.util.HashMap<com.cburch.logisim.circuit.Circuit,java.util.Map<com.cburch.logisim.comp.ComponentFactory,com.cburch.logisim.file.FileStatistics.Count>> countMap = new HashMap<Circuit, Map<ComponentFactory, Count>>();
    doRecursiveCount(circuit, include, countMap);
    doUniqueCounts(countMap.get(circuit), countMap);
    final java.util.List<com.cburch.logisim.file.FileStatistics.Count> countList = sortCounts(countMap.get(circuit), file);
    return new FileStatistics(countList, getTotal(countList, include), getTotal(countList, null));
  }

  private static Map<ComponentFactory, Count> doRecursiveCount(Circuit circuit, Set<Circuit> include, Map<Circuit, Map<ComponentFactory, Count>> countMap) {
    if (countMap.containsKey(circuit)) {
      return countMap.get(circuit);
    }

    final java.util.Map<com.cburch.logisim.comp.ComponentFactory,com.cburch.logisim.file.FileStatistics.Count> counts = doSimpleCount(circuit);
    countMap.put(circuit, counts);
    for (final com.cburch.logisim.file.FileStatistics.Count count : counts.values()) {
      count.uniqueCount = count.simpleCount;
      count.recursiveCount = count.simpleCount;
    }
    for (final com.cburch.logisim.circuit.Circuit sub : include) {
      final com.cburch.logisim.circuit.SubcircuitFactory subFactory = sub.getSubcircuitFactory();
      if (counts.containsKey(subFactory)) {
        final int multiplier = counts.get(subFactory).simpleCount;
        final java.util.Map<com.cburch.logisim.comp.ComponentFactory,com.cburch.logisim.file.FileStatistics.Count> subCountRecursive = doRecursiveCount(sub, include, countMap);
        for (final com.cburch.logisim.file.FileStatistics.Count subCount : subCountRecursive.values()) {
          final com.cburch.logisim.comp.ComponentFactory subfactory = subCount.factory;
          com.cburch.logisim.file.FileStatistics.Count superCount = counts.get(subfactory);
          if (superCount == null) {
            superCount = new Count(subfactory);
            counts.put(subfactory, superCount);
          }
          superCount.recursiveCount += multiplier * subCount.recursiveCount;
        }
      }
    }

    return counts;
  }

  private static Map<ComponentFactory, Count> doSimpleCount(Circuit circuit) {
    final java.util.HashMap<com.cburch.logisim.comp.ComponentFactory,com.cburch.logisim.file.FileStatistics.Count> counts = new HashMap<ComponentFactory, Count>();
    for (final com.cburch.logisim.comp.Component comp : circuit.getNonWires()) {
      final com.cburch.logisim.comp.ComponentFactory factory = comp.getFactory();
      com.cburch.logisim.file.FileStatistics.Count count = counts.get(factory);
      if (count == null) {
        count = new Count(factory);
        counts.put(factory, count);
      }
      count.simpleCount++;
    }
    return counts;
  }

  private static void doUniqueCounts(
      Map<ComponentFactory, Count> counts,
      Map<Circuit, Map<ComponentFactory, Count>> circuitCounts) {
    for (final com.cburch.logisim.file.FileStatistics.Count count : counts.values()) {
      final com.cburch.logisim.comp.ComponentFactory factory = count.getFactory();
      int unique = 0;
      for (final com.cburch.logisim.circuit.Circuit circ : circuitCounts.keySet()) {
        final com.cburch.logisim.file.FileStatistics.Count subcount = circuitCounts.get(circ).get(factory);
        if (subcount != null) {
          unique += subcount.simpleCount;
        }
      }
      count.uniqueCount = unique;
    }
  }

  private static Count getTotal(List<Count> counts, Set<Circuit> exclude) {
    final com.cburch.logisim.file.FileStatistics.Count ret = new Count(null);
    for (final com.cburch.logisim.file.FileStatistics.Count count : counts) {
      final com.cburch.logisim.comp.ComponentFactory factory = count.getFactory();
      Circuit factoryCirc = null;
      if (factory instanceof SubcircuitFactory sub) {
        factoryCirc = sub.getSubcircuit();
      }
      if (exclude == null || !exclude.contains(factoryCirc)) {
        ret.simpleCount += count.simpleCount;
        ret.uniqueCount += count.uniqueCount;
        ret.recursiveCount += count.recursiveCount;
      }
    }
    return ret;
  }

  private static List<Count> sortCounts(Map<ComponentFactory, Count> counts, LogisimFile file) {
    final java.util.ArrayList<com.cburch.logisim.file.FileStatistics.Count> ret = new ArrayList<Count>();
    for (final com.cburch.logisim.tools.AddTool tool : file.getTools()) {
      final com.cburch.logisim.comp.ComponentFactory factory = tool.getFactory();
      final com.cburch.logisim.file.FileStatistics.Count count = counts.get(factory);
      if (count != null) {
        count.library = file;
        ret.add(count);
      }
    }
    for (final com.cburch.logisim.tools.Library lib : file.getLibraries()) {
      for (final com.cburch.logisim.tools.Tool tool : lib.getTools()) {
        if (tool instanceof AddTool addTool) {
          final com.cburch.logisim.comp.ComponentFactory factory = addTool.getFactory();
          final com.cburch.logisim.file.FileStatistics.Count count = counts.get(factory);
          if (count != null) {
            count.library = lib;
            ret.add(count);
          }
        }
      }
    }
    return ret;
  }

  public List<Count> getCounts() {
    return counts;
  }

  public Count getTotalWithoutSubcircuits() {
    return totalWithout;
  }

  public Count getTotalWithSubcircuits() {
    return totalWith;
  }
}
