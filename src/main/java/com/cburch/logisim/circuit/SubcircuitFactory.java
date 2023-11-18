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

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.CircuitStateHolder;
import com.cburch.logisim.tools.MenuExtender;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.StringUtil;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.font.TextLayout;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RUntainted;

public class SubcircuitFactory extends InstanceFactory {
  private class CircuitFeature implements StringGetter, MenuExtender, ActionListener {
    private final Instance instance;
    private Project proj;

    public CircuitFeature(Instance instance) {
      this.instance = instance;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final com.cburch.logisim.circuit.CircuitState superState = proj.getCircuitState();
      if (superState == null) return;

      final com.cburch.logisim.circuit.CircuitState subState = getSubstate(superState, instance);
      if (subState == null) return;
      proj.setCircuitState(subState);
    }

    @Override
    public void configureMenu(JPopupMenu menu, Project proj) {
      this.proj = proj;
      final java.lang.String name = instance.getFactory().getDisplayName();
      final java.lang.String text = S.get("subcircuitViewItem", name);
      final javax.swing.JMenuItem item = new JMenuItem(text);
      item.addActionListener(this);
      menu.add(item);
      final com.cburch.logisim.tools.CircuitStateHolder.HierarchyInfo hi = new CircuitStateHolder.HierarchyInfo(proj.getCurrentCircuit());
      hi.addComponent(instance.getComponent());
      getSubMenuItems(menu, proj, (CircuitState) instance.getData(proj.getCircuitState()), hi);
    }

    public void getSubMenuItems(JPopupMenu menu, Project proj, CircuitState state,
                                CircuitStateHolder.HierarchyInfo hi) {
      for (final com.cburch.logisim.comp.Component comp : source.getNonWires()) {
        if (comp instanceof InstanceComponent c) {
          if (c.getFactory() instanceof SubcircuitFactory) {
            final com.cburch.logisim.circuit.SubcircuitFactory.CircuitFeature m = (CircuitFeature) c.getFeature(MenuExtender.class);
            final com.cburch.logisim.tools.CircuitStateHolder.HierarchyInfo newhi = hi.getCopy();
            newhi.addComponent(c);
            m.getSubMenuItems(menu, proj, (CircuitState) c.getInstance().getData(state), newhi);
          } else if (c.getInstance().getFactory().providesSubCircuitMenu()) {
            final com.cburch.logisim.tools.MenuExtender m = (MenuExtender) c.getFeature(MenuExtender.class);
            if (m instanceof CircuitStateHolder csh) {
              csh.setCircuitState(state);
              csh.setHierarchyName(hi);
            }
            m.configureMenu(menu, proj);
          }
        }
      }
    }

    @Override
    public String toString() {
      return source.getName();
    }
  }

  private Circuit source;

  public SubcircuitFactory(Circuit source) {
    super("", null, new CircuitHdlGeneratorFactory(source), true);
    this.source = source;
    setFacingAttribute(StdAttr.FACING);
    setDefaultToolTip(new CircuitFeature(null));
    setInstancePoker(SubcircuitPoker.class);
  }

  void computePorts(Instance instance) {
    final com.cburch.logisim.data.Direction facing = instance.getAttributeValue(StdAttr.FACING);
    final java.util.SortedMap<com.cburch.logisim.data.Location,com.cburch.logisim.instance.Instance> portLocs = source.getAppearance().getPortOffsets(facing);
    final com.cburch.logisim.instance.Port[] ports = new Port[portLocs.size()];
    final com.cburch.logisim.instance.Instance[] pins = new Instance[portLocs.size()];
    int i = -1;
    for (final java.util.Map.Entry<com.cburch.logisim.data.Location,com.cburch.logisim.instance.Instance> portLoc : portLocs.entrySet()) {
      i++;
      final com.cburch.logisim.data.Location loc = portLoc.getKey();
      final com.cburch.logisim.instance.Instance pin = portLoc.getValue();
      final java.lang.String type = Pin.FACTORY.isInputPin(pin) ? Port.INPUT : Port.OUTPUT;
      final com.cburch.logisim.data.BitWidth width = pin.getAttributeValue(StdAttr.WIDTH);
      ports[i] = new Port(loc.getX(), loc.getY(), type, width);
      pins[i] = pin;

      final java.lang.String label = pin.getAttributeValue(StdAttr.LABEL);
      if (label != null && label.length() > 0) {
        ports[i].setToolTip(StringUtil.constantGetter(label));
      }
    }

    final com.cburch.logisim.circuit.CircuitAttributes attrs = (CircuitAttributes) instance.getAttributeSet();
    attrs.setPinInstances(pins);
    instance.setPorts(ports);
    instance.recomputeBounds();
    configureLabel(instance); // since this affects the circuit's bounds
  }

  private void configureLabel(Instance instance) {
    final com.cburch.logisim.data.Bounds bds = instance.getBounds();
    final com.cburch.logisim.data.Direction loc = instance.getAttributeValue(CircuitAttributes.LABEL_LOCATION_ATTR);

    int x = bds.getX() + bds.getWidth() / 2;
    int y = bds.getY() + bds.getHeight() / 2;
    int ha = GraphicsUtil.H_CENTER;
    int va = GraphicsUtil.V_CENTER;
    if (loc == Direction.EAST) {
      x = bds.getX() + bds.getWidth() + 2;
      ha = GraphicsUtil.H_LEFT;
    } else if (loc == Direction.WEST) {
      x = bds.getX() - 2;
      ha = GraphicsUtil.H_RIGHT;
    } else if (loc == Direction.SOUTH) {
      y = bds.getY() + bds.getHeight() + 2;
      va = GraphicsUtil.V_TOP;
    } else {
      y = bds.getY() - 2;
      va = GraphicsUtil.V_BASELINE;
    }
    instance.setTextField(StdAttr.LABEL, StdAttr.LABEL_FONT, x, y, ha, va);
  }

  //
  // methods for configuring instances
  //
  @Override
  public void configureNewInstance(Instance instance) {
    final com.cburch.logisim.circuit.CircuitAttributes attrs = (CircuitAttributes) instance.getAttributeSet();
    attrs.setSubcircuit(instance);

    instance.addAttributeListener();
    computePorts(instance);
    // configureLabel(instance); already done in computePorts
  }

  /**
   * Code taken from Cornell's version of Logisim: http://www.cs.cornell.edu/courses/cs3410/2015sp/
   */
  @Override
  public boolean contains(Location loc, AttributeSet attrs) {
    if (super.contains(loc, attrs)) {
      final com.cburch.logisim.data.Direction facing = attrs.getValue(StdAttr.FACING);
      final com.cburch.logisim.data.Direction defaultFacing = source.getAppearance().getFacing();
      Location query;

      if (facing.equals(defaultFacing)) {
        query = loc;
      } else {
        query = loc.rotate(facing, defaultFacing, 0, 0);
      }

      return source.getAppearance().contains(query);
    } else {
      return false;
    }
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new CircuitAttributes(source);
  }

  private void drawCircuitLabel(InstancePainter painter, Bounds bds, Direction facing, Direction defaultFacing) {
    final com.cburch.logisim.data.AttributeSet staticAttrs = source.getStaticAttributes();

    java.lang.String label = staticAttrs.getValue(CircuitAttributes.CIRCUIT_LABEL_ATTR);

    if (label != null && !label.equals("")) {
      final com.cburch.logisim.data.Direction up = staticAttrs.getValue(CircuitAttributes.CIRCUIT_LABEL_FACING_ATTR);
      final java.awt.Font font = staticAttrs.getValue(CircuitAttributes.CIRCUIT_LABEL_FONT_ATTR);

      int back = label.indexOf('\\');
      int lines = 1;
      boolean backs = false;
      while (back >= 0 && back <= label.length() - 2) {
        final char c = label.charAt(back + 1);
        if (c == 'n') lines++;
        else if (c == '\\') backs = true;
        back = label.indexOf('\\', back + 2);
      }

      final int x = bds.getX() + bds.getWidth() / 2;
      int y = bds.getY() + bds.getHeight() / 2;
      final java.awt.Graphics g = painter.getGraphics().create();
      final double angle = Math.PI / 2 - (up.toRadians() - defaultFacing.toRadians()) - facing.toRadians();
      if (g instanceof Graphics2D g2 && Math.abs(angle) > 0.01) {
        g2.rotate(angle, x, y);
      }
      g.setFont(font);
      if (lines == 1 && !backs) {
        GraphicsUtil.drawCenteredText(g, label, x, y);
      } else {
        final java.awt.FontMetrics fm = g.getFontMetrics();
        final int height = fm.getHeight();
        y = y - (height * lines - fm.getLeading()) / 2 + fm.getAscent();
        back = label.indexOf('\\');
        while (back >= 0 && back <= label.length() - 2) {
          final char c = label.charAt(back + 1);
          if (c == 'n') {
            final java.lang.String line = label.substring(0, back);
            GraphicsUtil.drawText(g, line, x, y, GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE);
            y += height;
            label = label.substring(back + 2);
            back = label.indexOf('\\');
          } else if (c == '\\') {
            label = label.substring(0, back) + label.substring(back + 1);
            back = label.indexOf('\\', back + 1);
          } else {
            back = label.indexOf('\\', back + 2);
          }
        }
        GraphicsUtil.drawText(g, label, x, y, GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE);
      }
      g.dispose();
    }
  }

  @Override
  public StringGetter getDisplayGetter() {
    return StringUtil.constantGetter(source.getName());
  }

  @Override
  public Object getInstanceFeature(Instance instance, Object key) {
    if (key == MenuExtender.class) return new CircuitFeature(instance);
    return super.getInstanceFeature(instance, key);
  }

  @Override
  public String getName() {
    return source.getName();
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    final com.cburch.logisim.data.Direction facing = attrs.getValue(StdAttr.FACING);
    final com.cburch.logisim.data.Direction defaultFacing = source.getAppearance().getFacing();
    final com.cburch.logisim.data.Bounds bds = source.getAppearance().getOffsetBounds();
    return bds.rotate(defaultFacing, facing, 0, 0);
  }

  public Circuit getSubcircuit() {
    return source;
  }

  public void setSubcircuit(Circuit sub) {
    source = sub;
  }

  public CircuitState getSubstate(CircuitState superState, Component comp) {
    return getSubstate(createInstanceState(superState, comp));
  }

  //
  // propagation-oriented methods
  //
  public CircuitState getSubstate(CircuitState superState, Instance instance) {
    return getSubstate(createInstanceState(superState, instance));
  }

  private CircuitState getSubstate(InstanceState instanceState) {
    com.cburch.logisim.circuit.CircuitState subState = (CircuitState) instanceState.getData();
    if (subState == null) {
      subState = new CircuitState(instanceState.getProject(), source);
      instanceState.setData(subState);
      instanceState.fireInvalidated();
    }
    return subState;
  }

  @Override
  public void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.FACING) {
      computePorts(instance);
    } else if (attr == CircuitAttributes.LABEL_LOCATION_ATTR) {
      configureLabel(instance);
    } else if (attr == CircuitAttributes.APPEARANCE_ATTR) {
      CircuitTransaction xn = new ChangeAppearanceTransaction();
      source.getLocker().execute(xn);
      computePorts(instance);
    }
  }

  private class ChangeAppearanceTransaction extends CircuitTransaction {

    @Override
    protected Map<Circuit, Integer> getAccessedCircuits() {
      final java.util.HashMap<com.cburch.logisim.circuit.Circuit,java.lang.Integer> accessMap = new HashMap<Circuit, Integer>();
      for (final com.cburch.logisim.circuit.Circuit supercirc : source.getCircuitsUsingThis()) {
        accessMap.put(supercirc, READ_WRITE);
      }
      return accessMap;
    }

    @Override
    protected void run(CircuitMutator mutator) {}
  }

  private void paintBase(InstancePainter painter, Graphics g) {
    final com.cburch.logisim.circuit.CircuitAttributes attrs = (CircuitAttributes) painter.getAttributeSet();
    final com.cburch.logisim.data.Direction facing = attrs.getFacing();
    final com.cburch.logisim.data.Direction defaultFacing = source.getAppearance().getFacing();
    final com.cburch.logisim.data.Location loc = painter.getLocation();
    g.translate(loc.getX(), loc.getY());
    source.getAppearance().paintSubcircuit(painter, g, facing);
    drawCircuitLabel(painter, getOffsetBounds(attrs), facing, defaultFacing);
    g.translate(-loc.getX(), -loc.getY());
    painter.drawLabel();
  }

  //
  // user interface features
  //
  @Override
  public void paintGhost(InstancePainter painter) {
    final java.awt.Graphics g = painter.getGraphics();
    final java.awt.Color fg = g.getColor();
    int v = fg.getRed() + fg.getGreen() + fg.getBlue();
    Composite oldComposite = null;
    if (g instanceof Graphics2D g2d && v > 50) {
      oldComposite = g2d.getComposite();
      Composite c = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f);
      ((Graphics2D) g).setComposite(c);
    }
    paintBase(painter, g);
    if (oldComposite != null) {
      ((Graphics2D) g).setComposite(oldComposite);
    }
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    paintBase(painter, painter.getGraphics());
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState superState) {
    final com.cburch.logisim.circuit.CircuitState subState = getSubstate(superState);

    final com.cburch.logisim.circuit.CircuitAttributes attrs = (CircuitAttributes) superState.getAttributeSet();
    final com.cburch.logisim.instance.Instance[] pins = attrs.getPinInstances();
    for (int i = 0; i < pins.length; i++) {
      final com.cburch.logisim.instance.Instance pin = pins[i];
      final com.cburch.logisim.instance.InstanceState pinState = subState.getInstanceState(pin);
      if (Pin.FACTORY.isInputPin(pin)) {
        final com.cburch.logisim.data.Value newVal = superState.getPortValue(i);
        final com.cburch.logisim.data.Value oldVal = Pin.FACTORY.getValue(pinState);
        if (!newVal.equals(oldVal)) {
          Pin.FACTORY.setValue(pinState, newVal);
          Pin.FACTORY.propagate(pinState);
        }
      } else { // it is output-only
        final com.cburch.logisim.data.Value val = pinState.getPortValue(0);
        superState.setPort(i, val, 1);
      }
    }
  }

  @Override
  public void paintIcon(InstancePainter painter) {
    final java.awt.Graphics2D g2 = (Graphics2D) painter.getGraphics().create();
    final com.cburch.logisim.circuit.CircuitAttributes attrs = (CircuitAttributes) painter.getAttributeSet();
    if (attrs.getValue(CircuitAttributes.APPEARANCE_ATTR).equals(CircuitAttributes.APPEAR_CLASSIC))
      paintClasicIcon(g2);
    else if (attrs
        .getValue(CircuitAttributes.APPEARANCE_ATTR)
        .equals(CircuitAttributes.APPEAR_FPGA)) paintHCIcon(g2);
    else paintEvolutionIcon(g2);
    g2.dispose();
  }

  public static void paintClasicIcon(Graphics2D g2) {
    g2.setStroke(new BasicStroke(AppPreferences.getScaled(2)));
    g2.setColor(Color.GRAY);
    g2.drawArc(
        AppPreferences.getScaled(6),
        AppPreferences.getScaled(-2),
        AppPreferences.getScaled(4),
        AppPreferences.getScaled(6),
        180,
        180);
    g2.setColor(Color.BLACK);
    g2.drawRect(
        AppPreferences.getScaled(2),
        AppPreferences.getScaled(1),
        AppPreferences.getScaled(12),
        AppPreferences.getScaled(14));
    final int wh = AppPreferences.getScaled(3);
    for (int y = 0; y < 3; y++) {
      if (y == 1) g2.setColor(Value.trueColor);
      else g2.setColor(Value.falseColor);
      g2.fillOval(AppPreferences.getScaled(1), AppPreferences.getScaled(y * 4 + 3), wh, wh);
      if (y < 2) {
        g2.setColor(Value.unknownColor);
        g2.fillOval(AppPreferences.getScaled(12), AppPreferences.getScaled(y * 4 + 3), wh, wh);
      }
    }
  }

  public static void paintHCIcon(Graphics2D g2) {
    g2.setStroke(new BasicStroke(AppPreferences.getScaled(2)));
    g2.setColor(Color.BLACK);
    g2.drawRect(
        AppPreferences.getScaled(1),
        AppPreferences.getScaled(1),
        AppPreferences.getScaled(14),
        AppPreferences.getScaled(14));
    final java.awt.Font f = g2.getFont().deriveFont((float) AppPreferences.getIconSize() / 4);
    final java.awt.font.TextLayout l = new TextLayout("main", f, g2.getFontRenderContext());
    l.draw(
        g2,
        (float) (AppPreferences.getIconSize() / 2 - l.getBounds().getCenterX()),
        (float) (AppPreferences.getIconSize() / 4 - l.getBounds().getCenterY()));
    final int wh = AppPreferences.getScaled(3);
    for (int y = 1; y < 3; y++) {
      if (y == 1) g2.setColor(Value.trueColor);
      else g2.setColor(Value.falseColor);
      g2.fillOval(AppPreferences.getScaled(0), AppPreferences.getScaled(y * 4 + 3), wh, wh);
      if (y < 2) {
        g2.setColor(Value.unknownColor);
        g2.fillOval(AppPreferences.getScaled(13), AppPreferences.getScaled(y * 4 + 3), wh, wh);
      }
    }
  }

  public static void paintEvolutionIcon(Graphics2D g2) {
    g2.setStroke(new BasicStroke(AppPreferences.getScaled(2)));
    g2.setColor(Color.BLACK);
    g2.drawRect(
        AppPreferences.getScaled(2), 0, AppPreferences.getScaled(12), AppPreferences.getScaled(16));
    g2.fillRect(
        AppPreferences.getScaled(2),
        (3 * AppPreferences.getIconSize()) / 4,
        AppPreferences.getScaled(12),
        AppPreferences.getIconSize() / 4);
    for (int y = 0; y < 3; y++) {
      g2.drawLine(
          0,
          AppPreferences.getScaled(y * 4 + 2),
          AppPreferences.getScaled(2),
          AppPreferences.getScaled(y * 4 + 2));
      if (y < 2)
        g2.drawLine(
            AppPreferences.getScaled(13),
            AppPreferences.getScaled(y * 4 + 2),
            AppPreferences.getScaled(15),
            AppPreferences.getScaled(y * 4 + 2));
    }
    g2.setColor(Color.WHITE);
    final java.awt.Font f = g2.getFont().deriveFont((float) AppPreferences.getIconSize() / 4);
    final java.awt.font.TextLayout l = new TextLayout("main", f, g2.getFontRenderContext());
    l.draw(g2,
            (float) (AppPreferences.getIconSize() / 2 - l.getBounds().getCenterX()),
            (float) ((7 * AppPreferences.getIconSize()) / 8 - l.getBounds().getCenterY()));
  }

  @Override
  public void removeComponent(Circuit circ, Component c, CircuitState state) {
    getSubcircuit().removeComponent(c);
  }
}
