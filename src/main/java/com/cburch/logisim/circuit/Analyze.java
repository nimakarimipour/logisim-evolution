/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit;

import static com.cburch.logisim.circuit.Strings.S;

import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.Entry;
import com.cburch.logisim.analyze.model.Expression;
import com.cburch.logisim.analyze.model.Expressions;
import com.cburch.logisim.analyze.model.TruthTable;
import com.cburch.logisim.analyze.model.Var;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class Analyze {
  public static class LocationBit {
    final Location loc;
    final int bit;

    public LocationBit(Location l, int b) {
      loc = l;
      bit = b;
    }

    @Override
    public boolean equals(Object other) {
      return (other instanceof LocationBit that)
             ? (that.loc.equals(this.loc) && that.bit == this.bit)
             : false;
    }

    @Override
    public int hashCode() {
      return loc.hashCode() + bit;
    }
  }

  private static class ExpressionMap extends HashMap<LocationBit, Expression> implements ExpressionComputer.Map {
    private static final long serialVersionUID = 1L;
    private final Circuit circuit;
    private final Set<LocationBit> dirtyPoints = new HashSet<>();
    private final Map<LocationBit, Component> causes = new HashMap<>();
    private Component currentCause = null;

    ExpressionMap(Circuit circuit) {
      this.circuit = circuit;
    }

    @Override
    public Expression put(LocationBit point, Expression expression) {
      final com.cburch.logisim.analyze.model.Expression ret = super.put(point, expression);
      if (currentCause != null) causes.put(point, currentCause);
      if (!Objects.equals(ret, expression)) {
        dirtyPoints.add(point);
      }
      return ret;
    }

    @Override
    public Expression put(Location point, int bit, Expression expression) {
      return put(new LocationBit(point, bit), expression);
    }

    @Override
    public Expression get(Location point, int bit) {
      return get(new LocationBit(point, bit));
    }
  }

  /**
   * Checks whether any of the recently placed expressions in the expression map are
   * self-referential; if so, return it.
   */
  private static Expression checkForCircularExpressions(ExpressionMap expressionMap) {
    for (final com.cburch.logisim.circuit.Analyze.LocationBit point : expressionMap.dirtyPoints) {
      final com.cburch.logisim.analyze.model.Expression expr = expressionMap.get(point);
      if (expr.isCircular()) return expr;
    }
    return null;
  }

  //
  // computeExpression
  //
  /**
   * Computes the expression corresponding to the given circuit, or raises ComputeException if
   * difficulties arise.
   */
  public static void computeExpression(AnalyzerModel model, Circuit circuit, Map<Instance, String> pinNames) throws AnalyzeException {
    final com.cburch.logisim.circuit.Analyze.ExpressionMap expressionMap = new ExpressionMap(circuit);

    final java.util.ArrayList<com.cburch.logisim.analyze.model.Var> inputVars = new ArrayList<Var>();
    final java.util.ArrayList<com.cburch.logisim.analyze.model.Var> outputVars = new ArrayList<Var>();
    final java.util.ArrayList<com.cburch.logisim.instance.Instance> outputPins = new ArrayList<Instance>();
    for (final java.util.Map.Entry<com.cburch.logisim.instance.Instance,java.lang.String> entry : pinNames.entrySet()) {
      final com.cburch.logisim.instance.Instance pin = entry.getKey();
      final java.lang.String label = entry.getValue();
      final int width = pin.getAttributeValue(StdAttr.WIDTH).getWidth();
      if (Pin.FACTORY.isInputPin(pin)) {
        expressionMap.currentCause = Instance.getComponentFor(pin);
        for (int b = 0; b < width; b++) {
          final com.cburch.logisim.analyze.model.Expression e = Expressions.variable(width > 1 ? label + "[" + b + "]" : label);
          expressionMap.put(new LocationBit(pin.getLocation(), b), e);
        }
        inputVars.add(new Var(label, width));
      } else {
        outputPins.add(pin);
        outputVars.add(new Var(label, width));
      }
    }

    propagateComponents(expressionMap, circuit.getNonWires());

    final int maxIterations = 100;
    for (int iterations = 0; !expressionMap.dirtyPoints.isEmpty(); iterations++) {
      if (iterations > maxIterations) {
        throw new AnalyzeException.Circular();
      }

      propagateWires(expressionMap, new HashSet<>(expressionMap.dirtyPoints));

      final java.util.HashSet<com.cburch.logisim.comp.Component> dirtyComponents = getDirtyComponents(circuit, expressionMap.dirtyPoints);
      expressionMap.dirtyPoints.clear();
      propagateComponents(expressionMap, dirtyComponents);

      final com.cburch.logisim.analyze.model.Expression expr = checkForCircularExpressions(expressionMap);
      if (expr != null) throw new AnalyzeException.Circular();
    }

    model.setVariables(inputVars, outputVars);
    for (final com.cburch.logisim.instance.Instance pin : outputPins) {
      final java.lang.String label = pinNames.get(pin);
      final int width = pin.getAttributeValue(StdAttr.WIDTH).getWidth();
      for (int b = 0; b < width; b++) {
        final com.cburch.logisim.circuit.Analyze.LocationBit loc = new LocationBit(pin.getLocation(), b);
        final java.lang.String name = (width > 1 ? label + "[" + b + "]" : label);
        model.getOutputExpressions().setExpression(name, expressionMap.get(loc));
      }
    }
  }

  //
  // ComputeTable
  //
  /** Returns a truth table corresponding to the circuit. */
  public static void computeTable(AnalyzerModel model, Project proj, Circuit circuit, Map<Instance, String> pinLabels) {
    final java.util.ArrayList<com.cburch.logisim.instance.Instance> inputPins = new ArrayList<Instance>();
    final java.util.ArrayList<com.cburch.logisim.analyze.model.Var> inputVars = new ArrayList<Var>();
    final java.util.ArrayList<java.lang.String> inputNames = new ArrayList<String>();
    final java.util.ArrayList<com.cburch.logisim.instance.Instance> outputPins = new ArrayList<Instance>();
    final java.util.ArrayList<com.cburch.logisim.analyze.model.Var> outputVars = new ArrayList<Var>();
    final java.util.ArrayList<java.lang.String> outputNames = new ArrayList<String>();
    for (final java.util.Map.Entry<com.cburch.logisim.instance.Instance,java.lang.String> entry : pinLabels.entrySet()) {
      final com.cburch.logisim.instance.Instance pin = entry.getKey();
      final int width = pin.getAttributeValue(StdAttr.WIDTH).getWidth();
      final com.cburch.logisim.analyze.model.Var variable = new Var(entry.getValue(), width);
      if (Pin.FACTORY.isInputPin(pin)) {
        inputPins.add(pin);
        for (final java.lang.String name : variable) inputNames.add(name);
        inputVars.add(variable);
      } else {
        outputPins.add(pin);
        for (final java.lang.String name : variable) outputNames.add(name);
        outputVars.add(variable);
      }
    }

    final int inputCount = inputNames.size();
    final int rowCount = 1 << inputCount;
    final com.cburch.logisim.analyze.model.Entry[][] columns = new Entry[outputNames.size()][rowCount];

    for (int i = 0; i < rowCount; i++) {
      final com.cburch.logisim.circuit.CircuitState circuitState = new CircuitState(proj, circuit);
      int incol = 0;
      for (final com.cburch.logisim.instance.Instance pin : inputPins) {
        final int width = pin.getAttributeValue(StdAttr.WIDTH).getWidth();
        final com.cburch.logisim.data.Value[] v = new Value[width];
        for (int b = width - 1; b >= 0; b--) {
          boolean value = TruthTable.isInputSet(i, incol++, inputCount);
          v[b] = value ? Value.TRUE : Value.FALSE;
        }
        final com.cburch.logisim.instance.InstanceState pinState = circuitState.getInstanceState(pin);
        Pin.FACTORY.setValue(pinState, Value.create(v));
      }

      final com.cburch.logisim.circuit.Propagator prop = circuitState.getPropagator();
      prop.propagate();
      /*
       * TODO for the SimulatorPrototype class do { prop.step(); } while
       * (prop.isPending());
       */
      // TODO: Search for circuit state

      if (prop.isOscillating()) {
        for (int j = 0; j < columns.length; j++) {
          columns[j][i] = Entry.OSCILLATE_ERROR;
        }
      } else {
        int outcol = 0;
        for (final com.cburch.logisim.instance.Instance pin : outputPins) {
          int width = pin.getAttributeValue(StdAttr.WIDTH).getWidth();
          final com.cburch.logisim.instance.InstanceState pinState = circuitState.getInstanceState(pin);
          Entry out;
          for (int b = width - 1; b >= 0; b--) {
            final com.cburch.logisim.data.Value outValue = Pin.FACTORY.getValue(pinState).get(b);
            if (outValue == Value.TRUE)
              out = Entry.ONE;
            else if (outValue == Value.FALSE)
              out = Entry.ZERO;
            else if (outValue == Value.ERROR)
              out = Entry.BUS_ERROR;
            else
              out = Entry.DONT_CARE;
            columns[outcol++][i] = out;
          }
        }
      }
    }

    model.setVariables(inputVars, outputVars);
    for (int i = 0; i < columns.length; i++) {
      model.getTruthTable().setOutputColumn(i, columns[i]);
    }
  }

  // computes outputs of affected components
  private static HashSet<Component> getDirtyComponents(Circuit circuit, Set<LocationBit> pointsToProcess) {
    final java.util.HashSet<com.cburch.logisim.comp.Component> dirtyComponents = new HashSet<Component>();
    for (final com.cburch.logisim.circuit.Analyze.LocationBit point : pointsToProcess) {
      dirtyComponents.addAll(circuit.getNonWires(point.loc));
    }
    return dirtyComponents;
  }

  //
  // getPinLabels
  //
  /**
   * Returns a sorted map from Pin objects to String objects, listed in canonical order (top-down
   * order, with ties broken left-right).
   */
  public static SortedMap<Instance, String> getPinLabels(Circuit circuit) {
    final java.util.TreeMap<com.cburch.logisim.instance.Instance,java.lang.String> ret = new TreeMap<Instance, String>(Location.CompareVertical);

    // Put the pins into the TreeMap, with null labels
    for (final com.cburch.logisim.instance.Instance pin : circuit.getAppearance().getPortOffsets(Direction.EAST).values()) {
      ret.put(pin, null);
    }

    // Process first the pins that the user has given labels.
    final java.util.ArrayList<com.cburch.logisim.instance.Instance> pinList = new ArrayList<>(ret.keySet());
    final java.util.HashSet<java.lang.String> labelsTaken = new HashSet<String>();
    for (final com.cburch.logisim.instance.Instance pin : pinList) {
      java.lang.String label = pin.getAttributeSet().getValue(StdAttr.LABEL);
      label = toValidLabel(label);
      if (label != null) {
        if (labelsTaken.contains(label)) {
          int i = 2;
          while (labelsTaken.contains(label + i)) i++;
          label = label + i;
        }
        ret.put(pin, label);
        labelsTaken.add(label);
      }
    }

    // Now process the unlabeled pins.
    for (final com.cburch.logisim.instance.Instance pin : pinList) {
      if (ret.get(pin) != null) continue;

      String defaultList;
      if (Pin.FACTORY.isInputPin(pin)) {
        defaultList = S.get("defaultInputLabels");
        if (!defaultList.contains(",")) {
          defaultList = "a,b,c,d,e,f,g,h";
        }
      } else {
        defaultList = S.get("defaultOutputLabels");
        if (!defaultList.contains(",")) {
          defaultList = "x,y,z,u,v,w,s,t";
        }
      }

      final java.lang.String[] options = defaultList.split(",");
      String label = null;
      for (int i = 0; label == null && i < options.length; i++) {
        if (!labelsTaken.contains(options[i])) {
          label = options[i];
        }
      }
      if (label == null) {
        // This is an extreme measure that should never happen
        // if the default labels are defined properly and the
        // circuit doesn't exceed the maximum number of pins.
        int i = 1;
        do {
          i++;
          label = "x" + i;
        } while (labelsTaken.contains(label));
      }

      labelsTaken.add(label);
      ret.put(pin, label);
    }

    return ret;
  }

  private static void propagateComponents(ExpressionMap expressionMap, Collection<Component> components) throws AnalyzeException {
    for (final com.cburch.logisim.comp.Component comp : components) {
      final com.cburch.logisim.circuit.ExpressionComputer computer = (ExpressionComputer) comp.getFeature(ExpressionComputer.class);
      if (computer != null) {
        try {
          expressionMap.currentCause = comp;
          computer.computeExpression(expressionMap);
        } catch (UnsupportedOperationException e) {
          throw new AnalyzeException.CannotHandle(comp.getFactory().getDisplayName());
        }
      } else if (comp.getFactory() instanceof Pin) { // pins are handled elsewhere
      } else if (comp.getFactory() instanceof SplitterFactory) { // splitters are handled elsewhere
      } else {
        throw new AnalyzeException.CannotHandle(comp.getFactory().getDisplayName());
      }
    }
  }

  // propagates expressions down wires
  private static void propagateWires(ExpressionMap expressionMap, HashSet<LocationBit> pointsToProcess) throws AnalyzeException {
    expressionMap.currentCause = null;
    for (final com.cburch.logisim.circuit.Analyze.LocationBit locationBit : pointsToProcess) {
      final com.cburch.logisim.analyze.model.Expression e = expressionMap.get(locationBit);
      expressionMap.currentCause = expressionMap.causes.get(locationBit);
      final com.cburch.logisim.circuit.WireBundle bundle = expressionMap.circuit.wires.getWireBundle(locationBit.loc);
      if (e != null && bundle != null && bundle.isValid() && bundle.threads != null) {
        if (bundle.threads.length <= locationBit.bit) {
          throw new AnalyzeException.CannotHandle("incompatible widths");
        }
        final com.cburch.logisim.circuit.WireThread t = bundle.threads[locationBit.bit];
        for (final com.cburch.logisim.circuit.CircuitWires.ThreadBundle tb : t.getBundles()) {
          for (final com.cburch.logisim.data.Location p2 : tb.b.points) {
            if (p2.equals(locationBit.loc)) continue;
            final com.cburch.logisim.circuit.Analyze.LocationBit p2b = new LocationBit(p2, tb.loc);
            final com.cburch.logisim.analyze.model.Expression old = expressionMap.get(p2b);
            if (old != null) {
              final com.cburch.logisim.comp.Component eCause = expressionMap.currentCause;
              final com.cburch.logisim.comp.Component oldCause = expressionMap.causes.get(p2b);
              if (eCause != oldCause && !old.equals(e)) {
                throw new AnalyzeException.Conflict();
              }
            }
            expressionMap.put(p2b, e);
          }
        }
      }
    }
  }

  private static String toValidLabel(String label) {
    if (label == null) return null;
    StringBuilder end = null;
    final java.lang.StringBuilder ret = new StringBuilder();
    boolean afterWhitespace = false;
    for (int i = 0; i < label.length(); i++) {
      char c = label.charAt(i);
      if (Character.isJavaIdentifierStart(c)) {
        if (afterWhitespace) {
          // capitalize words after the first one
          c = Character.toTitleCase(c);
          afterWhitespace = false;
        }
        ret.append(c);
      } else if (Character.isJavaIdentifierPart(c)) {
        // If we can't place it at the start, we'll dump it
        // onto the end.
        if (ret.length() > 0) {
          ret.append(c);
        } else {
          if (end == null) end = new StringBuilder();
          end.append(c);
        }
        afterWhitespace = false;
      } else if (Character.isWhitespace(c)) {
        afterWhitespace = true;
      } else { // just ignore any other characters
      }
    }
    if (end != null && ret.length() > 0) ret.append(end);
    if (ret.length() == 0) return null;
    return ret.toString();
  }

  private Analyze() {
    // dummy
  }
}
