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

public class DotMatrixHdlGeneratorFactory extends InlinedHdlGeneratorFactory {

  @Override
  public LineBuffer getInlinedCode(
      Netlist netlist, Long componentId, netlistComponent componentInfo, String circuitName) {
    final com.cburch.logisim.util.LineBuffer contents = LineBuffer.getHdlBuffer();
    final boolean colBased =
        componentInfo.getComponent().getAttributeSet().getValue(DotMatrixBase.ATTR_INPUT_TYPE)
            == DotMatrixBase.INPUT_COLUMN;
    final boolean rowBased =
        componentInfo.getComponent().getAttributeSet().getValue(DotMatrixBase.ATTR_INPUT_TYPE)
            == DotMatrixBase.INPUT_ROW;
    final int rows =
        componentInfo
            .getComponent()
            .getAttributeSet()
            .getValue(DotMatrix.ATTR_MATRIX_ROWS)
            .getWidth();
    final int cols =
        componentInfo
            .getComponent()
            .getAttributeSet()
            .getValue(DotMatrix.ATTR_MATRIX_COLS)
            .getWidth();
    final java.util.HashMap<java.lang.String,java.lang.String> wires = new HashMap<String, String>();

    if (colBased) {
      /* The simulator uses here following addressing scheme (2x2):
       *  r1,c0 r1,c1
       *  r0,c0 r0,c1
       *
       *  hence the rows are inverted to the definition of the LED-Matrix that uses:
       *  r0,c0 r0,c1
       *  r1,c0 r1,c1
       */
      for (int dotMatrixRow = 0; dotMatrixRow < rows; dotMatrixRow++) {
        final int ledMatrixRow = rows - dotMatrixRow - 1;
        for (int ledMatrixCol = 0; ledMatrixCol < cols; ledMatrixCol++) {
          final java.lang.String wire =
              (rows == 1)
                  ? Hdl.getNetName(componentInfo, ledMatrixCol, true, netlist)
                  : Hdl.getBusEntryName(componentInfo, ledMatrixCol, true, dotMatrixRow, netlist);
          final int idx =
              (ledMatrixRow * cols) + ledMatrixCol + componentInfo.getLocalBubbleOutputStartId();
          wires.put(
              LineBuffer.formatHdl("{{1}}{{<}}{{2}}{{>}}", LOCAL_OUTPUT_BUBBLE_BUS_NAME, idx),
              wire);
        }
      }
    } else if (rowBased) {
      /* The simulator uses here following addressing scheme (2x2):
       *  r1,c1 r1,c0
       *  r0,c1 r0,c0
       *
       *  hence the cols are inverted to the definition of the LED-Matrix that uses:
       *  r0,c0 r0,c1
       *  r1,c0 r1,c1
       */
      for (int ledMatrixRow = 0; ledMatrixRow < rows; ledMatrixRow++) {
        for (int dotMatrixCol = 0; dotMatrixCol < cols; dotMatrixCol++) {
          final int ledMatrixCol = cols - dotMatrixCol - 1;
          final java.lang.String wire =
              (cols == 1)
                  ? Hdl.getNetName(componentInfo, ledMatrixRow, true, netlist)
                  : Hdl.getBusEntryName(componentInfo, ledMatrixRow, true, ledMatrixCol, netlist);
          final int idx =
              (ledMatrixRow * cols) + dotMatrixCol + componentInfo.getLocalBubbleOutputStartId();
          wires.put(
              LineBuffer.formatHdl("{{1}}{{<}}{{2}}{{>}}", LOCAL_OUTPUT_BUBBLE_BUS_NAME, idx),
              wire);
        }
      }
    } else {
      /* The simulator uses here following addressing scheme (2x2):
       *  r1,c0 r1,c1
       *  r0,c0 r0,c1
       *
       *  hence the rows are inverted to the definition of the LED-Matrix that uses:
       *  r0,c0 r0,c1
       *  r1,c0 r1,c1
       */
      for (int dotMatrixRow = 0; dotMatrixRow < rows; dotMatrixRow++) {
        final int ledMatrixRow = rows - dotMatrixRow - 1;
        for (int ledMatrixCol = 0; ledMatrixCol < cols; ledMatrixCol++) {
          final java.lang.String rowWire =
              (rows == 1)
                  ? Hdl.getNetName(componentInfo, 1, true, netlist)
                  : Hdl.getBusEntryName(componentInfo, 1, true, dotMatrixRow, netlist);
          final java.lang.String colWire =
              (cols == 1)
                  ? Hdl.getNetName(componentInfo, 0, true, netlist)
                  : Hdl.getBusEntryName(componentInfo, 0, true, ledMatrixCol, netlist);
          final int idx =
              (ledMatrixRow * cols) + ledMatrixCol + componentInfo.getLocalBubbleOutputStartId();
          wires.put(
              LineBuffer.formatHdl("{{1}}{{<}}{{2}}{{>}}", LOCAL_OUTPUT_BUBBLE_BUS_NAME, idx),
              LineBuffer.formatHdl("{{1}}{{and}}{{2}}", rowWire, colWire));
        }
      }
    }
    Hdl.addAllWiresSorted(contents, wires);
    return contents;
  }

  @Override
  public boolean isHdlSupportedTarget(AttributeSet attrs) {
    return attrs.getValue(DotMatrixBase.ATTR_PERSIST) == 0;
  }
}
