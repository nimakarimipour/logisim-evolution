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

import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentEvent;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.comp.ManagedComponent;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.MenuExtender;
import com.cburch.logisim.tools.ToolTipMaker;
import com.cburch.logisim.tools.WireRepair;
import com.cburch.logisim.tools.WireRepairData;
import com.cburch.logisim.util.GraphicsUtil;
import javax.swing.JPopupMenu;

public class Splitter extends ManagedComponent
    implements WireRepair, ToolTipMaker, MenuExtender, AttributeListener {

  /**
   * Unique identifier of the tool, used as reference in project files.
   * Do NOT change as it will prevent project files from loading.
   *
   * Identifier value must MUST be unique string among all tools.
   */
  public static final String _ID = "Splitter";

  private static void appendBuf(StringBuilder buf, int start, int end) {
    if (buf.length() > 0) buf.append(",");
    if (start == end) {
      buf.append(start);
    } else {
      buf.append(start).append("-").append(end);
    }
  }

  private boolean isMarked = false;

  public void setMarked(boolean value) {
    isMarked = value;
  }

  public boolean isMarked() {
    return isMarked;
  }

  // basic data
  byte[] bitThread; // how each bit maps to thread within end

  // derived data
  CircuitWires.SplitterData wireData;

  public Splitter(Location loc, AttributeSet attrs) {
    super(loc, attrs, 3);
    configureComponent();
    attrs.addAttributeListener(this);
  }

  //
  // AttributeListener methods
  //
  @Override
  public void attributeListChanged(AttributeEvent e) {}

  @Override
  public void attributeValueChanged(AttributeEvent e) {
    configureComponent();
  }

  private synchronized void configureComponent() {
    final com.cburch.logisim.circuit.SplitterAttributes attrs = (SplitterAttributes) getAttributeSet();
    final com.cburch.logisim.circuit.SplitterParameters parms = attrs.getParameters();
    final byte fanout = attrs.fanout;
    final byte[] bitEnd = attrs.bitEnd;

    // compute width of each end
    bitThread = new byte[bitEnd.length];
    final byte[] endWidth = new byte[fanout + 1];
    endWidth[0] = (byte) bitEnd.length;
    for (int i = 0; i < bitEnd.length; i++) {
      final byte thr = bitEnd[i];
      if (thr > 0) {
        bitThread[i] = endWidth[thr];
        endWidth[thr]++;
      } else {
        bitThread[i] = -1;
      }
    }

    // compute end positions
    final com.cburch.logisim.data.Location origin = getLocation();
    int x = origin.getX() + parms.getEnd0X();
    int y = origin.getY() + parms.getEnd0Y();
    final int dx = parms.getEndToEndDeltaX();
    final int dy = parms.getEndToEndDeltaY();

    final com.cburch.logisim.comp.EndData[] ends = new EndData[fanout + 1];
    ends[0] = new EndData(origin, BitWidth.create(bitEnd.length), EndData.INPUT_OUTPUT);
    for (int i = 0; i < fanout; i++) {
      ends[i + 1] = new EndData(Location.create(x, y, true), BitWidth.create(endWidth[i + 1]), EndData.INPUT_OUTPUT);
      x += dx;
      y += dy;
    }
    wireData = new CircuitWires.SplitterData(fanout);
    setEnds(ends);
    recomputeBounds();
    fireComponentInvalidated(new ComponentEvent(this));
  }

  @Override
  public void configureMenu(JPopupMenu menu, Project proj) {
    menu.addSeparator();
    menu.add(new SplitterDistributeItem(proj, this, 1));
    menu.add(new SplitterDistributeItem(proj, this, -1));
  }

  @Override
  public boolean contains(Location loc) {
    if (super.contains(loc)) {
      final com.cburch.logisim.data.Location myLoc = getLocation();
      final com.cburch.logisim.data.Direction facing = getAttributeSet().getValue(StdAttr.FACING);
      if (facing == Direction.EAST || facing == Direction.WEST) {
        return Math.abs(loc.getX() - myLoc.getX()) > 5 || loc.manhattanDistanceTo(myLoc) <= 5;
      } else {
        return Math.abs(loc.getY() - myLoc.getY()) > 5 || loc.manhattanDistanceTo(myLoc) <= 5;
      }
    } else {
      return false;
    }
  }

  //
  // user interface methods
  //
  @Override
  public void draw(ComponentDrawContext context) {
    final com.cburch.logisim.circuit.SplitterAttributes attrs = (SplitterAttributes) getAttributeSet();
    if (attrs.appear == SplitterAttributes.APPEAR_LEGACY) {
      SplitterPainter.drawLegacy(context, attrs, getLocation());
    } else {
      final com.cburch.logisim.data.Location loc = getLocation();
      SplitterPainter.drawLines(context, attrs, loc);
      SplitterPainter.drawLabels(context, attrs, loc);
      context.drawPins(this);
    }
    if (isMarked) {
      final java.awt.Graphics g = context.getGraphics();
      final com.cburch.logisim.data.Bounds bds = this.getBounds();
      g.setColor(Netlist.DRC_INSTANCE_MARK_COLOR);
      GraphicsUtil.switchToWidth(g, 2);
      g.drawRoundRect(bds.getX() - 10, bds.getY() - 10, bds.getWidth() + 20, bds.getHeight() + 20, 20, 20);
    }
  }

  public byte[] getEndpoints() {
    return ((SplitterAttributes) getAttributeSet()).bitEnd;
  }

  //
  // abstract ManagedComponent methods
  //
  @Override
  public ComponentFactory getFactory() {
    return SplitterFactory.instance;
  }

  @Override
  public void setFactory(ComponentFactory fact) {}

  @Override
  public Object getFeature(Object key) {
    if (key == WireRepair.class) return this;
    if (key == ToolTipMaker.class) return this;
    if (key == MenuExtender.class) return this;
    else return super.getFeature(key);
  }

  @Override
  public String getToolTip(ComponentUserEvent e) {
    int end = -1;
    for (int i = getEnds().size() - 1; i >= 0; i--) {
      if (getEndLocation(i).manhattanDistanceTo(e.getX(), e.getY()) < 10) {
        end = i;
        break;
      }
    }

    if (end == 0) return S.get("splitterCombinedTip");
    if (end < 0) return null;
    int bits = 0;
    final java.lang.StringBuilder buffer = new StringBuilder();
    final com.cburch.logisim.circuit.SplitterAttributes attrs = (SplitterAttributes) getAttributeSet();
    final byte[] bitEnd = attrs.bitEnd;
    boolean inString = false;
    int beginString = 0;
    for (int i = 0; i < bitEnd.length; i++) {
      if (bitEnd[i] == end) {
        bits++;
        if (!inString) {
          inString = true;
          beginString = i;
        }
      } else if (inString) {
        appendBuf(buffer, i - 1, beginString);
        inString = false;
      }
    }

    if (inString) appendBuf(buffer, bitEnd.length - 1, beginString);
    final java.lang.String base = switch (bits) {
      case 0 -> S.get("splitterSplit0Tip");
      case 1 -> S.get("splitterSplit1Tip");
      default -> S.get("splitterSplitManyTip");
    };
    return String.format(base, buffer.toString());
  }

  @Override
  public void propagate(CircuitState state) {
    // handled by CircuitWires, nothing to do
  }

  @Override
  public boolean shouldRepairWire(WireRepairData data) {
    return true;
  }
}
