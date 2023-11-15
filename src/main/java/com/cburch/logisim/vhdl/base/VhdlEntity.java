/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.vhdl.base;

import static com.cburch.logisim.vhdl.Strings.S;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.icons.ArithmeticIcon;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.StringUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RUntainted;

public class VhdlEntity extends InstanceFactory implements HdlModelListener {

  static final Logger logger = LoggerFactory.getLogger(VhdlEntity.class);
  static final Attribute<String> nameAttr = Attributes.forString("vhdlEntity", S.getter("vhdlEntityName"));
  static final ArithmeticIcon icon = new ArithmeticIcon("VHDL");

  static final int WIDTH = 140;
  static final int HEIGHT = 40;
  static final int PORT_GAP = 10;

  static final int X_PADDING = 5;

  private final VhdlContent content;
  private final ArrayList<Instance> myInstances;

  public VhdlEntity(VhdlContent content) {
    super("", null, new VhdlHdlGeneratorFactory(), true);
    this.content = content;
    this.content.addHdlModelListener(this);
    this.setIcon(icon);
    icon.setInvalid(!content.isValid());
    setFacingAttribute(StdAttr.FACING);
    appearance = VhdlAppearance.create(getPins(), getName(), StdAttr.APPEAR_EVOLUTION);
    myInstances = new ArrayList<>();
  }

  public void setSimName(AttributeSet attrs, String sName) {
    if (attrs == null) return;
    final com.cburch.logisim.vhdl.base.VhdlEntityAttributes atrs = (VhdlEntityAttributes) attrs;
    final java.lang.String label = (!attrs.getValue(StdAttr.LABEL).equals("")) ? getHDLTopName(attrs) : sName;
    if (atrs.containsAttribute(VhdlSimConstants.SIM_NAME_ATTR))
      atrs.setValue(VhdlSimConstants.SIM_NAME_ATTR, label);
  }

  public String getSimName(AttributeSet attrs) {
    if (attrs == null) return null;
    final com.cburch.logisim.vhdl.base.VhdlEntityAttributes atrs = (VhdlEntityAttributes) attrs;
    return atrs.getValue(VhdlSimConstants.SIM_NAME_ATTR);
  }

  @Override
  public String getName() {
    if (content == null) return "VHDL Entity";
    else return content.getName();
  }

  @Override
  public StringGetter getDisplayGetter() {
    if (content == null) return S.getter("vhdlComponent");
    else return StringUtil.constantGetter(content.getName());
  }

  public VhdlContent getContent() {
    return content;
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    final com.cburch.logisim.vhdl.base.VhdlEntityAttributes attrs = (VhdlEntityAttributes) instance.getAttributeSet();
    attrs.setInstance(instance);
    instance.addAttributeListener();
    updatePorts(instance);
    if (!myInstances.contains(instance)) myInstances.add(instance);
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new VhdlEntityAttributes(content);
  }

  @Override
  public String getHDLName(AttributeSet attrs) {
    return content.getName().toLowerCase();
  }

  @Override
  public String getHDLTopName(AttributeSet attrs) {
    java.lang.String label = "";
    if (!attrs.getValue(StdAttr.LABEL).equals(""))
      label = "_" + attrs.getValue(StdAttr.LABEL).toLowerCase();
    return getHDLName(attrs) + label;
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    if (appearance == null) return Bounds.create(0, 0, 100, 100);
    final com.cburch.logisim.data.Direction facing = attrs.getValue(StdAttr.FACING);
    return appearance.getOffsetBounds().rotate(Direction.EAST, facing, 0, 0);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.FACING) {
      updatePorts(instance);
    } else if (attr == StdAttr.APPEARANCE) {
      for (final com.cburch.logisim.instance.Instance j : myInstances) {
        updatePorts(j);
      }
    }
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    final com.cburch.logisim.vhdl.base.VhdlEntityAttributes attrs = (VhdlEntityAttributes) painter.getAttributeSet();
    final com.cburch.logisim.data.Direction facing = attrs.getFacing();
    final java.awt.Graphics gfx = painter.getGraphics();

    final com.cburch.logisim.data.Location loc = painter.getLocation();
    gfx.translate(loc.getX(), loc.getY());
    appearance.paintSubcircuit(painter, gfx, facing);
    gfx.translate(-loc.getX(), -loc.getY());

    final java.lang.String label = painter.getAttributeValue(StdAttr.LABEL);
    if (label != null && painter.getAttributeValue(StdAttr.LABEL_VISIBILITY)) {
      final com.cburch.logisim.data.Bounds bds = painter.getBounds();
      final java.awt.Font oldFont = gfx.getFont();
      final java.awt.Color color = gfx.getColor();
      gfx.setFont(painter.getAttributeValue(StdAttr.LABEL_FONT));
      gfx.setColor(StdAttr.DEFAULT_LABEL_COLOR);
      GraphicsUtil.drawCenteredText(gfx, label, bds.getX() + bds.getWidth() / 2, bds.getY() - gfx.getFont().getSize());
      gfx.setFont(oldFont);
      gfx.setColor(color);
    }
    painter.drawPorts();
  }

  /**
   * Propagate signals through the VHDL component. Logisim doesn't have a VHDL simulation tool. So
   * we need to use an external tool. We send signals to Questasim/Modelsim through a socket and a
   * tcl binder. Then, a simulation step is done and the tcl server sends the output signals back to
   * Logisim. Then we can set the VHDL component output properly.
   *
   * <p>This can be done only if Logisim could connect to the tcl server (socket). This is done in
   * Simulation.java.
   */
  @Override
  public void propagate(InstanceState state) {

    if (state.getProject().getVhdlSimulator().isEnabled()
        && state.getProject().getVhdlSimulator().isRunning()) {

      final com.cburch.logisim.vhdl.sim.VhdlSimulatorTop vhdlSimulator = state.getProject().getVhdlSimulator();

      for (final com.cburch.logisim.instance.Port singlePort : state.getInstance().getPorts()) {
        final int index = state.getPortIndex(singlePort);
        final com.cburch.logisim.data.Value val = state.getPortValue(index);
        final java.lang.String vhdlEntityName = getSimName(state.getAttributeSet());

        String message =
            singlePort.getType()
                + ":"
                + vhdlEntityName
                + "_"
                + singlePort.getToolTip()
                + ":"
                + val.toBinaryString()
                + ":"
                + index;

        vhdlSimulator.send(message);
      }

      vhdlSimulator.send("sync");

      /* Get response from tcl server */
      String serverResponse;
      while ((serverResponse = vhdlSimulator.receive()) != null
          && serverResponse.length() > 0
          && !serverResponse.equals("sync")) {

        final java.lang.String[] parameters = serverResponse.split(":");
        final java.lang.String busValue = parameters[1];
        final com.cburch.logisim.data.Value[] vectorValues = new Value[busValue.length()];

        int idx = busValue.length() - 1;
        for (final char bit : busValue.toCharArray()) {

          try {
            vectorValues[idx] = switch (Character.getNumericValue(bit)) {
              case 0 -> Value.FALSE;
              case 1 -> Value.TRUE;
              default -> Value.UNKNOWN;
            };
          } catch (NumberFormatException e) {
            vectorValues[idx] = Value.UNKNOWN;
          }
          idx--;
        }

        state.setPort(Integer.parseInt(parameters[2]), Value.create(vectorValues), 1);
      }

      /* VhdlSimulation stopped/disabled */
    } else {

      for (final com.cburch.logisim.instance.Port port : state.getInstance().getPorts()) {
        final int index = state.getPortIndex(port);

        /* If it is an output */
        if (port.getType() == 2) {
          final com.cburch.logisim.data.Value[] vectorValues = new Value[port.getFixedBitWidth().getWidth()];
          for (int k = 0; k < port.getFixedBitWidth().getWidth(); k++) {
            vectorValues[k] = Value.UNKNOWN;
          }

          state.setPort(index, Value.create(vectorValues), 1);
        }
      }

      // FIXME: hardcoded string
      throw new UnsupportedOperationException(
          "VHDL component simulation is not supported. This could be because there is no Questasim/Modelsim simulation server running.");     // FIXME: hardcoded string
    }
  }

  /**
   * Save the VHDL entity in a file. The file is used for VHDL components simulation by
   * QUestasim/Modelsim
   */
  public void saveFile(AttributeSet attrs) {

    PrintWriter writer;
    try {
      writer =
          new PrintWriter(VhdlSimConstants.SIM_SRC_PATH + getSimName(attrs) + ".vhdl",
              StandardCharsets.UTF_8);

      String content = this.content.getContent();

      content = content.replaceAll("(?i)" + getHDLName(attrs), getSimName(attrs));

      writer.print(content);
      writer.close();
    } catch (IOException e) {
      logger.error("Could not create VHDL file: {}", e.getMessage());     // FIXME: hardcoded string
      e.printStackTrace();
    }
  }

  private VhdlAppearance appearance;

  private ArrayList<Instance> getPins() {
    final java.util.ArrayList<com.cburch.logisim.instance.Instance> pins = new ArrayList<Instance>();
    int yPos = 0;
    for (final com.cburch.logisim.vhdl.base.VhdlParser.PortDescription port : content.getPorts()) {
      final com.cburch.logisim.data.AttributeSet attr = Pin.FACTORY.createAttributeSet();
      attr.setValue(StdAttr.LABEL, port.getName());
      attr.setValue(Pin.ATTR_TYPE, !port.getType().equals(Port.INPUT));
      attr.setValue(StdAttr.FACING, !port.getType().equals(Port.INPUT) ? Direction.WEST : Direction.EAST);
      attr.setValue(StdAttr.WIDTH, port.getWidth());
      final com.cburch.logisim.instance.InstanceComponent component = (InstanceComponent) Pin.FACTORY.createComponent(Location.create(100, yPos, true), attr);
      pins.add(component.getInstance());
      yPos += 10;
    }
    return pins;
  }

  void updatePorts(Instance instance) {
    AttributeOption style = instance.getAttributeValue(StdAttr.APPEARANCE);
    appearance = VhdlAppearance.create(getPins(), getName(), style);

    Direction facing = instance.getAttributeValue(StdAttr.FACING);
    Map<Location, Instance> portLocs = appearance.getPortOffsets(facing);

    Port[] ports = new Port[portLocs.size()];
    int i = -1;
    for (Map.Entry<Location, Instance> portLoc : portLocs.entrySet()) {
      i++;
      Location loc = portLoc.getKey();
      Instance pin = portLoc.getValue();
      String type = Pin.FACTORY.isInputPin(pin) ? Port.INPUT : Port.OUTPUT;
      BitWidth width = pin.getAttributeValue(StdAttr.WIDTH);
      ports[i] = new Port(loc.getX(), loc.getY(), type, width);

      String label = pin.getAttributeValue(StdAttr.LABEL);
      if (label != null && label.length() > 0) {
        ports[i].setToolTip(StringUtil.constantGetter(label));
      }
    }
    instance.setPorts(ports);
    instance.recomputeBounds();
  }

  @Override
  public void contentSet(HdlModel source) {
    icon.setInvalid(!content.isValid());
  }

  private final WeakHashMap<Component, Circuit> circuitsUsingThis = new WeakHashMap<>();

  public Collection<Circuit> getCircuitsUsingThis() {
    return circuitsUsingThis.values();
  }

  public void addCircuitUsing(Component comp, Circuit circ) {
    circuitsUsingThis.put(comp, circ);
  }

  public void removeCircuitUsing(Component comp) {
    circuitsUsingThis.remove(comp);
  }

  @Override
  public void removeComponent(Circuit circ, Component c, CircuitState state) {
    removeCircuitUsing(c);
  }
}
