/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.plexers;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.hdlgenerator.AbstractHdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.util.LineBuffer;

public class DecoderHdlGeneratorFactory extends AbstractHdlGeneratorFactory {

  public DecoderHdlGeneratorFactory() {
    super();
    getWiresPortsDuringHDLWriting = true;
  }

  @Override
  public void getGenerationTimeWiresPorts(Netlist theNetlist, AttributeSet attrs) {
    final int nrOfselectBits = attrs.getValue(PlexersLibrary.ATTR_SELECT).getWidth();
    final int selectInputIndex = (1 << nrOfselectBits);
    for (int outp = 0; outp < selectInputIndex; outp++) {
      myPorts.add(Port.OUTPUT, String.format("decoderOut_%d", outp), 1, outp);
    }
    myPorts.add(Port.INPUT, "sel", nrOfselectBits, selectInputIndex);
    if (attrs.getValue(PlexersLibrary.ATTR_ENABLE))
        myPorts.add(Port.INPUT, "enable", 1, selectInputIndex + 1, false);
  }

  @Override
  public LineBuffer getModuleFunctionality(Netlist theNetList, AttributeSet attrs) {
    final com.cburch.logisim.util.LineBuffer contents = LineBuffer.getBuffer();
    final int nrOfselectBits = attrs.getValue(PlexersLibrary.ATTR_SELECT).getWidth();
    final int numOutputs = (1 << nrOfselectBits);
    java.lang.String space = " ";
    for (int i = 0; i < numOutputs; i++) {
      if (i == 10) space = "";
      contents.pair("bin", Hdl.getConstantVector(i, nrOfselectBits))
              .pair("i", i);
      if (Hdl.isVhdl()) {
        contents.empty().addVhdlKeywords().add("""
            decoderOut_{{i}}{{1}}<= '1' {{when}} sel = {{bin}} {{and}}
            {{1}}                        enable = '1' {{else}} '0';
            """, space);
      } else {
        contents.add("assign decoderOut_{{i}}{{1}} = (enable&(sel == {{bin}})) ? 1'b1 : 1'b0;", space);
      }
    }
    return contents;
  }
}
