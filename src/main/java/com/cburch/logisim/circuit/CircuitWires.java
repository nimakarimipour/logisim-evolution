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
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.PullResistor;
import com.cburch.logisim.std.wiring.Tunnel;
import com.cburch.logisim.util.CollectionUtil;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.IteratorUtil;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CircuitWires {

  static class BundleMap {
    final HashMap<Location, WireBundle> pointBundles = new HashMap<>();
    final HashSet<WireBundle> bundles = new HashSet<>();
    boolean isValid = true;
    // NOTE: It would make things more efficient if we also had
    // a set of just the first bundle in each tree.
    HashSet<WidthIncompatibilityData> incompatibilityData = null;

    void addWidthIncompatibilityData(WidthIncompatibilityData e) {
      if (incompatibilityData == null) {
        incompatibilityData = new HashSet<>();
      }
      incompatibilityData.add(e);
    }

    WireBundle createBundleAt(Location p) {
      com.cburch.logisim.circuit.WireBundle ret = pointBundles.get(p);
      if (ret == null) {
        ret = new WireBundle();
        pointBundles.put(p, ret);
        ret.points.add(p);
        bundles.add(ret);
      }
      return ret;
    }

    WireBundle getBundleAt(Location p) {
      return pointBundles.get(p);
    }

    Set<Location> getBundlePoints() {
      return pointBundles.keySet();
    }

    Set<WireBundle> getBundles() {
      return bundles;
    }

    HashSet<WidthIncompatibilityData> getWidthIncompatibilityData() {
      return incompatibilityData;
    }

    void invalidate() {
      isValid = false;
    }

    boolean isValid() {
      return isValid;
    }

    void setBundleAt(Location p, WireBundle b) {
      pointBundles.put(p, b);
    }
  }

  static class SplitterData {
    final WireBundle[] endBundle; // PointData associated with each end

    SplitterData(int fanOut) {
      endBundle = new WireBundle[fanOut + 1];
    }
  }

  static class State {
    final BundleMap bundleMap;
    final HashMap<WireThread, Value> thrValues = new HashMap<>();

    State(BundleMap bundleMap) {
      this.bundleMap = bundleMap;
    }

    @Override
    public Object clone() {
      final com.cburch.logisim.circuit.CircuitWires.State ret = new State(this.bundleMap);
      ret.thrValues.putAll(this.thrValues);
      return ret;
    }
  }

  static class ThreadBundle {
    final int loc;
    final WireBundle b;

    ThreadBundle(int loc, WireBundle b) {
      this.loc = loc;
      this.b = b;
    }
  }

  private class TunnelListener implements AttributeListener {
    @Override
    public void attributeListChanged(AttributeEvent e) {
      // do nothing.
    }

    @Override
    public void attributeValueChanged(AttributeEvent e) {
      final com.cburch.logisim.data.Attribute<?> attr = e.getAttribute();
      if (attr == StdAttr.LABEL || attr == PullResistor.ATTR_PULL_TYPE) {
        voidBundleMap();
      }
    }
  }

  private static Value pullValue(Value base, Value pullTo) {
    if (base.isFullyDefined()) {
      return base;
    } else if (base.getWidth() == 1) {
      if (base == Value.UNKNOWN) return pullTo;
      else return base;
    } else {
      final com.cburch.logisim.data.Value[] ret = base.getAll();
      for (int i = 0; i < ret.length; i++) {
        if (ret[i] == Value.UNKNOWN) ret[i] = pullTo;
      }
      return Value.create(ret);
    }
  }

  static final Logger logger = LoggerFactory.getLogger(CircuitWires.class);

  // user-given data
  private final HashSet<Wire> wires = new HashSet<>();
  private final HashSet<Splitter> splitters = new HashSet<>();
  private final HashSet<Component> tunnels = new HashSet<>(); // of
  // Components
  // with
  // Tunnel
  // factory
  private final TunnelListener tunnelListener = new TunnelListener();
  private final HashSet<Component> pulls = new HashSet<>(); // of
  // Components
  // with
  // PullResistor
  // factory

  final CircuitPoints points = new CircuitPoints();
  // derived data
  private Bounds bounds = Bounds.EMPTY_BOUNDS;

  private BundleMap masterBundleMap = null;

  CircuitWires() {}

  //
  // action methods
  //
  // NOTE: this could be made much more efficient in most cases to
  // avoid voiding the bundle map.
  /*synchronized*/ boolean add(Component comp) {
    boolean added = true;
    if (comp instanceof Wire wire) {
      added = addWire(wire);
    } else if (comp instanceof Splitter splitter) {
      splitters.add(splitter);
    } else {
      final com.cburch.logisim.comp.ComponentFactory factory = comp.getFactory();
      if (factory instanceof Tunnel) {
        tunnels.add(comp);
        comp.getAttributeSet().addAttributeListener(tunnelListener);
      } else if (factory instanceof PullResistor) {
        pulls.add(comp);
        comp.getAttributeSet().addAttributeListener(tunnelListener);
      }
    }
    if (added) {
      points.add(comp);
      voidBundleMap();
    }
    return added;
  }

  /*synchronized*/ void add(Component comp, EndData end) {
    points.add(comp, end);
    voidBundleMap();
  }

  private boolean addWire(Wire w) {
    final boolean added = wires.add(w);
    if (!added) return false;

    if (bounds != Bounds.EMPTY_BOUNDS) { // update bounds
      bounds = bounds.add(w.e0).add(w.e1);
    }
    return true;
  }

  // To be called by getBundleMap only
  private void computeBundleMap(BundleMap ret) {
    // create bundles corresponding to wires and tunnels
    connectWires(ret);
    connectTunnels(ret);
    connectPullResistors(ret);

    // merge any WireBundle objects united by previous steps
    for (final java.util.Iterator<com.cburch.logisim.circuit.WireBundle> it = ret.getBundles().iterator(); it.hasNext(); ) {
      final com.cburch.logisim.circuit.WireBundle b = it.next();
      final com.cburch.logisim.circuit.WireBundle bpar = b.find();
      if (bpar != b) { // b isn't group's representative
        for (final com.cburch.logisim.data.Location pt : b.points) {
          ret.setBundleAt(pt, bpar);
          bpar.points.add(pt);
        }
        bpar.addPullValue(b.getPullValue());
        it.remove();
      }
    }

    // make a WireBundle object for each end of a splitter
    for (final com.cburch.logisim.circuit.Splitter spl : splitters) {
      final java.util.ArrayList<com.cburch.logisim.comp.EndData> ends = new ArrayList<>(spl.getEnds());
      for (final com.cburch.logisim.comp.EndData end : ends) {
        final com.cburch.logisim.data.Location p = end.getLocation();
        final com.cburch.logisim.circuit.WireBundle pb = ret.createBundleAt(p);
        pb.setWidth(end.getWidth(), p);
      }
    }

    // set the width for each bundle whose size is known
    // based on components
    for (final com.cburch.logisim.data.Location p : ret.getBundlePoints()) {
      final com.cburch.logisim.circuit.WireBundle pb = ret.getBundleAt(p);
      final com.cburch.logisim.data.BitWidth width = points.getWidth(p);
      if (width != BitWidth.UNKNOWN) {
        pb.setWidth(width, p);
      }
    }

    // determine the bundles at the end of each splitter
    for (final com.cburch.logisim.circuit.Splitter spl : splitters) {
      final java.util.ArrayList<com.cburch.logisim.comp.EndData> ends = new ArrayList<>(spl.getEnds());
      int index = -1;
      for (final com.cburch.logisim.comp.EndData end : ends) {
        index++;
        final com.cburch.logisim.data.Location p = end.getLocation();
        final com.cburch.logisim.circuit.WireBundle pb = ret.getBundleAt(p);
        if (pb != null) {
          pb.setWidth(end.getWidth(), p);
          spl.wireData.endBundle[index] = pb;
        }
      }
    }

    // unite threads going through splitters
    for (final com.cburch.logisim.circuit.Splitter spl : splitters) {
      synchronized (spl) {
        final com.cburch.logisim.circuit.SplitterAttributes splAttrs = (SplitterAttributes) spl.getAttributeSet();
        final byte[] bitEnd = splAttrs.bitEnd;
        final com.cburch.logisim.circuit.CircuitWires.SplitterData splData = spl.wireData;
        final com.cburch.logisim.circuit.WireBundle fromBundle = splData.endBundle[0];
        if (fromBundle == null || !fromBundle.isValid()) continue;

        for (int i = 0; i < bitEnd.length; i++) {
          byte j = bitEnd[i];
          if (j > 0) {
            byte thr = spl.bitThread[i];
            final com.cburch.logisim.circuit.WireBundle toBundle = splData.endBundle[j];
            final com.cburch.logisim.circuit.WireThread[] toThreads = toBundle.threads;
            if (toThreads != null && toBundle.isValid()) {
              final com.cburch.logisim.circuit.WireThread[] fromThreads = fromBundle.threads;
              if (i >= fromThreads.length) {
                throw new ArrayIndexOutOfBoundsException("from " + i + " of " + fromThreads.length);
              }
              if (thr >= toThreads.length) {
                throw new ArrayIndexOutOfBoundsException("to " + thr + " of " + toThreads.length);
              }
              fromThreads[i].unite(toThreads[thr]);
            }
          }
        }
      }
    }

    // merge any threads united by previous step
    for (final com.cburch.logisim.circuit.WireBundle wireBundle : ret.getBundles()) {
      if (wireBundle.isValid() && wireBundle.threads != null) {
        for (int i = 0; i < wireBundle.threads.length; i++) {
          final com.cburch.logisim.circuit.WireThread thr = wireBundle.threads[i].find();
          wireBundle.threads[i] = thr;
          thr.getBundles().add(new ThreadBundle(i, wireBundle));
        }
      }
    }

    // All threads are sewn together! Compute the exception set before
    // leaving
    final java.util.Collection<com.cburch.logisim.circuit.WidthIncompatibilityData> exceptions = points.getWidthIncompatibilityData();
    if (CollectionUtil.isNotEmpty(exceptions)) {
      for (final com.cburch.logisim.circuit.WidthIncompatibilityData wid : exceptions) {
        ret.addWidthIncompatibilityData(wid);
      }
    }
    for (final com.cburch.logisim.circuit.WireBundle wireBundle : ret.getBundles()) {
      final com.cburch.logisim.circuit.WidthIncompatibilityData e = wireBundle.getWidthIncompatibilityData();
      if (e != null) ret.addWidthIncompatibilityData(e);
    }
  }

  private void connectPullResistors(BundleMap ret) {
    for (final com.cburch.logisim.comp.Component comp : pulls) {
      final com.cburch.logisim.data.Location loc = comp.getEnd(0).getLocation();
      com.cburch.logisim.circuit.WireBundle b = ret.getBundleAt(loc);
      if (b == null) {
        b = ret.createBundleAt(loc);
        b.points.add(loc);
        ret.setBundleAt(loc, b);
      }
      final com.cburch.logisim.instance.Instance instance = Instance.getInstanceFor(comp);
      b.addPullValue(PullResistor.getPullValue(instance));
    }
  }

  private void connectTunnels(BundleMap ret) {
    // determine the sets of tunnels
    final java.util.HashMap<java.lang.String,java.util.ArrayList<com.cburch.logisim.data.Location>> tunnelSets = new HashMap<String, ArrayList<Location>>();
    for (final com.cburch.logisim.comp.Component comp : tunnels) {
      final java.lang.String label = comp.getAttributeSet().getValue(StdAttr.LABEL).trim();
      if (!label.equals("")) {
        final java.util.ArrayList<com.cburch.logisim.data.Location> tunnelSet = tunnelSets.computeIfAbsent(label, k -> new ArrayList<>(3));
        tunnelSet.add(comp.getLocation());
      }
    }

    // now connect the bundles that are tunnelled together
    for (ArrayList<Location> tunnelSet : tunnelSets.values()) {
      WireBundle foundBundle = null;
      Location foundLocation = null;
      for (final com.cburch.logisim.data.Location loc : tunnelSet) {
        final com.cburch.logisim.circuit.WireBundle bundle = ret.getBundleAt(loc);
        if (bundle != null) {
          foundBundle = bundle;
          foundLocation = loc;
          break;
        }
      }
      if (foundBundle == null) {
        foundLocation = tunnelSet.get(0);
        foundBundle = ret.createBundleAt(foundLocation);
      }
      for (final com.cburch.logisim.data.Location loc : tunnelSet) {
        if (loc != foundLocation) {
          final com.cburch.logisim.circuit.WireBundle bundle = ret.getBundleAt(loc);
          if (bundle == null) {
            foundBundle.points.add(loc);
            ret.setBundleAt(loc, foundBundle);
          } else {
            bundle.unite(foundBundle);
          }
        }
      }
    }
  }

  private void connectWires(BundleMap ret) {
    // make a WireBundle object for each tree of connected wires
    for (final com.cburch.logisim.circuit.Wire wire : wires) {
      final com.cburch.logisim.circuit.WireBundle bundleA = ret.getBundleAt(wire.e0);
      if (bundleA == null) {
        final com.cburch.logisim.circuit.WireBundle bundleB = ret.createBundleAt(wire.e1);
        bundleB.points.add(wire.e0);
        ret.setBundleAt(wire.e0, bundleB);
      } else {
        final com.cburch.logisim.circuit.WireBundle bundleB = ret.getBundleAt(wire.e1);
        if (bundleB == null) { // t1 doesn't exist
          bundleA.points.add(wire.e1);
          ret.setBundleAt(wire.e1, bundleA);
        } else {
          bundleB.unite(bundleA); // unite bundles
        }
      }
    }
  }

  void draw(ComponentDrawContext context, Collection<Component> hidden) {
    final boolean showState = context.getShowState();
    final com.cburch.logisim.circuit.CircuitState state = context.getCircuitState();
    final java.awt.Graphics2D g = (Graphics2D) context.getGraphics();
    g.setColor(Color.BLACK);
    GraphicsUtil.switchToWidth(g, Wire.WIDTH);
    final com.cburch.logisim.circuit.WireSet highlighted = context.getHighlightedWires();

    final com.cburch.logisim.circuit.CircuitWires.BundleMap bmap = getBundleMap();
    final boolean isValid = bmap.isValid();
    if (CollectionUtil.isNullOrEmpty(hidden)) {
      for (final com.cburch.logisim.circuit.Wire wire : wires) {
        final com.cburch.logisim.data.Location s = wire.e0;
        final com.cburch.logisim.data.Location t = wire.e1;
        final com.cburch.logisim.circuit.WireBundle wb = bmap.getBundleAt(s);
        int width = 5;
        if (!wb.isValid()) {
          g.setColor(Value.widthErrorColor);
        } else if (showState) {
          g.setColor(!isValid ? Value.nilColor : state.getValue(s).getColor());
        } else {
          g.setColor(Color.BLACK);
        }
        if (highlighted.containsWire(wire)) {
          width = wb.isBus() ? Wire.HIGHLIGHTED_WIDTH_BUS : Wire.HIGHLIGHTED_WIDTH;
          GraphicsUtil.switchToWidth(g, width);
          g.drawLine(s.getX(), s.getY(), t.getX(), t.getY());

          final java.awt.Stroke oldStroke = g.getStroke();
          g.setStroke(Wire.HIGHLIGHTED_STROKE);
          g.setColor(Value.strokeColor);
          g.drawLine(s.getX(), s.getY(), t.getX(), t.getY());
          g.setStroke(oldStroke);
        } else {
          width = wb.isBus() ? Wire.WIDTH_BUS : Wire.WIDTH;
          GraphicsUtil.switchToWidth(g, width);
          g.drawLine(s.getX(), s.getY(), t.getX(), t.getY());
        }
        /* The following part is used by the FPGA-commanders DRC to highlight a wire with DRC
         * problems (KTT1)
         */
        if (wire.isDrcHighlighted()) {
          width += 2;
          g.setColor(wire.getDrcHighlightColor());
          GraphicsUtil.switchToWidth(g, 2);
          if (wire.isVertical()) {
            g.drawLine(s.getX() - width, s.getY(), t.getX() - width, t.getY());
            g.drawLine(s.getX() + width, s.getY(), t.getX() + width, t.getY());
          } else {
            g.drawLine(s.getX(), s.getY() - width, t.getX(), t.getY() - width);
            g.drawLine(s.getX(), s.getY() + width, t.getX(), t.getY() + width);
          }
        }
      }

      for (final com.cburch.logisim.data.Location loc : points.getSplitLocations()) {
        if (points.getComponentCount(loc) > 2) {
          final com.cburch.logisim.circuit.WireBundle wb = bmap.getBundleAt(loc);
          if (wb != null) {
            java.awt.Color color = Color.BLACK;
            if (!wb.isValid()) {
              color = Value.widthErrorColor;
            } else if (showState) {
              color = !isValid ? Value.nilColor : state.getValue(loc).getColor();
            }
            g.setColor(color);

            int radius =
                highlighted.containsLocation(loc)
                    ? wb.isBus()
                        ? (int) (Wire.HIGHLIGHTED_WIDTH_BUS * Wire.DOT_MULTIPLY_FACTOR)
                        : (int) (Wire.HIGHLIGHTED_WIDTH * Wire.DOT_MULTIPLY_FACTOR)
                    : wb.isBus()
                        ? (int) (Wire.WIDTH_BUS * Wire.DOT_MULTIPLY_FACTOR)
                        : (int) (Wire.WIDTH * Wire.DOT_MULTIPLY_FACTOR);
            radius = (int) (radius * Wire.DOT_MULTIPLY_FACTOR);
            g.fillOval(loc.getX() - radius, loc.getY() - radius, radius * 2, radius * 2);
          }
        }
      }
    } else {
      for (final com.cburch.logisim.circuit.Wire wire : wires) {
        if (!hidden.contains(wire)) {
          final com.cburch.logisim.data.Location s = wire.e0;
          final com.cburch.logisim.data.Location t = wire.e1;
          final com.cburch.logisim.circuit.WireBundle wb = bmap.getBundleAt(s);
          if (!wb.isValid()) {
            g.setColor(Value.widthErrorColor);
          } else if (showState) {
            g.setColor(!isValid ? Value.nilColor : state.getValue(s).getColor());
          } else {
            g.setColor(Color.BLACK);
          }
          if (highlighted.containsWire(wire)) {
            GraphicsUtil.switchToWidth(g, Wire.WIDTH + 2);
            g.drawLine(s.getX(), s.getY(), t.getX(), t.getY());
            GraphicsUtil.switchToWidth(g, Wire.WIDTH);
          } else {
            GraphicsUtil.switchToWidth(g, wb.isBus() ? Wire.WIDTH_BUS : Wire.WIDTH);
            g.drawLine(s.getX(), s.getY(), t.getX(), t.getY());
          }
        }
      }

      // this is just an approximation, but it's good enough since
      // the problem is minor, and hidden only exists for a short
      // while at a time anway.
      for (final com.cburch.logisim.data.Location loc : points.getSplitLocations()) {
        if (points.getComponentCount(loc) > 2) {
          int icount = 0;
          for (final com.cburch.logisim.comp.Component comp : points.getComponents(loc)) {
            if (!hidden.contains(comp)) ++icount;
          }
          if (icount > 2) {
            final com.cburch.logisim.circuit.WireBundle wireBundle = bmap.getBundleAt(loc);
            if (wireBundle != null) {
              if (!wireBundle.isValid()) {
                g.setColor(Value.widthErrorColor);
              } else if (showState) {
                g.setColor(!isValid ? Value.nilColor : state.getValue(loc).getColor());
              } else {
                g.setColor(Color.BLACK);
              }
              int radius = highlighted.containsLocation(loc)
                      ? (wireBundle.isBus() ? Wire.HIGHLIGHTED_WIDTH_BUS : Wire.HIGHLIGHTED_WIDTH)
                      : (wireBundle.isBus() ? Wire.WIDTH_BUS : Wire.WIDTH);
              radius = (int) (radius * Wire.DOT_MULTIPLY_FACTOR);
              g.fillOval(loc.getX() - radius, loc.getY() - radius, radius * 2, radius * 2);
            }
          }
        }
      }
    }
  }

  // There are only two threads that need to use the bundle map, I think:
  // the AWT event thread, and the simulation worker thread.
  // AWT does modifications to the components and wires, then voids the
  // masterBundleMap, and eventually recomputes a new map (if needed) during
  // painting. AWT sometimes locks a splitter, then changes components and
  // wires.
  // Computing a new bundle map requires both locking splitters and touching
  // the components and wires, so to avoid deadlock, only the AWT should
  // create the new bundle map.

  /*synchronized*/ private BundleMap getBundleMap() {
    if (SwingUtilities.isEventDispatchThread()) {
      // AWT event thread.
      if (masterBundleMap != null) return masterBundleMap;
      final com.cburch.logisim.circuit.CircuitWires.BundleMap ret = new BundleMap();
      try {
        computeBundleMap(ret);
        masterBundleMap = ret;
      } catch (Exception t) {
        ret.invalidate();
        logger.error(t.getLocalizedMessage());
      }
      return ret;
    } else {
      // Simulation thread.
      try {
        final com.cburch.logisim.circuit.CircuitWires.BundleMap[] ret = new BundleMap[1];
        SwingUtilities.invokeAndWait(() -> ret[0] = getBundleMap());
        return ret[0];
      } catch (Exception e) {
        final com.cburch.logisim.circuit.CircuitWires.BundleMap ret = new BundleMap();
        ret.invalidate();
        return ret;
      }
    }
  }

  Iterator<? extends Component> getComponents() {
    return IteratorUtil.createJoinedIterator(splitters.iterator(), wires.iterator());
  }

  private Value getThreadValue(CircuitState state, WireThread t) {
    com.cburch.logisim.data.Value ret = Value.UNKNOWN;
    com.cburch.logisim.data.Value pull = Value.UNKNOWN;
    for (final com.cburch.logisim.circuit.CircuitWires.ThreadBundle tb : t.getBundles()) {
      for (final com.cburch.logisim.data.Location p : tb.b.points) {
        final com.cburch.logisim.data.Value val = state.getComponentOutputAt(p);
        if (val != null && val != Value.NIL) {
          ret = ret.combine(val.get(tb.loc));
        }
      }
      final com.cburch.logisim.data.Value pullHere = tb.b.getPullValue();
      if (pullHere != Value.UNKNOWN) pull = pull.combine(pullHere);
    }
    if (pull != Value.UNKNOWN) {
      ret = pullValue(ret, pull);
    }
    return ret;
  }

  BitWidth getWidth(Location q) {
    final com.cburch.logisim.data.BitWidth det = points.getWidth(q);
    if (det != BitWidth.UNKNOWN) return det;

    final com.cburch.logisim.circuit.CircuitWires.BundleMap bmap = getBundleMap();
    if (!bmap.isValid()) return BitWidth.UNKNOWN;
    final com.cburch.logisim.circuit.WireBundle qb = bmap.getBundleAt(q);
    if (qb != null && qb.isValid()) return qb.getWidth();

    return BitWidth.UNKNOWN;
  }

  Location getWidthDeterminant(Location q) {
    final com.cburch.logisim.data.BitWidth det = points.getWidth(q);
    if (det != BitWidth.UNKNOWN) return q;

    final com.cburch.logisim.circuit.WireBundle qb = getBundleMap().getBundleAt(q);
    if (qb != null && qb.isValid()) return qb.getWidthDeterminant();

    return q;
  }

  Set<WidthIncompatibilityData> getWidthIncompatibilityData() {
    return getBundleMap().getWidthIncompatibilityData();
  }

  Bounds getWireBounds() {
    com.cburch.logisim.data.Bounds bds = bounds;
    if (bds == Bounds.EMPTY_BOUNDS) {
      bds = recomputeBounds();
    }
    return bds;
  }

  WireBundle getWireBundle(Location query) {
    final com.cburch.logisim.circuit.CircuitWires.BundleMap bundleMap = getBundleMap();
    return bundleMap.getBundleAt(query);
  }

  Set<Wire> getWires() {
    return wires;
  }

  WireSet getWireSet(Wire start) {
    final com.cburch.logisim.circuit.WireBundle wireBundle = getWireBundle(start.e0);
    if (wireBundle == null) return WireSet.EMPTY;
    final java.util.HashSet<com.cburch.logisim.circuit.Wire> wires = new HashSet<Wire>();
    for (final com.cburch.logisim.data.Location loc : wireBundle.points) {
      wires.addAll(points.getWires(loc));
    }
    return new WireSet(wires);
  }

  //
  // query methods
  //
  boolean isMapVoided() {
    return masterBundleMap == null;
  }

  //
  // utility methods
  //
  void propagate(CircuitState circState, Set<Location> points) {
    final com.cburch.logisim.circuit.CircuitWires.BundleMap map = getBundleMap();
    final java.util.concurrent.CopyOnWriteArraySet<com.cburch.logisim.circuit.WireThread> dirtyThreads = new CopyOnWriteArraySet<WireThread>(); // affected threads

    // get state, or create a new one if current state is outdated
    com.cburch.logisim.circuit.CircuitWires.State state = circState.getWireData();
    if (state == null || state.bundleMap != map) {
      // if it is outdated, we need to compute for all threads
      state = new State(map);
      for (final com.cburch.logisim.circuit.WireBundle bundle : map.getBundles()) {
        final com.cburch.logisim.circuit.WireThread[] wireThreads = bundle.threads;
        if (bundle.isValid() && wireThreads != null) {
          dirtyThreads.addAll(Arrays.asList(wireThreads));
        }
      }
      circState.setWireData(state);
    }

    // determine affected threads, and set values for unwired points
    for (final com.cburch.logisim.data.Location point : points) {
      final com.cburch.logisim.circuit.WireBundle wireBundle = map.getBundleAt(point);
      if (wireBundle == null) { // point is not wired
        circState.setValueByWire(point, circState.getComponentOutputAt(point));
      } else {
        final com.cburch.logisim.circuit.WireThread[] th = wireBundle.threads;
        if (!wireBundle.isValid() || th == null) {
          // immediately propagate NILs across invalid bundles
          final java.util.concurrent.CopyOnWriteArraySet<com.cburch.logisim.data.Location> pbPoints = wireBundle.points;
          if (pbPoints == null) {
            circState.setValueByWire(point, Value.NIL);
          } else {
            for (final com.cburch.logisim.data.Location loc2 : pbPoints) {
              circState.setValueByWire(loc2, Value.NIL);
            }
          }
        } else {
          dirtyThreads.addAll(Arrays.asList(th));
        }
      }
    }

    if (dirtyThreads.isEmpty()) return;

    // determine values of affected threads
    final java.util.HashSet<com.cburch.logisim.circuit.CircuitWires.ThreadBundle> bundles = new HashSet<ThreadBundle>();
    for (final com.cburch.logisim.circuit.WireThread t : dirtyThreads) {
      final com.cburch.logisim.data.Value v = getThreadValue(circState, t);
      state.thrValues.put(t, v);
      bundles.addAll(t.getBundles());
    }

    // now propagate values through circuit
    for (final com.cburch.logisim.circuit.CircuitWires.ThreadBundle tb : bundles) {
      final com.cburch.logisim.circuit.WireBundle b = tb.b;

      Value bv = null;
      if (!b.isValid() || b.threads == null) {
        // do nothing
      } else if (b.threads.length == 1) {
        bv = state.thrValues.get(b.threads[0]);
      } else {
        final com.cburch.logisim.data.Value[] tvs = new Value[b.threads.length];
        boolean tvsValid = true;
        for (int i = 0; i < tvs.length; i++) {
          final com.cburch.logisim.data.Value tv = state.thrValues.get(b.threads[i]);
          if (tv == null) {
            tvsValid = false;
            break;
          }
          tvs[i] = tv;
        }
        if (tvsValid) bv = Value.create(tvs);
      }

      if (bv != null) {
        for (final com.cburch.logisim.data.Location p : b.points) {
          circState.setValueByWire(p, bv);
        }
      }
    }
  }

  private Bounds recomputeBounds() {
    final java.util.Iterator<com.cburch.logisim.circuit.Wire> it = wires.iterator();
    if (!it.hasNext()) {
      bounds = Bounds.EMPTY_BOUNDS;
      return Bounds.EMPTY_BOUNDS;
    }

    com.cburch.logisim.circuit.Wire w = it.next();
    int xmin = w.e0.getX();
    int ymin = w.e0.getY();
    int xmax = w.e1.getX();
    int ymax = w.e1.getY();
    while (it.hasNext()) {
      w = it.next();
      final int x0 = w.e0.getX();
      if (x0 < xmin) xmin = x0;
      final int x1 = w.e1.getX();
      if (x1 > xmax) xmax = x1;
      final int y0 = w.e0.getY();
      if (y0 < ymin) ymin = y0;
      final int y1 = w.e1.getY();
      if (y1 > ymax) ymax = y1;
    }
    bounds = Bounds.create(xmin, ymin, xmax - xmin + 1, ymax - ymin + 1);
    return bounds;
  }

  /*synchronized*/ void remove(Component comp) {
    if (comp instanceof Wire wire) {
      removeWire(wire);
    } else if (comp instanceof Splitter) {
      splitters.remove(comp);
    } else {
      final com.cburch.logisim.comp.ComponentFactory factory = comp.getFactory();
      if (factory instanceof Tunnel) {
        tunnels.remove(comp);
        comp.getAttributeSet().removeAttributeListener(tunnelListener);
      } else if (factory instanceof PullResistor) {
        pulls.remove(comp);
        comp.getAttributeSet().removeAttributeListener(tunnelListener);
      }
    }
    points.remove(comp);
    voidBundleMap();
  }

  /*synchronized*/ void remove(Component comp, EndData end) {
    points.remove(comp, end);
    voidBundleMap();
  }

  private void removeWire(Wire w) {
    if (!wires.remove(w)) return;

    if (bounds != Bounds.EMPTY_BOUNDS) {
      // bounds is valid - invalidate if endpoint on border
      final com.cburch.logisim.data.Bounds smaller = bounds.expand(-2);
      if (!smaller.contains(w.e0) || !smaller.contains(w.e1)) {
        bounds = Bounds.EMPTY_BOUNDS;
      }
    }
  }

  /*synchronized*/ void replace(Component comp, EndData oldEnd, EndData newEnd) {
    points.remove(comp, oldEnd);
    points.add(comp, newEnd);
    voidBundleMap();
  }

  //
  // helper methods
  //
  private void voidBundleMap() {
    // This should really only be called by AWT thread, but main() also
    // calls it during startup. It should not be called by the simulation
    // thread.
    masterBundleMap = null;
  }
}
