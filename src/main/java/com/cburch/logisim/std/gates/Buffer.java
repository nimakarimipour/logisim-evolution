/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.gates;

import static com.cburch.logisim.std.Strings.S;

import com.cburch.logisim.circuit.ExpressionComputer;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Options;
import com.cburch.logisim.fpga.designrulecheck.CorrectLabel;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Color;
import java.awt.Graphics2D;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RUntainted;

class Buffer extends InstanceFactory {
  /**
   * Unique identifier of the tool, used as reference in project files.
   * Do NOT change as it will prevent project files from loading.
   *
   * Identifier value must MUST be unique string among all tools.
   */
  public static final String _ID = "Buffer";

  //
  // static methods - shared with other classes
  //
  static Value repair(InstanceState state, Value v) {
    final com.cburch.logisim.data.AttributeSet opts = state.getProject().getOptions().getAttributeSet();
    Object onUndefined = opts.getValue(Options.ATTR_GATE_UNDEFINED);
    final boolean errorIfUndefined = onUndefined.equals(Options.GATE_UNDEFINED_ERROR);
    Value repaired;
    if (errorIfUndefined) {
      final int vw = v.getWidth();
      final com.cburch.logisim.data.BitWidth w = state.getAttributeValue(StdAttr.WIDTH);
      final int ww = w.getWidth();
      if (vw == ww && v.isFullyDefined()) return v;
      final com.cburch.logisim.data.Value[] vs = new Value[w.getWidth()];
      for (int i = 0; i < vs.length; i++) {
        final com.cburch.logisim.data.Value ini = i < vw ? v.get(i) : Value.ERROR;
        vs[i] = ini.isFullyDefined() ? ini : Value.ERROR;
      }
      repaired = Value.create(vs);
    } else {
      repaired = v;
    }

    Object outType = state.getAttributeValue(GateAttributes.ATTR_OUTPUT);
    return AbstractGate.pullOutput(repaired, outType);
  }

  public static final InstanceFactory FACTORY = new Buffer();

  private Buffer() {
    super(_ID, S.getter("bufferComponent"), new AbstractBufferHdlGenerator(false));
    setAttributes(
        new Attribute[] {
          StdAttr.FACING,
          StdAttr.WIDTH,
          GateAttributes.ATTR_OUTPUT,
          StdAttr.LABEL,
          StdAttr.LABEL_FONT
        },
        new Object[] {
          Direction.EAST, BitWidth.ONE, GateAttributes.OUTPUT_01, "", StdAttr.DEFAULT_LABEL_FONT
        });
    setFacingAttribute(StdAttr.FACING);
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
    setPorts(
        new Port[] {
          new Port(0, 0, Port.OUTPUT, StdAttr.WIDTH), new Port(0, -20, Port.INPUT, StdAttr.WIDTH),
        });
  }

  //
  // methods for instances
  //
  @Override
  protected void configureNewInstance(Instance instance) {
    configurePorts(instance);
    instance.addAttributeListener();
    NotGate.configureLabel(instance, false, null);
  }

  private void configurePorts(Instance instance) {
    final com.cburch.logisim.data.Direction facing = instance.getAttributeValue(StdAttr.FACING);

    final com.cburch.logisim.instance.Port[] ports = new Port[2];
    ports[0] = new Port(0, 0, Port.OUTPUT, StdAttr.WIDTH);
    final com.cburch.logisim.data.Location out = Location.create(0, 0, true).translate(facing, -20);
    ports[1] = new Port(out.getX(), out.getY(), Port.INPUT, StdAttr.WIDTH);
    instance.setPorts(ports);
  }

  @Override
  public String getHDLName(AttributeSet attrs) {
    final java.lang.StringBuilder completeName = new StringBuilder();
    completeName.append(CorrectLabel.getCorrectLabel(this.getName()).toUpperCase());
    completeName.append("_COMPONENT");
    final com.cburch.logisim.data.BitWidth width = attrs.getValue(StdAttr.WIDTH);
    if (width.getWidth() > 1) completeName.append("_BUS");
    return completeName.toString();
  }

  @Override
  public Object getInstanceFeature(final Instance instance, Object key) {
    if (key == ExpressionComputer.class) {
      return (ExpressionComputer) expressionMap -> {
        final int width = instance.getAttributeValue(StdAttr.WIDTH).getWidth();
        for (int b = 0; b < width; b++) {
          final com.cburch.logisim.analyze.model.Expression e = expressionMap.get(instance.getPortLocation(1), b);
          if (e != null) {
            expressionMap.put(instance.getPortLocation(0), b, e);
          }
        }
      };
    }
    return super.getInstanceFeature(instance, key);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    final com.cburch.logisim.data.Direction facing = attrs.getValue(StdAttr.FACING);
    if (facing == Direction.SOUTH) return Bounds.create(-9, -20, 18, 20);
    if (facing == Direction.NORTH) return Bounds.create(-9, 0, 18, 20);
    if (facing == Direction.WEST) return Bounds.create(0, -9, 20, 18);
    return Bounds.create(-20, -9, 20, 18);
  }

  @Override
  public boolean hasThreeStateDrivers(AttributeSet attrs) {
    return attrs.containsAttribute(GateAttributes.ATTR_OUTPUT)
        ? attrs.getValue(GateAttributes.ATTR_OUTPUT) != GateAttributes.OUTPUT_01
        : false;
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.FACING) {
      instance.recomputeBounds();
      configurePorts(instance);
      NotGate.configureLabel(instance, false, null);
    }
  }

  private void paintBase(InstancePainter painter) {
    final com.cburch.logisim.data.Direction facing = painter.getAttributeValue(StdAttr.FACING);
    final com.cburch.logisim.data.Location loc = painter.getLocation();
    final int x = loc.getX();
    final int y = loc.getY();
    final java.awt.Graphics g = painter.getGraphics();
    g.translate(x, y);
    double rotate = 0.0d;
    if (facing != Direction.EAST && g instanceof Graphics2D g2d) {
      rotate = -facing.toRadians();
      g2d.rotate(rotate);
    }

    GraphicsUtil.switchToWidth(g, 2);
    final java.lang.Object shape = painter.getGateShape();
    if (shape == AppPreferences.SHAPE_RECTANGULAR) {
      g.drawRect(-19, -9, 18, 18);
      GraphicsUtil.drawCenteredText(g, "1", -10, 0);
    } else {
      final int[] xp = new int[4];
      final int[] yp = new int[4];
      xp[0] = 0;
      yp[0] = 0;
      xp[1] = -19;
      yp[1] = -7;
      xp[2] = -19;
      yp[2] = 7;
      xp[3] = 0;
      yp[3] = 0;
      g.drawPolyline(xp, yp, 4);
    }
    if (rotate != 0.0) {
      ((Graphics2D) g).rotate(-rotate);
    }
    g.translate(-x, -y);
  }

  //
  // painting methods
  //
  @Override
  public void paintGhost(InstancePainter painter) {
    paintBase(painter);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    final java.awt.Graphics g = painter.getGraphics();
    g.setColor(Color.BLACK);
    paintBase(painter);
    painter.drawPorts();
    painter.drawLabel();
  }

  @Override
  public void propagate(InstanceState state) {
    final com.cburch.logisim.data.Value in = Buffer.repair(state, state.getPortValue(1));
    state.setPort(0, in, GateAttributes.DELAY);
  }

  @Override
  public void paintIcon(InstancePainter painter) {
    final java.awt.Graphics2D g = (Graphics2D) painter.getGraphics();
    if (painter.getGateShape() == AppPreferences.SHAPE_RECTANGULAR)
      AbstractGate.paintIconIEC(g, "1", false, true);
    else
      AbstractGate.paintIconBufferAnsi(g, false, false);
  }
}
