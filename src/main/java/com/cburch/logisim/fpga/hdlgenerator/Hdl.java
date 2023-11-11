/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.hdlgenerator;

import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.netlistComponent;
import com.cburch.logisim.fpga.file.FileWriter;
import com.cburch.logisim.fpga.gui.Reporter;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.CollectionUtil;
import com.cburch.logisim.util.LineBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RUntainted;

public class Hdl {

  public static final String NET_NAME = "s_logisimNet";
  public static final String BUS_NAME = "s_logisimBus";

  /**
   * Length of remark block special sequences (block open/close, line open/close).
   */
  public static final int REMARK_MARKER_LENGTH = 3;

  private Hdl() {
    throw new IllegalStateException("Utility class. No instantiation allowed.");
  }

  public static boolean isVhdl() {
    return AppPreferences.HdlType.get().equals(HdlGeneratorFactory.VHDL);
  }

  public static boolean isVerilog() {
    return AppPreferences.HdlType.get().equals(HdlGeneratorFactory.VERILOG);
  }

  public static String bracketOpen() {
    return isVhdl() ? "(" : "[";
  }

  public static String bracketClose() {
    return isVhdl() ? ")" : "]";
  }

  public static String getRemarkChar() {
    return isVhdl() ? "-" : "*";
  }

  /**
   * Comment block opening sequence. Must be REMARK_BLOCK_SEQ_LEN long.
   */
  public static String getRemarkBlockStart() {
    return isVhdl() ? "---" : "/**";
  }

  /**
   * Comment block closing sequence. Must be REMARK_BLOCK_SEQ_LEN long.
   */
  public static String getRemarkBlockEnd() {
    return isVhdl() ? "---" : "**/";
  }

  /**
   * Comment block line (mid block) opening sequence. Must be REMARK_BLOCK_SEQ_LEN long.
   */
  public static String getRemarkBlockLineStart() {
    return isVhdl() ? "-- " : "** ";
  }

  /**
   * Comment block line (mid block) closing sequence. Must be REMARK_BLOCK_SEQ_LEN long.
   */
  public static String getRemarkBlockLineEnd() {
    return isVhdl() ? " --" : " **";
  }

  public static String getLineCommentStart() {
    return isVhdl() ? "-- " : "// ";
  }

  public static String startIf(String condition) {
    return isVhdl() ? LineBuffer.formatHdl("IF {{1}} THEN", condition)
                    : LineBuffer.formatHdl("if ({{1}}) begin", condition);
  }

  public static String elseStatement() {
    return isVhdl() ? Vhdl.getVhdlKeyword("ELSE") : "end else begin";
  }

  public static String elseIf(String condition) {
    return isVhdl() ? LineBuffer.formatHdl("{{1}} {{2}} {{3}}", Vhdl.getVhdlKeyword("ELSIF"), condition, Vhdl.getVhdlKeyword("THEN"))
                    : LineBuffer.formatHdl("end else if ({{1}}) begin", condition);
  }

  public static String endIf() {
    return isVhdl() ? Vhdl.getVhdlKeyword("END ") + Vhdl.getVhdlKeyword("IF")
                    : "end";
  }

  public static String assignPreamble() {
    return isVhdl() ? "" : "assign ";
  }

  public static String assignOperator() {
    return isVhdl() ? " <= " : " = ";
  }

  public static String equalOperator() {
    return isVhdl() ? " = " : "==";
  }

  public static String notEqualOperator() {
    return isVhdl() ? " \\= " : "!=";
  }

  private static String typecast(String signal, boolean signed) {
    return isVhdl()
                ? LineBuffer.formatHdl("{{1}}({{2}})", (signed ? "signed" : "unsigned"), signal)
                : (signed ? "$signed(" + signal + ")" : signal);
  }

  public static String greaterOperator(String signalOne, String signalTwo, boolean signed, boolean equal) {
    return LineBuffer.formatHdl("{{1}} >{{2}} {{3}}", typecast(signalOne, signed), equal ? "=" : "", typecast(signalTwo, signed));
  }

  public static String lessOperator(String signalOne, String signalTwo, boolean signed, boolean equal) {
    return LineBuffer.formatHdl("{{1}} <{{2}} {{3}}", typecast(signalOne, signed), equal ? "=" : "", typecast(signalTwo, signed));
  }

  public static String leqOperator(String signalOne, String signalTwo, boolean signed) {
    return lessOperator(signalOne, signalTwo, signed, true);
  }

  public static String geqOperator(String signalOne, String signalTwo, boolean signed) {
    return greaterOperator(signalOne, signalTwo, signed, true);
  }

  public static String risingEdge(String signal) {
    return isVhdl() ? "rising_edge(" + signal + ")"
                    : "posedge " + signal;
  }

  public static String notOperator() {
    return isVhdl() ? Vhdl.getVhdlKeyword(" NOT ") : "~";
  }

  public static String andOperator() {
    return isVhdl() ? Vhdl.getVhdlKeyword(" AND ") : "&";
  }

  public static String orOperator() {
    return isVhdl() ? Vhdl.getVhdlKeyword(" OR ") : "|";
  }

  public static String xorOperator() {
    return isVhdl() ? Vhdl.getVhdlKeyword(" XOR ") : "^";
  }

  public static String addOperator(String signalOne, String signalTwo, boolean signed) {
    return (isVhdl() ? "std_logic_vector(" : "")
            + typecast(signalOne, signed)
            + " + "
            + typecast(signalTwo, signed)
            + (isVhdl() ? ")" : "");
  }

  public static String subOperator(String signalOne, String signalTwo, boolean signed) {
    return (isVhdl() ? "std_logic_vector(" : "")
            + typecast(signalOne, signed)
            + " - "
            + typecast(signalTwo, signed)
            + (isVhdl() ? ")" : "");
  }

  public static String shiftlOperator(String signal, int width, int distance, boolean arithmetic) {
    if (distance == 0) return signal;
    return isVhdl() ? LineBuffer.formatHdl("{{1}}{{2}} & {{4}}{{3}}{{4}}", signal, splitVector(width - 1 - distance, 0), "0".repeat(distance), distance == 1 ? "'" : "\"")
                    : LineBuffer.formatHdl("{{{1}}{{2}},{{{3}}{1'b0}}}", signal, splitVector(width - 1 - distance, 0), distance);
  }

  public static String shiftrOperator(String signal, int width, int distance, boolean arithmetic) {
    if (distance == 0) return signal;
    if (arithmetic) {
      return isVhdl()
        ? LineBuffer.formatHdl("({{1}}{{2}}0 => {{3}}({{1}})) & {{3}}{{4}}", width - 1, vectorLoopId(), signal, splitVector(width - 1, width - distance))
        : LineBuffer.formatHdl("{ {{{1}}{{{2}}[{{1}}-1]}},{{2}}{{3}}}", width, signal, splitVector(width - 1, width - distance));
    } else {
      return isVhdl()
        ? LineBuffer.formatHdl("{{1}}{{2}}{{1}} & {{3}}{{4}", (distance == 1 ? "'" : "\""), "0".repeat(distance), signal, splitVector(width - 1, width - distance))
        : LineBuffer.formatHdl("{ {{{1}}{1'b0}},{{2}}{{3}}}", width, signal, splitVector(width - 1, width - distance));
    }
  }

  public static String sllOperator(String signal, int width, int distance) {
    return shiftlOperator(signal, width, distance, false);
  }

  public static String slaOperator(String signal, int width, int distance) {
    return shiftlOperator(signal, width, distance, true);
  }

  public static String srlOperator(String signal, int width, int distance) {
    return shiftrOperator(signal, width, distance, false);
  }

  public static String sraOperator(String signal, int width, int distance) {
    return shiftrOperator(signal, width, distance, true);
  }

  public static String rolOperator(String signal, int width, int distance) {
    return LineBuffer.formatHdl("{{1}}{{2}}{{3}}{{1}}{{4}}", signal, splitVector(width - 1 - distance, 0), (isVhdl() ? " & " : ","), splitVector(width - 1, width - distance));
  }

  public static String rorOperator(String signal, int width, int distance) {
    return LineBuffer.formatHdl("{{1}}{{2}}{{3}}{{1}}{{4}}", signal, splitVector(distance, 0), (isVhdl() ? " & " : ","), splitVector(width - 1, distance));
  }

  public static String zeroBit() {
    return isVhdl() ? "'0'" : "1'b0";
  }

  public static String oneBit() {
    return isVhdl() ? "'1'" : "1'b1";
  }

  public static @RUntainted String unconnected(boolean empty) {
    return isVhdl() ? Vhdl.getVhdlKeyword("OPEN") : empty ? "" : "'bz";
  }

  public static String vectorLoopId() {
    return isVhdl() ? Vhdl.getVhdlKeyword(" DOWNTO ") : ":";
  }

  public static String splitVector(int start, int end) {
    if (start == end) return LineBuffer.formatHdl("{{<}}{{2}}{{>}}", start);
    return isVhdl()
                ? LineBuffer.formatHdl("({{1}}{{2}}{{3}})", start, vectorLoopId(), end)
                : LineBuffer.formatHdl("[{{1}}:{{2}}]", start, end);
  }

  public static @RUntainted String getZeroVector(int nrOfBits, boolean floatingPinTiedToGround) {
    final java.lang.StringBuilder contents = new StringBuilder();
    if (isVhdl()) {
      java.lang.String fillValue = (floatingPinTiedToGround) ? "0" : "1";
      java.lang.String hexFillValue = (floatingPinTiedToGround) ? "0" : "F";
      if (nrOfBits == 1) {
        contents.append("'").append(fillValue).append("'");
      } else {
        if ((nrOfBits % 4) > 0) {
          contents.append("\"");
          contents.append(fillValue.repeat((nrOfBits % 4)));
          contents.append("\"");
          if (nrOfBits > 3) {
            contents.append("&");
          }
        }
        if ((nrOfBits / 4) > 0) {
          contents.append("X\"");
          contents.append(hexFillValue.repeat(Math.max(0, (nrOfBits / 4))));
          contents.append("\"");
        }
      }
    } else {
      contents.append(nrOfBits).append("'d");
      contents.append(floatingPinTiedToGround ? "0" : "-1");
    }
    return contents.toString();
  }

  public static String getConstantVector(long value, int nrOfBits) {
    final int nrHexDigits = nrOfBits / 4;
    final int nrSingleBits = nrOfBits % 4;
    final java.lang.String[] hexDigits = new String[nrHexDigits];
    final java.lang.StringBuilder singleBits = new StringBuilder();
    long shiftValue = value;
    for (int hexIndex = nrHexDigits - 1; hexIndex >= 0; hexIndex--) {
      long hexValue = shiftValue & 0xFL;
      shiftValue >>= 4L;
      hexDigits[hexIndex] = String.format("%1X", hexValue);
    }
    final java.lang.StringBuilder hexValue = new StringBuilder();
    for (int hexIndex = 0; hexIndex < nrHexDigits; hexIndex++) {
      hexValue.append(hexDigits[hexIndex]);
    }
    long mask = (nrSingleBits == 0) ? 0 : 1L << (nrSingleBits - 1);
    while (mask > 0) {
      singleBits.append((shiftValue & mask) == 0 ? "0" : "1");
      mask >>= 1L;
    }
    // first case, we have to concatinate
    if ((nrHexDigits > 0) && (nrSingleBits > 0)) {
      return Hdl.isVhdl()
             ? LineBuffer.format("\"{{1}}\"&X\"{{2}}\"", singleBits.toString(), hexValue.toString())
             : LineBuffer.format("{{{1}}'b{{2}}, {{3}}'h{{4}}}", nrSingleBits, singleBits.toString(),
                nrHexDigits * 4, hexValue.toString());
    }
    // second case, we have only hex digits
    if (nrHexDigits > 0) {
      return Hdl.isVhdl()
        ? LineBuffer.format("X\"{{1}}\"", hexValue.toString())
        : LineBuffer.format("{{1}}'h{{2}}", nrHexDigits * 4, hexValue.toString());
    }
    // final case, we have only single bits
    if (Hdl.isVhdl()) {
      final java.lang.String vhdlTicks = (nrOfBits == 1) ? "'" : "\"";
      return LineBuffer.format("{{1}}{{2}}{{1}}", vhdlTicks, singleBits.toString());
    }
    return LineBuffer.format("{{1}}'b{{2}}", nrSingleBits, singleBits.toString());
  }

  public static String getNetName(netlistComponent comp, int endIndex, boolean floatingNetTiedToGround, Netlist myNetlist) {
    java.lang.String netName = "";
    if ((endIndex >= 0) && (endIndex < comp.nrOfEnds())) {
      final java.lang.@RUntainted String floatingValue = floatingNetTiedToGround ? zeroBit() : oneBit();
      final com.cburch.logisim.fpga.designrulecheck.ConnectionEnd thisEnd = comp.getEnd(endIndex);
      final boolean isOutput = thisEnd.isOutputEnd();

      if (thisEnd.getNrOfBits() == 1) {
        final com.cburch.logisim.fpga.designrulecheck.ConnectionPoint solderPoint = thisEnd.get((byte) 0);
        if (solderPoint.getParentNet() == null) {
          // The net is not connected
          netName = LineBuffer.formatHdl(isOutput ? unconnected(true) : floatingValue);
        } else {
          // The net is connected, we have to find out if the connection
          // is to a bus or to a normal net.
          netName = (solderPoint.getParentNet().getBitWidth() == 1)
                  ? LineBuffer.formatHdl("{{1}}{{2}}", NET_NAME, myNetlist.getNetId(solderPoint.getParentNet()))
                  : LineBuffer.formatHdl("{{1}}{{2}}{{<}}{{3}}{{>}}", BUS_NAME,
                      myNetlist.getNetId(solderPoint.getParentNet()), solderPoint.getParentNetBitIndex());
        }
      }
    }
    return netName;
  }

  public static String getBusEntryName(netlistComponent comp, int endIndex, boolean floatingNetTiedToGround, int bitindex, Netlist theNets) {
    java.lang.String busName = "";
    if ((endIndex >= 0) && (endIndex < comp.nrOfEnds())) {
      final com.cburch.logisim.fpga.designrulecheck.ConnectionEnd thisEnd = comp.getEnd(endIndex);
      final boolean isOutput = thisEnd.isOutputEnd();
      final int nrOfBits = thisEnd.getNrOfBits();
      if ((nrOfBits > 1) && (bitindex >= 0) && (bitindex < nrOfBits)) {
        if (thisEnd.get((byte) bitindex).getParentNet() == null) {
          // The net is not connected
          busName = LineBuffer.formatHdl(isOutput ? unconnected(false) : getZeroVector(1, floatingNetTiedToGround));
        } else {
          final com.cburch.logisim.fpga.designrulecheck.Net connectedNet = thisEnd.get((byte) bitindex).getParentNet();
          final java.lang.Byte connectedNetBitIndex = thisEnd.get((byte) bitindex).getParentNetBitIndex();
          // The net is connected, we have to find out if the connection
          // is to a bus or to a normal net.
          busName =
              !connectedNet.isBus()
                  ? LineBuffer.formatHdl("{{1}}{{2}}", NET_NAME, theNets.getNetId(connectedNet))
                  : LineBuffer.formatHdl("{{1}}{{2}}{{<}}{{3}}{{>}}", BUS_NAME, theNets.getNetId(connectedNet), connectedNetBitIndex);
        }
      }
    }
    return busName;
  }

  public static String getBusNameContinues(netlistComponent comp, int endIndex, Netlist theNets) {
    if ((endIndex < 0) || (endIndex >= comp.nrOfEnds())) return null;
    final com.cburch.logisim.fpga.designrulecheck.ConnectionEnd connectionInformation = comp.getEnd(endIndex);
    final int nrOfBits = connectionInformation.getNrOfBits();
    if (nrOfBits == 1) return getNetName(comp, endIndex, true, theNets);
    if (!theNets.isContinuesBus(comp, endIndex)) return null;
    final com.cburch.logisim.fpga.designrulecheck.Net connectedNet = connectionInformation.get((byte) 0).getParentNet();
    return LineBuffer.formatHdl("{{1}}{{2}}{{3}}",
        BUS_NAME,
        theNets.getNetId(connectedNet),
        splitVector(connectionInformation.get((byte) (connectionInformation.getNrOfBits() - 1)).getParentNetBitIndex(),
                    connectionInformation.get((byte) (0)).getParentNetBitIndex()));
  }

  public static String getBusName(netlistComponent comp, int endIndex, Netlist theNets) {
    if ((endIndex < 0) || (endIndex >= comp.nrOfEnds())) return null;
    final com.cburch.logisim.fpga.designrulecheck.ConnectionEnd connectionInformation = comp.getEnd(endIndex);
    final int nrOfBits = connectionInformation.getNrOfBits();
    if (nrOfBits == 1)  return getNetName(comp, endIndex, true, theNets);
    if (!theNets.isContinuesBus(comp, endIndex)) return null;
    final com.cburch.logisim.fpga.designrulecheck.Net connectedNet = connectionInformation.get((byte) 0).getParentNet();
    if (connectedNet.getBitWidth() != nrOfBits) return getBusNameContinues(comp, endIndex, theNets);
    return LineBuffer.format("{{1}}{{2}}", BUS_NAME, theNets.getNetId(connectedNet));
  }

  public static String getClockNetName(netlistComponent comp, int endIndex, Netlist theNets) {
    java.lang.StringBuilder contents = new StringBuilder();
    if ((theNets.getCurrentHierarchyLevel() != null) && (endIndex >= 0) && (endIndex < comp.nrOfEnds())) {
      final com.cburch.logisim.fpga.designrulecheck.ConnectionEnd endData = comp.getEnd(endIndex);
      if (endData.getNrOfBits() == 1) {
        final com.cburch.logisim.fpga.designrulecheck.Net connectedNet = endData.get((byte) 0).getParentNet();
        final java.lang.Byte ConnectedNetBitIndex = endData.get((byte) 0).getParentNetBitIndex();
        /* Here we search for a clock net Match */
        final int clocksourceid = theNets.getClockSourceId(
            theNets.getCurrentHierarchyLevel(), connectedNet, ConnectedNetBitIndex);
        if (clocksourceid >= 0) {
          contents.append(HdlGeneratorFactory.CLOCK_TREE_NAME).append(clocksourceid);
        }
      }
    }
    return contents.toString();
  }

  public static boolean writeEntity(String targetDirectory, List<@RUntainted String> contents, String componentName) {
    if (!Hdl.isVhdl()) return true;
    if (contents.isEmpty()) {
      // FIXME: hardcoded string
      Reporter.report.addFatalError("INTERNAL ERROR: Empty entity description received!");
      return false;
    }
    final java.io.File outFile = FileWriter.getFilePointer(targetDirectory, componentName, true);
    if (outFile == null) return false;
    return FileWriter.writeContents(outFile, contents);
  }

  public static boolean writeArchitecture(String targetDirectory, List<@RUntainted String> contents, String componentName) {
    if (CollectionUtil.isNullOrEmpty(contents)) {
      // FIXME: hardcoded string
      Reporter.report.addFatalErrorFmt("INTERNAL ERROR: Empty behavior description for Component '%s' received!", componentName);
      return false;
    }
    final java.io.File outFile = FileWriter.getFilePointer(targetDirectory, componentName, false);
    if (outFile == null)  return false;
    return FileWriter.writeContents(outFile, contents);
  }

  public static Map<String, String> getNetMap(String sourceName, boolean floatingPinTiedToGround,
      netlistComponent comp, int endIndex, Netlist theNets) {
    final java.util.HashMap<java.lang.String,java.lang.String> netMap = new HashMap<String, String>();
    if ((endIndex < 0) || (endIndex >= comp.nrOfEnds())) {
      Reporter.report.addFatalError("INTERNAL ERROR: Component tried to index non-existing SolderPoint");
      return netMap;
    }
    final com.cburch.logisim.fpga.designrulecheck.ConnectionEnd connectionInformation = comp.getEnd(endIndex);
    final boolean isOutput = connectionInformation.isOutputEnd();
    final int nrOfBits = connectionInformation.getNrOfBits();
    if (nrOfBits == 1) {
      /* Here we have the easy case, just a single bit net */
      netMap.put(sourceName, getNetName(comp, endIndex, floatingPinTiedToGround, theNets));
    } else {
      /*
       * Here we have the more difficult case, it is a bus that needs to
       * be mapped
       */
      /* First we check if the bus has a connection */
      boolean connected = false;
      for (int bit = 0; bit < nrOfBits; bit++) {
        if (connectionInformation.get((byte) bit).getParentNet() != null)
          connected = true;
      }
      if (!connected) {
        /* Here is the easy case, the bus is unconnected */
        netMap.put(sourceName, isOutput ? unconnected(true) : getZeroVector(nrOfBits, floatingPinTiedToGround));
      } else {
        /*
         * There are connections, we detect if it is a continues bus
         * connection
         */
        if (theNets.isContinuesBus(comp, endIndex)) {
          /* Another easy case, the continues bus connection */
          netMap.put(sourceName, getBusNameContinues(comp, endIndex, theNets));
        } else {
          /* The last case, we have to enumerate through each bit */
          if (isVhdl()) {
            final java.lang.StringBuilder sourceNetName = new StringBuilder();
            for (int bit = 0; bit < nrOfBits; bit++) {
              /* First we build the Line information */
              sourceNetName.setLength(0);
              sourceNetName.append(String.format("%s(%d) ", sourceName, bit));
              final com.cburch.logisim.fpga.designrulecheck.ConnectionPoint solderPoint = connectionInformation.get((byte) bit);
              if (solderPoint.getParentNet() == null) {
                /* The net is not connected */
                netMap.put(sourceNetName.toString(), isOutput ? unconnected(false) : getZeroVector(1, floatingPinTiedToGround));
              } else {
                /*
                 * The net is connected, we have to find out if
                 * the connection is to a bus or to a normal net
                 */
                if (solderPoint.getParentNet().getBitWidth() == 1) {
                  /* The connection is to a Net */
                  netMap.put(sourceNetName.toString(), String.format("%s%d", NET_NAME,
                      theNets.getNetId(solderPoint.getParentNet())));
                } else {
                  /* The connection is to an entry of a bus */
                  netMap.put(sourceNetName.toString(), String.format("%s%d(%d)", BUS_NAME,
                      theNets.getNetId(solderPoint.getParentNet()), solderPoint.getParentNetBitIndex()));
                }
              }
            }
          } else {
            final java.util.ArrayList<java.lang.String> seperateSignals = new ArrayList<String>();
            /*
             * First we build an array with all the signals that
             * need to be concatenated
             */
            for (int bit = 0; bit < nrOfBits; bit++) {
              final com.cburch.logisim.fpga.designrulecheck.ConnectionPoint solderPoint = connectionInformation.get((byte) bit);
              if (solderPoint.getParentNet() == null) {
                /* this entry is not connected */
                seperateSignals.add(isOutput ? "1'bZ" : getZeroVector(1, floatingPinTiedToGround));
              } else {
                /*
                 * The net is connected, we have to find out if
                 * the connection is to a bus or to a normal net
                 */
                if (solderPoint.getParentNet().getBitWidth() == 1) {
                  /* The connection is to a Net */
                  seperateSignals.add(String.format("%s%d", NET_NAME,
                      theNets.getNetId(solderPoint.getParentNet())));
                } else {
                  /* The connection is to an entry of a bus */
                  seperateSignals.add(String.format("%s%d[%d]", BUS_NAME,
                      theNets.getNetId(solderPoint.getParentNet()), solderPoint.getParentNetBitIndex()));
                }
              }
            }
            /* Finally we can put all together */
            final java.lang.StringBuilder vector = new StringBuilder();
            vector.append("{");
            for (int bit = nrOfBits; bit > 0; bit--) {
              vector.append(seperateSignals.get(bit - 1));
              if (bit != 1) {
                vector.append(",");
              }
            }
            vector.append("}");
            netMap.put(sourceName, vector.toString());
          }
        }
      }
    }
    return netMap;
  }

  public static void addAllWiresSorted(LineBuffer contents, Map<String, String> wires) {
    int maxNameLength = 0;
    for (java.lang.String wire : wires.keySet())
      maxNameLength = Math.max(maxNameLength, wire.length());
    final java.util.TreeSet<java.lang.String> sortedWires = new TreeSet<>(wires.keySet());
    for (java.lang.String wire : sortedWires)
      contents.add("{{assign}}{{1}}{{2}}{{=}}{{3}};", wire, " ".repeat(maxNameLength - wire.length()), wires.get(wire));
    wires.clear();
  }

  public static List<@RUntainted String> getExtendedLibrary() {
    final com.cburch.logisim.util.LineBuffer lines = LineBuffer.getBuffer();
    lines.addVhdlKeywords().add("""

               {{library}} ieee;
               {{use}} ieee.std_logic_1164.all;
               {{use}} ieee.numeric_std.all;

               """);
    return lines.get();
  }

  public static List<@RUntainted String> getStandardLibrary() {
    final com.cburch.logisim.util.LineBuffer lines = LineBuffer.getBuffer();
    lines.addVhdlKeywords().add("""

              {{library}} ieee;
              {{use}} ieee.std_logic_1164.all;

              """);
    return lines.get();
  }
}
