/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.io;

import java.util.HashMap;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.netlistComponent;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import com.cburch.logisim.fpga.hdlgenerator.InlinedHdlGeneratorFactory;
import com.cburch.logisim.util.LineBuffer;

public class LedBarHdlGeneratorFactory extends InlinedHdlGeneratorFactory {

  @Override
  public LineBuffer getInlinedCode(
      Netlist netlist, Long componentId, netlistComponent componentInfo, String circuitName) {
    final com.cburch.logisim.util.LineBuffer contents = LineBuffer.getHdlBuffer();
    final boolean isSingleBus =
        componentInfo
            .getComponent()
            .getAttributeSet()
            .getValue(LedBar.ATTR_INPUT_TYPE)
            .equals(LedBar.INPUT_ONE_WIRE);
    final int nrOfSegments =
        componentInfo.getComponent().getAttributeSet().getValue(LedBar.ATTR_MATRIX_COLS).getWidth();
    final java.util.HashMap<java.lang.String,java.lang.String> wires = new HashMap<String, String>();
    for (int pin = 0; pin < nrOfSegments; pin++) {
      final java.lang.String destPin =
          LineBuffer.format(
              "{{1}}{{<}}{{2}}{{>}}",
              LOCAL_OUTPUT_BUBBLE_BUS_NAME, componentInfo.getLocalBubbleOutputStartId() + pin);
      final java.lang.String sourcePin =
          isSingleBus
              ? Hdl.getBusEntryName(componentInfo, 0, true, pin, netlist)
              : Hdl.getNetName(componentInfo, pin, true, netlist);
      wires.put(destPin, sourcePin);
    }
    Hdl.addAllWiresSorted(contents, wires);
    return contents;
  }

  @Override
  public boolean isHdlSupportedTarget(AttributeSet attrs) {
    return attrs.getValue(DotMatrixBase.ATTR_PERSIST) == 0;
  }
}
