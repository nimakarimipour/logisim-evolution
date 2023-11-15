/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.gates;

import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.VariableList;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.SplitterAttributes;
import com.cburch.logisim.circuit.SplitterFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Constant;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.ProbeAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RUntainted;

public class CircuitBuilder {

  private static final int SPINE_DISTANCE = 10;
  private static final int BUS_SPINE_TO_WIRE_SPINE_DISTANCE = 20;
  private static final int MINIMAL_PIN_DISTANCE = 30;
  private static final int SPLITTER_HEIGHT = 20;
  private static final int TOP_BORDER = 40; // minimal value due to constants
  private static final int INVERTER_WIDTH = 30;
  private static final int NAND_WIDTH = 40;
  private static final int GATE_HEIGHT = 40;

  private static class CompareYs implements Comparator<Location> {
    @Override
    public int compare(Location a, Location b) {
      return a.getY() - b.getY();
    }
  }

  private static class InputData {
    int startX;
    int startY;
    int pinX;
    private final List<String> names = new ArrayList<>();
    private final Map<String, SingleInput> inputs = new HashMap<>();
    private final Map<String, SingleInput> invertedInputs = new HashMap<>();

    InputData() {}

    public void addInput(String name, SingleInput info) {
      inputs.put(name, info);
      names.add(name);
    }

    public int createInvertedLocs(int spineX) {
      int cur = spineX + SPINE_DISTANCE;
      addInput("0", new SingleInput(cur)); // Constant zero line
      cur += 20;
      addInput("1", new SingleInput(cur)); // Constant one line
      cur += NAND_WIDTH + 20;
      for (int i = 0; i < getNrOfInputs(); i++) {
        invertedInputs.put(names.get(i), new SingleInput(cur));
        cur += SPINE_DISTANCE;
      }
      return cur;
    }

    public int getInverterXLoc() {
      final boolean hasOne = !inputs.get("1").ys.isEmpty();
      final boolean hasZero = !inputs.get("0").ys.isEmpty();
      if (hasOne) return invertedInputs.get(names.get(0)).spineX - 10;
      if (hasZero) return invertedInputs.get(names.get(0)).spineX - 20;
      else return invertedInputs.get(names.get(0)).spineX - 20 - SPINE_DISTANCE;
    }

    public boolean hasInvertedConnections(String name) {
      final com.cburch.logisim.std.gates.CircuitBuilder.SingleInput inp = invertedInputs.get(name);
      if (inp == null) return false;
      return !inp.ys.isEmpty();
    }

    int getNrOfInputs() {
      return names.size();
    }

    int getSpineX(String input, boolean inverted) {
      final com.cburch.logisim.std.gates.CircuitBuilder.SingleInput data = (inverted) ? invertedInputs.get(input) : inputs.get(input);
      return data.spineX;
    }

    int getStartX() {
      return startX;
    }

    int getStartY() {
      return startY;
    }

    int getPinX() {
      return pinX;
    }

    public int getInverterHeight() {
      int nr = names.size();
      if (names.contains("0")) nr--;
      if (names.contains("1")) nr--;
      return nr * GATE_HEIGHT;
    }

    public SingleInput getInputLocs(String Name, boolean inverted) {
      return inverted ? invertedInputs.get(Name) : inputs.get(Name);
    }

    public String getInputName(int index) {
      if ((index < 0) || (index >= getNrOfInputs())) return null;
      return names.get(index);
    }

    void registerConnection(String input, Location loc, boolean inverted) {
      final com.cburch.logisim.std.gates.CircuitBuilder.SingleInput data = (inverted) ? invertedInputs.get(input) : inputs.get(input);
      data.ys.add(loc);
    }
  }

  private static class Layout {
    // initialized by parent
    int y; // top edge relative to parent's top edge
    // (or edge corresponding to input)

    // initialized by self
    final int width;
    final int height;
    final ComponentFactory factory;
    final AttributeSet attrs;
    final int outputY; // where output is relative to my top edge
    final int subX; // where right edge of sublayouts should be relative to my
    // left edge
    final Layout[] subLayouts;
    String inputName; // for references directly to inputs
    boolean inverted;

    Layout(
        int width,
        int height,
        int outputY,
        ComponentFactory factory,
        AttributeSet attrs,
        Layout[] subLayouts,
        int subX) {
      this.width = width;
      this.height = roundUp(height);
      this.outputY = outputY;
      this.factory = factory;
      this.attrs = attrs;
      this.subLayouts = subLayouts;
      this.subX = subX;
      this.inputName = null;
    }

    Layout(String inputName, boolean inverted) {
      this(0, 0, 0, null, null, null, 0);
      this.inputName = inputName;
      this.inverted = inverted;
    }
  }

  private static class SingleInput {
    final int spineX;
    int spineY;
    final ArrayList<Location> ys = new ArrayList<>();

    SingleInput(int spineX) {
      this.spineX = spineX;
    }

    SingleInput(int spineX, int spineY) {
      this.spineX = spineX;
      this.spineY = spineY;
    }
  }

  public static CircuitMutation build(Circuit destCirc, AnalyzerModel model, boolean twoInputs, boolean useNands) {
    final com.cburch.logisim.circuit.CircuitMutation result = new CircuitMutation(destCirc);
    result.clear();

    final com.cburch.logisim.std.gates.CircuitBuilder.Layout[] layouts = new Layout[model.getOutputs().bits.size()];
    int maxWidth = 0;
    for (int i = 0; i < layouts.length; i++) {
      final java.lang.String output = model.getOutputs().bits.get(i);
      final com.cburch.logisim.analyze.model.Expression expr = model.getOutputExpressions().getExpression(output);
      final com.cburch.logisim.std.gates.CircuitDetermination det = CircuitDetermination.create(expr);
      if (det != null) {
        if (twoInputs) det.convertToTwoInputs();
        if (useNands) det.convertToNands();
        det.repair();
        layouts[i] = layoutGates(det);
        maxWidth = Math.max(maxWidth, layouts[i].width);
      } else {
        layouts[i] = null;
      }
    }

    final com.cburch.logisim.std.gates.CircuitBuilder.InputData inputData = computeInputData(model);
    final com.cburch.logisim.std.gates.CircuitBuilder.InputData outputData = new InputData();
    outputData.startY = inputData.startY;
    final int x = inputData.getStartX();
    int y = inputData.getStartY() + inputData.getInverterHeight();
    final int outputX = x + maxWidth + 20;
    for (int i = 0; i < layouts.length; i++) {
      final java.lang.String outputName = model.getOutputs().bits.get(i);
      final com.cburch.logisim.std.gates.CircuitBuilder.Layout layout = layouts[i];
      Location output;
      int height;
      if (layout == null) {
        outputData.addInput(outputName, null);
        height = -10;
      } else {
        int dy = 0;
        if (layout.outputY < 20) dy = 20 - layout.outputY;
        height = Math.max(dy + layout.height, 40);
        output = Location.create(outputX, y + dy + layout.outputY, true);
        outputData.addInput(outputName, new SingleInput(outputX, y + dy + layout.outputY));
        placeComponents(result, layouts[i], x, y + dy, inputData, output);
      }
      y += height + 10;
    }
    placeInputs(model, result, inputData, useNands);
    placeOutputs(model, result, outputData);
    return result;
  }

  //
  // computeInputData
  //
  private static InputData computeInputData(AnalyzerModel model) {
    final com.cburch.logisim.std.gates.CircuitBuilder.InputData ret = new InputData();
    final com.cburch.logisim.analyze.model.VariableList inputs = model.getInputs();
    int nameLength = 1;
    int busLength = 1;
    int nrOfBusses = 0;
    for (int i = 0; i < inputs.vars.size(); i++) {
      if (inputs.vars.get(i).name.length() > nameLength)
        nameLength = inputs.vars.get(i).name.length();
      if (inputs.vars.get(i).width > busLength) {
        busLength = inputs.vars.get(i).width;
      }
      if (inputs.vars.get(i).width > 1) nrOfBusses++;
    }
    int spineX = 100 + nameLength * 10 + (busLength - 1) * 10;
    ret.pinX = spineX - 10;
    if (nrOfBusses > 0) spineX += nrOfBusses * SPINE_DISTANCE + BUS_SPINE_TO_WIRE_SPINE_DISTANCE;
    int cnt = 0;
    for (int i = 0; i < inputs.vars.size(); i++) {
      final com.cburch.logisim.analyze.model.Var inp = inputs.vars.get(i);
      if (inp.width == 1) {
        final java.lang.String name = inputs.bits.get(cnt++);
        ret.addInput(name, new SingleInput(spineX));
        spineX += SPINE_DISTANCE;
      } else {
        for (int idx = inp.width - 1; idx >= 0; idx--) {
          final java.lang.String name = inputs.bits.get(cnt++);
          ret.addInput(name, new SingleInput(spineX));
          spineX += SPINE_DISTANCE;
        }
      }
    }
    /* do the same for the inverted inputs */
    spineX = ret.createInvertedLocs(spineX);
    spineX += SPINE_DISTANCE;
    final com.cburch.logisim.analyze.model.VariableList outputs = model.getOutputs();
    int nrOutBusses = 0;
    for (int i = 0; i < outputs.vars.size(); i++) if (outputs.vars.get(i).width > 1) nrOutBusses++;
    nrOfBusses = Math.max(nrOfBusses, nrOutBusses);
    ret.startX = spineX;
    ret.startY = TOP_BORDER + nrOfBusses * SPLITTER_HEIGHT + (nrOfBusses > 0 ? 10 : 0);
    return ret;
  }

  //
  // layoutGates
  //
  private static Layout layoutGates(CircuitDetermination det) {
    return layoutGatesSub(det);
  }

  private static Layout layoutGatesSub(CircuitDetermination det) {
    if (det instanceof CircuitDetermination.Input input) {
      return new Layout(input.getName(), input.isInvertedVersion());
    } else if (det instanceof CircuitDetermination.Value value) {
      if ((value.getValue() == 1) || (value.getValue() == 0)) {
        return new Layout(Integer.toString(value.getValue()), false);
      }
      final com.cburch.logisim.instance.InstanceFactory factory = Constant.FACTORY;
      final com.cburch.logisim.data.AttributeSet attrs = factory.createAttributeSet();
      attrs.setValue(Constant.ATTR_VALUE, (long) value.getValue());
      final com.cburch.logisim.data.Bounds bds = factory.getOffsetBounds(attrs);
      return new Layout(bds.getWidth(), bds.getHeight(), -bds.getY(), factory, attrs, new Layout[0], 0);
    }

    // We know det is a Gate. Determine sublayouts.
    final com.cburch.logisim.std.gates.CircuitDetermination.Gate gate = (CircuitDetermination.Gate) det;
    final com.cburch.logisim.comp.ComponentFactory factory = gate.getFactory();
    final java.util.ArrayList<com.cburch.logisim.std.gates.CircuitDetermination> inputs = gate.getInputs();

    // Handle a NOT implemented with a NAND as a special case
    if (gate.isNandNot()) {
      final com.cburch.logisim.std.gates.CircuitDetermination subDet = inputs.get(0);
      if (!(subDet instanceof CircuitDetermination.Input)
          && !(subDet instanceof CircuitDetermination.Value)) {
        Layout[] sub = new Layout[1];
        sub[0] = layoutGatesSub(subDet);
        sub[0].y = 0;

        final com.cburch.logisim.data.AttributeSet attrs = factory.createAttributeSet();
        attrs.setValue(GateAttributes.ATTR_SIZE, GateAttributes.SIZE_NARROW);
        attrs.setValue(GateAttributes.ATTR_INPUTS, 2);

        // determine layout's width
        final com.cburch.logisim.data.Bounds bds = factory.getOffsetBounds(attrs);
        int betweenWidth = 40;
        if (sub[0].width == 0) betweenWidth = 0;
        final int width = sub[0].width + betweenWidth + bds.getWidth();

        // determine outputY and layout's height.
        int outputY = sub[0].y + sub[0].outputY;
        int height = sub[0].height;
        final int minOutputY = roundUp(-bds.getY());
        if (minOutputY > outputY) {
          // we have to shift everything down because otherwise
          // the component will peek over the rectangle's top.
          final int dy = minOutputY - outputY;
          sub[0].y += dy;
          height += dy;
          outputY += dy;
        }
        final int minHeight = outputY + bds.getY() + bds.getHeight();
        if (minHeight > height) height = minHeight;

        // ok; create and return the layout.
        return new Layout(width, height, outputY, factory, attrs, sub, sub[0].width);
      }
    }

    final com.cburch.logisim.std.gates.CircuitBuilder.Layout[] sub = new Layout[inputs.size()];
    int subWidth = 0; // maximum width of sublayouts
    int subHeight = 0; // total height of sublayouts
    for (int i = 0; i < sub.length; i++) {
      sub[i] = layoutGatesSub(inputs.get(i));
      if (sub.length % 2 == 0
          && i == (sub.length + 1) / 2
          && sub[i - 1].height + sub[i].height == 0) {
        // if there are an even number of inputs, then there is a
        // 20-tall gap between the middle two inputs. Ensure the two
        // middle inputs are at least 20 pixels apart.
        subHeight += 10;
      }
      sub[i].y = subHeight;
      subWidth = Math.max(subWidth, sub[i].width);
      subHeight += sub[i].height + 10;
    }
    subHeight -= 10;

    final com.cburch.logisim.data.AttributeSet attrs = factory.createAttributeSet();
    if (factory == NotGate.FACTORY) {
      attrs.setValue(NotGate.ATTR_SIZE, NotGate.SIZE_NARROW);
    } else {
      attrs.setValue(GateAttributes.ATTR_SIZE, GateAttributes.SIZE_NARROW);

      int ins = sub.length;
      attrs.setValue(GateAttributes.ATTR_INPUTS, ins);
    }

    // determine layout's width
    final com.cburch.logisim.data.Bounds bds = factory.getOffsetBounds(attrs);
    int betweenWidth = 40 + 10 * (sub.length / 2 - 1);
    if (sub.length == 1) betweenWidth = 20;
    if (subWidth == 0) betweenWidth = 0;
    final int width = subWidth + betweenWidth + bds.getWidth();

    // determine outputY and layout's height.
    int outputY;
    if (sub.length % 2 == 1) { // odd number - match the middle input
      int i = (sub.length - 1) / 2;
      outputY = sub[i].y + sub[i].outputY;
    } else { // even number - halfway between middle two inputs
      int i0 = (sub.length / 2) - 1;
      int i1 = (sub.length / 2);
      int o0 = sub[i0].y + sub[i0].outputY;
      int o1 = sub[i1].y + sub[i1].outputY;
      outputY = roundDown((o0 + o1) / 2);
    }
    int height = subHeight;
    int minOutputY = roundUp(-bds.getY());
    if (minOutputY > outputY) {
      // we have to shift everything down because otherwise
      // the component will peek over the rectangle's top.
      int dy = minOutputY - outputY;
      for (final com.cburch.logisim.std.gates.CircuitBuilder.Layout layout : sub)
        layout.y += dy;
      height += dy;
      outputY += dy;
    }
    int minHeight = outputY + bds.getY() + bds.getHeight();
    if (minHeight > height) height = minHeight;

    // ok; create and return the layout.
    return new Layout(width, height, outputY, factory, attrs, sub, subWidth);
  }

  //
  // placeComponents
  //
  /**
   * @param result the circuit where to place the components.
   * @param layout the layout specifying the gates to place there.
   * @param x the left edge of where the layout should be placed.
   * @param y the top edge of where the layout should be placed.
   * @param inputData information about how to reach inputs.
   * @param output a point to which the output should be connected.
   */
  private static void placeComponents(
      CircuitMutation result, Layout layout, int x, int y, InputData inputData, Location output) {
    if (layout.inputName != null) {
      final int inputX = inputData.getSpineX(layout.inputName, layout.inverted);
      final com.cburch.logisim.data.Location input = Location.create(inputX, output.getY(), true);
      inputData.registerConnection(layout.inputName, input, layout.inverted);
      result.add(Wire.create(input, output));
      return;
    }

    final com.cburch.logisim.data.Location compOutput = Location.create(x + layout.width, output.getY(), true);
    final com.cburch.logisim.comp.Component parent = layout.factory.createComponent(compOutput, layout.attrs);
    result.add(parent);
    if (!compOutput.equals(output)) {
      result.add(Wire.create(compOutput, output));
    }

    // handle a NOT gate pattern implemented with NAND as a special case
    if (layout.factory == NandGate.FACTORY
        && layout.subLayouts.length == 1
        && layout.subLayouts[0].inputName == null) {
      final com.cburch.logisim.std.gates.CircuitBuilder.Layout sub = layout.subLayouts[0];

      final com.cburch.logisim.data.Location input0 = parent.getEnd(1).getLocation();
      final com.cburch.logisim.data.Location input1 = parent.getEnd(2).getLocation();

      int midX = input0.getX() - 20;
      final com.cburch.logisim.data.Location subOutput = Location.create(midX, output.getY(), true);
      final com.cburch.logisim.data.Location midInput0 = Location.create(midX, input0.getY(), true);
      final com.cburch.logisim.data.Location midInput1 = Location.create(midX, input1.getY(), true);
      result.add(Wire.create(subOutput, midInput0));
      result.add(Wire.create(midInput0, input0));
      result.add(Wire.create(subOutput, midInput1));
      result.add(Wire.create(midInput1, input1));

      final int subX = x + layout.subX - sub.width;
      placeComponents(result, sub, subX, y + sub.y, inputData, subOutput);
      return;
    }

    if (layout.subLayouts.length == parent.getEnds().size() - 2) {
      final int index = layout.subLayouts.length / 2 + 1;
      final com.cburch.logisim.comp.ComponentFactory factory = parent.getFactory();
      if (factory instanceof AbstractGate gate) {
        final com.cburch.logisim.data.Value val = gate.getIdentity();
        final long valLong = val.toLongValue();
        final com.cburch.logisim.data.Location loc = parent.getEnd(index).getLocation();
        final com.cburch.logisim.data.AttributeSet attrs = Constant.FACTORY.createAttributeSet();
        attrs.setValue(Constant.ATTR_VALUE, valLong);
        result.add(Constant.FACTORY.createComponent(loc, attrs));
      }
    }

    for (int i = 0; i < layout.subLayouts.length; i++) {
      final com.cburch.logisim.std.gates.CircuitBuilder.Layout sub = layout.subLayouts[i];

      final int inputIndex = i + 1;
      Location subDest = parent.getEnd(inputIndex).getLocation();

      int subOutputY = y + sub.y + sub.outputY;
      if (sub.inputName != null) {
        final int destY = subDest.getY();
        if (i == 0 && destY < subOutputY
            || i == layout.subLayouts.length - 1 && destY > subOutputY) {
          subOutputY = destY;
        }
      }

      Location subOutput;
      final int numSubs = layout.subLayouts.length;
      if (subOutputY == subDest.getY()) {
        subOutput = subDest;
      } else {
        int back;
        if (i < numSubs / 2) {
          if (subOutputY < subDest.getY()) { // bending upward
            back = i;
          } else {
            back = ((numSubs - 1) / 2) - i;
          }
        } else {
          if (subOutputY > subDest.getY()) { // bending downward
            back = numSubs - 1 - i;
          } else {
            back = i - (numSubs / 2);
          }
        }
        int subOutputX = subDest.getX() - 20 - 10 * back;
        subOutput = Location.create(subOutputX, subOutputY, true);
        final com.cburch.logisim.data.Location mid = Location.create(subOutputX, subDest.getY(), true);
        result.add(Wire.create(subOutput, mid));
        result.add(Wire.create(mid, subDest));
      }

      final int subX = x + layout.subX - sub.width;
      final int subY = y + sub.y;
      placeComponents(result, sub, subX, subY, inputData, subOutput);
    }
  }

  private static void placeInputInverters(CircuitMutation result, InputData inputData, boolean useNands) {
    int invPosY = inputData.getStartY() + GATE_HEIGHT / 2;
    for (int i = 0; i < inputData.getNrOfInputs(); i++) {
      final java.lang.String inputName = inputData.getInputName(i);
      if (inputData.hasInvertedConnections(inputName)) {
        if (useNands) {
          final com.cburch.logisim.std.gates.NandGate fact = NandGate.FACTORY;
          final com.cburch.logisim.data.AttributeSet attrs = fact.createAttributeSet();
          attrs.setValue(GateAttributes.ATTR_SIZE, GateAttributes.SIZE_NARROW);
          com.cburch.logisim.data.Location ipLoc1 = Location.create(inputData.getSpineX("1", false), invPosY - 10, true);
          inputData.registerConnection("1", ipLoc1, false);
          final com.cburch.logisim.data.Location Ploc = Location.create(inputData.getInverterXLoc(), invPosY, true);
          result.add(fact.createComponent(Ploc, attrs));
          com.cburch.logisim.data.Location ipLoc2 = Location.create(inputData.getInverterXLoc() - NAND_WIDTH, invPosY - 10, true);
          result.add(Wire.create(ipLoc1, ipLoc2));
          ipLoc1 = Location.create(inputData.getSpineX(inputName, false), invPosY + 10, true);
          ipLoc2 = Location.create(inputData.getInverterXLoc() - NAND_WIDTH, invPosY + 10, true);
          result.add(Wire.create(ipLoc1, ipLoc2));
          inputData.registerConnection(inputName, ipLoc1, false);
          final com.cburch.logisim.data.Location IPloc3 = Location.create(inputData.getSpineX(inputName, true), invPosY, true);
          result.add(Wire.create(Ploc, IPloc3));
          inputData.registerConnection(inputName, IPloc3, true);
        } else {
          final com.cburch.logisim.instance.InstanceFactory fact = NotGate.FACTORY;
          final com.cburch.logisim.data.AttributeSet attrs = fact.createAttributeSet();
          final com.cburch.logisim.data.Location Ploc = Location.create(inputData.getInverterXLoc(), invPosY, true);
          result.add(fact.createComponent(Ploc, attrs));
          final com.cburch.logisim.data.Location IPloc1 = Location.create(inputData.getSpineX(inputName, false), invPosY, true);
          final com.cburch.logisim.data.Location IPloc2 = Location.create(inputData.getInverterXLoc() - INVERTER_WIDTH, invPosY, true);
          result.add(Wire.create(IPloc1, IPloc2));
          inputData.registerConnection(inputName, IPloc1, false);
          final com.cburch.logisim.data.Location IPloc3 = Location.create(inputData.getSpineX(inputName, true), invPosY, true);
          result.add(Wire.create(Ploc, IPloc3));
          inputData.registerConnection(inputName, IPloc3, true);
        }
        /* Here we draw the inverted spine */
        createSpine(result, inputData.getInputLocs(inputName, true).ys, new CompareYs());
        invPosY += GATE_HEIGHT;
      }
    }
  }

  private static void placeConstants(CircuitMutation result, InputData inputData) {
    final com.cburch.logisim.instance.InstanceFactory fact = Constant.FACTORY;
    if (!inputData.getInputLocs("0", false).ys.isEmpty()) {
      final com.cburch.logisim.data.AttributeSet attrs = fact.createAttributeSet();
      attrs.setValue(StdAttr.FACING, Direction.SOUTH);
      attrs.setValue(Constant.ATTR_VALUE, 0L);
      final com.cburch.logisim.data.Location loc = Location.create(inputData.getSpineX("0", false), inputData.startY - 10, true);
      result.add(fact.createComponent(loc, attrs));
      inputData.registerConnection("0", loc, false);
      createSpine(result, inputData.getInputLocs("0", false).ys, new CompareYs());
    }
    if (!inputData.getInputLocs("1", false).ys.isEmpty()) {
      final com.cburch.logisim.data.AttributeSet attrs = fact.createAttributeSet();
      attrs.setValue(StdAttr.FACING, Direction.SOUTH);
      attrs.setValue(Constant.ATTR_VALUE, 1L);
      final com.cburch.logisim.data.Location loc = Location.create(inputData.getSpineX("1", false), inputData.startY - 10, true);
      result.add(fact.createComponent(loc, attrs));
      inputData.registerConnection("1", loc, false);
      createSpine(result, inputData.getInputLocs("1", false).ys, new CompareYs());
    }
  }

  //
  // placeInputs
  //
  private static void placeInputs(AnalyzerModel model, CircuitMutation result, InputData inputData, boolean useNands) {
    final java.util.ArrayList<com.cburch.logisim.data.Location> forbiddenYs = new ArrayList<Location>();
    final com.cburch.logisim.std.gates.CircuitBuilder.CompareYs compareYs = new CompareYs();
    int curX = inputData.getPinX();
    int curY = inputData.getStartY() + 20;
    VariableList inputs = model.getInputs();

    /* we start with placing the inverters */
    placeInputInverters(result, inputData, useNands);
    /* now we do the constants */
    placeConstants(result, inputData);

    int idx = 0;
    int busNr = 0;
    int busY = inputData.startY - 10;
    for (int nr = 0; nr < inputs.vars.size(); nr++) {
      final com.cburch.logisim.analyze.model.Var inp = inputs.vars.get(nr);
      if (inp.width == 1) {
        final java.lang.String name = inputData.getInputName(idx++);
        final com.cburch.logisim.std.gates.CircuitBuilder.SingleInput singleInput = inputData.getInputLocs(name, false);

        // determine point where we can intersect with spine
        int spineX = singleInput.spineX;
        com.cburch.logisim.data.Location spineLoc = Location.create(spineX, curY, true);
        if (!singleInput.ys.isEmpty()) {
          // search for a Y that won't intersect with others
          // (we needn't bother if the pin doesn't connect
          // with anything anyway.)
          forbiddenYs.sort(compareYs);
          while (Collections.binarySearch(forbiddenYs, spineLoc, compareYs) >= 0) {
            curY += 10;
            spineLoc = Location.create(spineX, curY, true);
          }
          singleInput.ys.add(spineLoc);
        }
        Location loc = Location.create(curX, curY, true);

        // now create the pin
        placeInput(result, loc, name, 1);

        final java.util.ArrayList<com.cburch.logisim.data.Location> spine = singleInput.ys;
        if (!spine.isEmpty()) {
          // create wire connecting pin to spine
          result.add(Wire.create(loc, spineLoc));

          // create spine
          createSpine(result, spine, compareYs);
        }

        // advance y and forbid spine intersections for next pin
        forbiddenYs.addAll(singleInput.ys);
      } else {
        /* first place the input and the splitter */
        final java.lang.String name = inp.name;
        final com.cburch.logisim.data.Location ploc = Location.create(curX, curY, true);
        /* create the pin */
        placeInput(result, ploc, name, inp.width);
        /* determine the position of the splitter */
        java.lang.String msbName = inputData.getInputName(idx);
        com.cburch.logisim.std.gates.CircuitBuilder.SingleInput singleInput = inputData.getInputLocs(msbName, false);
        int spineX = singleInput.spineX;
        final com.cburch.logisim.data.Location sloc = Location.create(spineX - 10, busY - SPLITTER_HEIGHT, true);
        placeSplitter(result, sloc, inp.width, true);
        /* place the bus connection */
        final com.cburch.logisim.data.Location BI1 = Location.create(ploc.getX() + 10 + busNr * SPINE_DISTANCE, ploc.getY(), true);
        final com.cburch.logisim.data.Location BI2 = Location.create(BI1.getX(), sloc.getY(), true);
        result.add(Wire.create(ploc, BI1));
        result.add(Wire.create(BI1, BI2));
        result.add(Wire.create(BI2, sloc));
        busNr++;
        /* Now connect to the spines */
        for (int bit = inp.width - 1; bit >= 0; bit--) {
          msbName = inputData.getInputName(idx++);
          singleInput = inputData.getInputLocs(msbName, false);
          spineX = singleInput.spineX;
          final java.util.ArrayList<com.cburch.logisim.data.Location> spine = singleInput.ys;
          if (!spine.isEmpty()) {
            /* add a location for the bus entry */
            final com.cburch.logisim.data.Location bloc = Location.create(spineX, busY, true);
            spine.add(bloc);
            forbiddenYs.sort(compareYs);
            // create spine
            createSpine(result, spine, compareYs);
          }
          forbiddenYs.addAll(singleInput.ys);
        }
        busY -= SPLITTER_HEIGHT;
      }
      curY += MINIMAL_PIN_DISTANCE;
    }
  }

  private static void createSpine(CircuitMutation result, List<Location> spine, Comparator<Location> compareYs) {
    spine.sort(compareYs);
    com.cburch.logisim.data.Location prev = spine.get(0);
    for (int k = 1, n = spine.size(); k < n; k++) {
      final com.cburch.logisim.data.Location cur = spine.get(k);
      if (!cur.equals(prev)) {
        result.add(Wire.create(prev, cur));
        prev = cur;
      }
    }
  }

  private static void placeInput(CircuitMutation result, Location loc, String name, int nrOfBits) {
    final com.cburch.logisim.std.wiring.Pin factory = Pin.FACTORY;
    final com.cburch.logisim.data.AttributeSet attrs = factory.createAttributeSet();
    attrs.setValue(StdAttr.FACING, Direction.EAST);
    attrs.setValue(Pin.ATTR_TYPE, Boolean.FALSE);
    attrs.setValue(Pin.ATTR_TRISTATE, Boolean.FALSE);
    attrs.setValue(StdAttr.LABEL, name);
    attrs.setValue(ProbeAttributes.PROBEAPPEARANCE, ProbeAttributes.getDefaultProbeAppearance());
    attrs.setValue(StdAttr.WIDTH, BitWidth.create(nrOfBits));
    result.add(factory.createComponent(loc, attrs));
  }

  private static void placeSplitter(CircuitMutation result, Location loc, int nrOfBits, boolean input) {
    final com.cburch.logisim.circuit.SplitterFactory factory = SplitterFactory.instance;
    final com.cburch.logisim.data.AttributeSet attrs = factory.createAttributeSet();
    attrs.setValue(StdAttr.FACING, Direction.SOUTH);
    attrs.setValue(SplitterAttributes.ATTR_FANOUT, nrOfBits);
    attrs.setValue(SplitterAttributes.ATTR_WIDTH, BitWidth.create(nrOfBits));
    attrs.setValue(
        SplitterAttributes.ATTR_APPEARANCE,
        input ? SplitterAttributes.APPEAR_LEFT : SplitterAttributes.APPEAR_RIGHT);
    attrs.setValue(SplitterAttributes.ATTR_SPACING, SPINE_DISTANCE / 10);
    result.add(factory.createComponent(loc, attrs));
  }

  private static void placeOutputs(AnalyzerModel model, CircuitMutation result, InputData outputData) {
    int startX = 0;
    int nrOfBusses = 0;
    final com.cburch.logisim.analyze.model.VariableList outputs = model.getOutputs();
    for (int idx = 0; idx < outputData.getNrOfInputs(); idx++) {
      final java.lang.String name = outputData.getInputName(idx);
      int posX =
          (outputData.getInputLocs(name, false) == null)
              ? 0
              : outputData.getInputLocs(name, false).spineX;
      if (posX > startX) startX = posX;
    }
    for (int idx = 0; idx < outputs.vars.size(); idx++) {
      if (outputs.vars.get(idx).width > 1) nrOfBusses++;
    }
    int pinX = startX + outputData.getNrOfInputs() * SPINE_DISTANCE + 10;
    if (nrOfBusses > 0) pinX += (nrOfBusses - 1) * SPINE_DISTANCE + BUS_SPINE_TO_WIRE_SPINE_DISTANCE;
    int pinY = outputData.getStartY() + 20;
    int busX = pinX - 10;
    int busID = 0;
    /* first we place the outputs with at least one connection, in the second pass we place the empty
     * outputs.
     */
    int cnt = 0;
    for (int idx = 0; idx < outputs.vars.size(); idx++) {
      final com.cburch.logisim.analyze.model.Var outp = outputs.vars.get(idx);
      final java.lang.String name = outputData.getInputName(cnt);
      if (outp.width == 1) {
        final com.cburch.logisim.data.Location pointP = Location.create(pinX, pinY, true);
        placeOutput(result, pointP, outp.name, 1);
        final com.cburch.logisim.std.gates.CircuitBuilder.SingleInput singleOutput = outputData.getInputLocs(name, false);
        if (singleOutput != null) {
          final com.cburch.logisim.data.Location pointC = Location.create(singleOutput.spineX, singleOutput.spineY, true);
          int xOffset = startX + cnt * SPINE_DISTANCE;
          final com.cburch.logisim.data.Location pointI1 = Location.create(xOffset, pointC.getY(), true);
          final com.cburch.logisim.data.Location pointI2 = Location.create(xOffset, pointP.getY(), true);
          if (pointC.getX() != pointI1.getX()) result.add(Wire.create(pointC, pointI1));
          if (pointI1.getY() != pointI2.getY()) result.add(Wire.create(pointI1, pointI2));
          if (pointI2.getX() != pointP.getX()) result.add(Wire.create(pointI2, pointP));
        }
        cnt++;
      } else {
        final com.cburch.logisim.data.Location pointP = Location.create(pinX, pinY, true);
        placeOutput(result, pointP, outp.name, outp.width);
        /* process the splitter */
        int sStartX = startX + cnt * SPINE_DISTANCE;
        final com.cburch.logisim.data.Location pointS =
            Location.create(
                sStartX + (outp.width - 1) * SPINE_DISTANCE + 10,
                TOP_BORDER + busID * SPLITTER_HEIGHT,
                true);
        placeSplitter(result, pointS, outp.width, false);
        // process the bus connection
        final com.cburch.logisim.data.Location pointI1 = Location.create(busX - busID * SPINE_DISTANCE, pointS.getY(), true);
        final com.cburch.logisim.data.Location pointI2 = Location.create(pointI1.getX(), pointP.getY(), true);
        busID++;
        if (pointS.getX() != pointI1.getX()) result.add(Wire.create(pointS, pointI1));
        if (pointI1.getY() != pointI2.getY()) result.add(Wire.create(pointI1, pointI2));
        if (pointI2.getX() != pointP.getX()) result.add(Wire.create(pointI2, pointP));
        // process the connections
        for (int bit = 0; bit < outp.width; bit++) {
          final com.cburch.logisim.data.Location pointSe = Location.create(sStartX + bit * SPINE_DISTANCE, pointS.getY() + 20, true);
          final java.lang.String tName = outputData.getInputName(cnt + bit);
          final com.cburch.logisim.std.gates.CircuitBuilder.SingleInput singleOutput = outputData.getInputLocs(tName, false);
          if (singleOutput != null) {
            final com.cburch.logisim.data.Location pointC = Location.create(singleOutput.spineX, singleOutput.spineY, true);
            if (pointSe.getX() == pointC.getX()) {
              result.add(Wire.create(pointSe, pointC));
            } else {
              final com.cburch.logisim.data.Location pointI = Location.create(pointSe.getX(), pointC.getY(), true);
              result.add(Wire.create(pointC, pointI));
              result.add(Wire.create(pointSe, pointI));
            }
          }
        }
        // all done
        cnt += outp.width;
      }
      pinY += MINIMAL_PIN_DISTANCE;
    }
  }
  //
  // placeOutput
  //
  private static void placeOutput(CircuitMutation result, Location loc, String name, int nrOfBits) {
    final com.cburch.logisim.std.wiring.Pin factory = Pin.FACTORY;
    final com.cburch.logisim.data.AttributeSet attrs = factory.createAttributeSet();
    attrs.setValue(StdAttr.FACING, Direction.WEST);
    attrs.setValue(Pin.ATTR_TYPE, Boolean.TRUE);
    attrs.setValue(ProbeAttributes.PROBEAPPEARANCE, ProbeAttributes.getDefaultProbeAppearance());
    attrs.setValue(StdAttr.LABEL, name);
    attrs.setValue(StdAttr.WIDTH, BitWidth.create(nrOfBits));
    result.add(factory.createComponent(loc, attrs));
  }

  private static int roundDown(int value) {
    return value / 10 * 10;
  }

  private static int roundUp(int value) {
    return (value + 9) / 10 * 10;
  }

  private CircuitBuilder() {}
}
