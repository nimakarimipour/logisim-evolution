/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.download;

import static com.cburch.logisim.fpga.Strings.S;

import com.cburch.logisim.fpga.data.BoardInformation;
import com.cburch.logisim.fpga.data.MappableResourcesContainer;
import com.cburch.logisim.fpga.data.PullBehaviors;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.file.FileWriter;
import com.cburch.logisim.fpga.gui.Reporter;
import com.cburch.logisim.fpga.hdlgenerator.HdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.TickComponentHdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.ToplevelHdlGeneratorFactory;
import com.cburch.logisim.fpga.settings.VendorSoftware;
import com.cburch.logisim.util.LineBuffer;
import com.cburch.logisim.util.XmlUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RUntainted;

public class AlteraDownload implements VendorDownload {

  private final VendorSoftware alteraVendor = VendorSoftware.getSoftware(VendorSoftware.VENDOR_ALTERA);
  private final String scriptPath;
  private final String projectPath;
  private final String sandboxPath;
  private final Netlist rootNetList;
  private MappableResourcesContainer mapInfo;
  private final BoardInformation boardInfo;
  private final List<String> entities;
  private final List<String> architectures;
  private final String hdlType;
  private String cablename;
  private final boolean writeToFlash;

  private static final String alteraTclFile = "AlteraDownload.tcl";
  private static final String AlteraCofFile = "AlteraFlash.cof";

  public AlteraDownload(
      String projectPath,
      Netlist rootNetList,
      BoardInformation boardInfo,
      List<String> entities,
      List<String> architectures,
      String hdlType,
      boolean writeToFlash) {
    this.projectPath = projectPath;
    this.sandboxPath = DownloadBase.getDirectoryLocation(projectPath, DownloadBase.SANDBOX_PATH);
    this.scriptPath = DownloadBase.getDirectoryLocation(projectPath, DownloadBase.SCRIPT_PATH);
    this.rootNetList = rootNetList;
    this.boardInfo = boardInfo;
    this.entities = entities;
    this.architectures = architectures;
    this.hdlType = hdlType;
    this.writeToFlash = writeToFlash;
    cablename = "";
  }

  @Override
  public void setMapableResources(MappableResourcesContainer resources) {
    mapInfo = resources;
  }

  @Override
  public int getNumberOfStages() {
    return 3;
  }

  @Override
  public String getStageMessage(int stage) {
    return switch (stage) {
      case 0 -> S.get("AlteraProject");
      case 1 -> S.get("AlteraOptimize");
      case 2 -> S.get("AlteraSyntPRBit");
      default -> "Unknown, bizare";
    };
  }

  @Override
  public ProcessBuilder performStep(int stage) {
    return switch (stage) {
      case 0 -> stage0Project();
      case 1 -> stage1Optimize();
      case 2 -> stage2SprBit();
      default -> null;
    };
  }

  @Override
  public boolean readyForDownload() {
    final boolean SofFile = new File(sandboxPath + ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME + ".sof").exists();
    final boolean PofFile = new File(sandboxPath + ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME + ".pof").exists();
    return SofFile || PofFile;
  }

  @Override
  public ProcessBuilder downloadToBoard() {
    if (writeToFlash && !doFlashing()) return null;
    final java.util.ArrayList<java.lang.String> command = new ArrayList<String>();
    command.add(alteraVendor.getBinaryPath(1));
    command.add("-c");
    command.add(cablename);
    command.add("-m");
    command.add("jtag");
    command.add("-o");
    // if there is no .sof generated, try with the .pof
    if (new File(sandboxPath + ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME + ".sof").exists()) {
      command.add("P;" + ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME + ".sof"
                  + "@" + boardInfo.fpga.getFpgaJTAGChainPosition());
    } else {
      command.add("P;" + ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME + ".pof"
                  + "@" + boardInfo.fpga.getFpgaJTAGChainPosition());
    }
    final java.lang.ProcessBuilder down = new ProcessBuilder(command);
    down.directory(new File(sandboxPath));
    return down;
  }

  private ProcessBuilder stage0Project() {
    final java.util.ArrayList<java.lang.String> command = new ArrayList<String>();
    command.add(alteraVendor.getBinaryPath(0));
    command.add("-t");
    command.add(scriptPath.replace(projectPath, ".." + File.separator) + alteraTclFile);
    final java.lang.ProcessBuilder stage0 = new ProcessBuilder(command);
    stage0.directory(new File(sandboxPath));
    return stage0;
  }

  private ProcessBuilder stage1Optimize() {
    final java.util.ArrayList<java.lang.@RUntainted String> command = new ArrayList<@RUntainted String>();
    command.add(alteraVendor.getBinaryPath(2));
    command.add(ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME);
    command.add("--optimize=area");
    final java.lang.ProcessBuilder stage1 = new ProcessBuilder(command);
    stage1.directory(new File(sandboxPath));
    return stage1;
  }

  private ProcessBuilder stage2SprBit() {
    java.util.ArrayList<java.lang.@RUntainted String> command = new ArrayList<@RUntainted String>();
    command.add(alteraVendor.getBinaryPath(0));
    command.add("--flow");
    command.add("compile");
    command.add(ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME);
    final java.lang.ProcessBuilder stage2 = new ProcessBuilder(command);
    stage2.directory(new File(sandboxPath));
    return stage2;
  }

  @Override
  public boolean createDownloadScripts() {
    java.io.File scriptFile = FileWriter.getFilePointer(scriptPath, alteraTclFile);
    if (scriptFile == null) {
      scriptFile = new File(scriptPath + alteraTclFile);
      return scriptFile.exists();
    }
    final java.lang.String fileType = hdlType.equals(HdlGeneratorFactory.VHDL) ? "VHDL_FILE" : "VERILOG_FILE";
    final com.cburch.logisim.util.LineBuffer contents = LineBuffer.getBuffer();
    contents
        .pair("topLevelName", ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME)
        .pair("fileType", fileType)
        .pair("clock", TickComponentHdlGeneratorFactory.FPGA_CLOCK);

    contents
        .add("""
            # Load Quartus II Tcl Project package
            package require ::quartus::project

            set need_to_close_project 0
            set make_assignments 1

            # Check that the right project is open
            if {[is_project_open]} {
                if {[string compare $quartus(project) "{{topLevelName}}"]} {
                    puts "Project {{topLevelName}} is not open"
                    set make_assignments 0
                }
            } else {
                # Only open if not already open
                if {[project_exists {{topLevelName}}]} {
                    project_open -revision {{topLevelName}} {{topLevelName}}
                } else {
                    project_new -revision {{topLevelName}} {{topLevelName}}
                }
                set need_to_close_project 1
            }
            # Make assignments
            if {$make_assignments} {
            """)
        .add(getAlteraAssignments(boardInfo))
        .add("""

                # Include all entities and gates

            """);
    for (java.lang.String entity : entities) {
      contents.add("    set_global_assignment -name {{fileType}} \"{{1}}\"", entity);
    }
    for (java.lang.String architecture : architectures) {
      contents.add("    set_global_assignment -name {{fileType}} \"{{1}}\"", architecture);
    }
    contents.add("");
    contents.add("    # Map fpga_clk and ionets to fpga pins");
    if (rootNetList.numberOfClockTrees() > 0 || rootNetList.requiresGlobalClockConnection()) {
      contents.add("    set_location_assignment {{1}} -to {{clock}}", boardInfo.fpga.getClockPinLocation());
    }
    contents
        .add(getPinLocStrings())
        .add("""
                # Commit assignments
                export_assignments

                # Close project
                if {$need_to_close_project} {
                    project_close
                }
            }
            """);
    return FileWriter.writeContents(scriptFile, contents.get());
  }

  private List<String> getPinLocStrings() {
    final com.cburch.logisim.util.LineBuffer contents = LineBuffer.getBuffer();

    for (final java.util.ArrayList<java.lang.String> key : mapInfo.getMappableResources().keySet()) {
      final com.cburch.logisim.fpga.data.MapComponent map = mapInfo.getMappableResources().get(key);

      for (int i = 0; i < map.getNrOfPins(); i++) {
        if (map.isMapped(i) && !map.isOpenMapped(i) && !map.isConstantMapped(i) && !map.isInternalMapped(i)) {
          final com.cburch.logisim.util.LineBuffer.Pairs pairs = new LineBuffer.Pairs()
                  .pair("pinLoc", map.getPinLocation(i))
                  .pair("inv", map.isExternalInverted(i) ? "n_" : "")
                  .pair("hdlStr", map.getHdlString(i));
          contents.add("set_location_assignment {{pinLoc}} -to {{inv}}{{hdlStr}}", pairs);
          if (map.requiresPullup(i))
            contents.add("set_instance_assignment -name WEAK_PULL_UP_RESISTOR ON -to {{inv}}{{hdlStr}}", pairs);
        }
      }
    }
    final java.util.Map<java.lang.String,java.lang.String> ledArrayMap = DownloadBase.getLedArrayMaps(mapInfo, rootNetList, boardInfo);
    for (final java.lang.String key : ledArrayMap.keySet())
      contents.add("set_location_assignment {{1}} -to {{2}}", ledArrayMap.get(key), key);
    return contents.getWithIndent(4);
  }

  private static List<String> getAlteraAssignments(BoardInformation currentBoard) {
    final java.lang.String[] pkg = currentBoard.fpga.getPackage().split(" ");
    final char currentBehavior = currentBoard.fpga.getUnusedPinsBehavior();
    final java.lang.String behavior = switch (currentBehavior) {
      case PullBehaviors.PULL_UP -> "PULLUP";
      case PullBehaviors.PULL_DOWN -> "PULLDOWN";
      case PullBehaviors.FLOAT -> "TRI-STATED";
      default -> throw new IllegalStateException("Unexpected value: " + currentBehavior);
    };

    return LineBuffer.getBuffer()
        .pair("assignName", "set_global_assignment -name")
        .add("{{assignName}} FAMILY \"{{1}}\"", currentBoard.fpga.getTechnology())
        .add("{{assignName}} DEVICE {{1}}", currentBoard.fpga.getPart())
        .add("{{assignName}} DEVICE_FILTER_PACKAGE {{1}}", pkg[0])
        .add("{{assignName}} DEVICE_FILTER_PIN_COUNT {{1}}", pkg[1])
        .add("{{assignName}} RESERVE_ALL_UNUSED_PINS \"AS INPUT {{1}}\"", behavior)
        .add("{{assignName}} FMAX_REQUIREMENT \"{{1}}\"", Download.getClockFrequencyString(currentBoard))
        .add("{{assignName}} RESERVE_NCEO_AFTER_CONFIGURATION \"USE AS REGULAR IO\"")
        .add("{{assignName}} CYCLONEII_RESERVE_NCEO_AFTER_CONFIGURATION \"USE AS REGULAR IO\"")
        .getWithIndent();
  }

  @Override
  public boolean isBoardConnected() {
    java.util.ArrayList<java.lang.@RUntainted String> command = new ArrayList<@RUntainted String>();
    command.add(alteraVendor.getBinaryPath(1));
    command.add("--list");
    final java.lang.ProcessBuilder detect = new ProcessBuilder(command);
    detect.directory(new File(sandboxPath));
    java.util.ArrayList<java.lang.String> response = new ArrayList<String>();
    try {
      Reporter.report.print("");
      Reporter.report.print("===");
      Reporter.report.print("===> " + S.get("AlteraDetectDevice"));
      Reporter.report.print("===");
      if (Download.execute(detect, response) != null) return false;
    } catch (IOException | InterruptedException e) {
      return false;
    }
    java.util.List<java.lang.String> devices = getDevices(response);
    if (devices == null) return false;
    if (devices.size() == 1) {
      cablename = devices.get(0);
      return true;
    }
    java.lang.String selection = Download.chooseBoard(devices);
    if (selection == null) return false;
    cablename = selection;
    return true;
  }

  private List<String> getDevices(ArrayList<String> lines) {
    final java.util.ArrayList<java.lang.String> dev = new ArrayList<String>();
    for (java.lang.String line : lines) {
      int n = dev.size() + 1;
      if (!line.matches("^" + n + "\\) .*")) continue;
      line = line.replaceAll("^" + n + "\\) ", "");
      dev.add(line.trim());
    }
    return (dev.isEmpty()) ? null : dev;
  }

  private boolean doFlashing() {
    if (!createCofFile()) {
      Reporter.report.addError(S.get("AlteraFlashError"));
      return false;
    }
    if (!createJicFile()) {
      Reporter.report.addError(S.get("AlteraFlashError"));
      return false;
    }
    if (!loadProgrammerSoftware()) {
      Reporter.report.addError(S.get("AlteraFlashError"));
      return false;
    }
    if (!flashDevice()) {
      Reporter.report.addError(S.get("AlteraFlashError"));
      return false;
    }
    return true;
  }

  private boolean flashDevice() {
    final java.lang.String jicFile = ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME + ".jic";
    Reporter.report.print("==>");
    Reporter.report.print("==> " + S.get("AlteraFlash"));
    Reporter.report.print("==>");
    if (!new File(sandboxPath + jicFile).exists()) {
      Reporter.report.addError(S.get("AlteraFlashError", jicFile));
      return false;
    }
    final com.cburch.logisim.util.LineBuffer command = LineBuffer.getBuffer();
    command
        .add(alteraVendor.getBinaryPath(1))
        .add("-c")
        .add(cablename)
        .add("-m")
        .add("jtag")
        .add("-o")
        .add("P;{{1}}", jicFile);
    final java.lang.ProcessBuilder prog = new ProcessBuilder(command.get());
    prog.directory(new File(sandboxPath));
    try {
      final java.lang.String result = Download.execute(prog, null);
      if (result != null) {
        Reporter.report.addFatalError(S.get("AlteraFlashFailure"));
        return false;
      }
    } catch (IOException | InterruptedException e) {
      Reporter.report.addFatalError(S.get("AlteraFlashFailure"));
      return false;
    }
    return true;
  }

  private boolean loadProgrammerSoftware() {
    final java.lang.String FpgaDevice = stripPackageSpeedSuffix();
    final java.lang.String ProgrammerSofFile = new File(VendorSoftware.getToolPath(VendorSoftware.VENDOR_ALTERA)).getParent()
        + File.separator
        + "common"
        + File.separator
        + "devinfo"
        + File.separator
        + "programmer"
        + File.separator
        + "sfl_"
        + FpgaDevice.toLowerCase()
        + ".sof";
    Reporter.report.print("==>");
    Reporter.report.print("==> " + S.get("AlteraProgSof"));
    Reporter.report.print("==>");
    if (!new File(ProgrammerSofFile).exists()) {
      Reporter.report.addError(S.get("AlteraProgSofError", ProgrammerSofFile));
      return false;
    }
    final com.cburch.logisim.util.LineBuffer command = LineBuffer.getBuffer();
    command.add(alteraVendor.getBinaryPath(1))
            .add("-c")
            .add(cablename)
            .add("-m")
            .add("jtag")
            .add("-o")
            .add("P;{{1}}", ProgrammerSofFile);
    final java.lang.ProcessBuilder prog = new ProcessBuilder(command.get());
    prog.directory(new File(sandboxPath));
    try {
      final java.lang.String result = Download.execute(prog, null);
      if (result != null) {
        Reporter.report.addFatalError(S.get("AlteraProgSofFailure"));
        return false;
      }
    } catch (IOException | InterruptedException e) {
      Reporter.report.addFatalError(S.get("AlteraProgSofFailure"));
      return false;
    }
    return true;
  }

  private String stripPackageSpeedSuffix() {
    /* For the Cyclone IV devices the name used for Syntesis is in form
     * EP4CE15F23C8. For the programmer sof-file (for flash writing) we need to strip
     * the part F23C8. For future supported devices this should be checked.
     */
    final java.lang.String FpgaDevice = boardInfo.fpga.getPart();
    final int index = FpgaDevice.indexOf("F");
    return FpgaDevice.substring(0, index);
  }

  private boolean createJicFile() {
    if (!new File(scriptPath + AlteraCofFile).exists()) {
      Reporter.report.addError(S.get("AlteraNoCof"));
      return false;
    }
    Reporter.report.print("==>");
    Reporter.report.print("==> " + S.get("AlteraJicFile"));
    Reporter.report.print("==>");
    java.util.ArrayList<java.lang.String> command = new ArrayList<String>();
    command.add(alteraVendor.getBinaryPath(3));
    command.add("-c");
    command.add((scriptPath + AlteraCofFile).replace(projectPath, "../"));
    final java.lang.ProcessBuilder jic = new ProcessBuilder(command);
    jic.directory(new File(sandboxPath));
    try {
      final java.lang.String result = Download.execute(jic, null);
      if (result != null) {
        Reporter.report.addFatalError(S.get("AlteraJicFileError"));
        return false;
      }
    } catch (IOException | InterruptedException e) {
      Reporter.report.addFatalError(S.get("AlteraJicFileError"));
      return false;
    }

    return true;
  }

  private boolean createCofFile() {
    if (!new File(sandboxPath + ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME + ".sof").exists()) {
      Reporter.report.addFatalError(S.get("AlteraNoSofFile"));
      return false;
    }
    Reporter.report.print("==>");
    Reporter.report.print("==> " + S.get("AlteraCofFile"));
    Reporter.report.print("==>");
    try {
      final javax.xml.parsers.DocumentBuilderFactory docFactory = XmlUtil.getHardenedBuilderFactory();
      final javax.xml.parsers.DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
      final org.w3c.dom.Document cofFile = docBuilder.newDocument();
      cofFile.setXmlStandalone(true);
      final org.w3c.dom.Element rootElement = cofFile.createElement("cof");
      cofFile.appendChild(rootElement);
      addElement("eprom_name", boardInfo.fpga.getFlashName(), rootElement, cofFile);
      addElement("flash_loader_device", stripPackageSpeedSuffix(), rootElement, cofFile);
      addElement("output_filename",
          sandboxPath + ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME + ".jic",
          rootElement,
          cofFile);
      addElement("n_pages", "1", rootElement, cofFile);
      addElement("width", "1", rootElement, cofFile);
      addElement("mode", "7", rootElement, cofFile);
      final org.w3c.dom.Element sofData = cofFile.createElement("sof_data");
      rootElement.appendChild(sofData);
      addElement("user_name", "Page_0", sofData, cofFile);
      addElement("page_flags", "1", sofData, cofFile);
      final org.w3c.dom.Element bitFile = cofFile.createElement("bit0");
      sofData.appendChild(bitFile);
      addElement("sof_filename",
          sandboxPath + ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME + ".sof",
          bitFile,
          cofFile);
      addElement("version", "10", rootElement, cofFile);
      addElement("create_cvp_file", "0", rootElement, cofFile);
      addElement("create_hps_iocsr", "0", rootElement, cofFile);
      addElement("auto_create_rpd", "0", rootElement, cofFile);
      addElement("rpd_little_endian", "1", rootElement, cofFile);
      final org.w3c.dom.Element options = cofFile.createElement("options");
      rootElement.appendChild(options);
      addElement("map_file", "0", options, cofFile);
      final org.w3c.dom.Element advancedOptions = cofFile.createElement("advanced_options");
      rootElement.appendChild(advancedOptions);
      addElement("ignore_epcs_id_check", "2", advancedOptions, cofFile);
      addElement("ignore_condone_check", "2", advancedOptions, cofFile);
      addElement("plc_adjustment", "0", advancedOptions, cofFile);
      addElement("post_chain_bitstream_pad_bytes", "-1", advancedOptions, cofFile);
      addElement("post_device_bitstream_pad_bytes", "-1", advancedOptions, cofFile);
      addElement("bitslice_pre_padding", "1", advancedOptions, cofFile);
      final javax.xml.transform.TransformerFactory transformerFac = TransformerFactory.newInstance();
      final javax.xml.transform.Transformer transformer = transformerFac.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(OutputKeys.METHOD, "xml");
      transformer.setOutputProperty(OutputKeys.ENCODING, "US-ASCII");
      transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
      final javax.xml.transform.dom.DOMSource source = new DOMSource(cofFile);
      final javax.xml.transform.stream.StreamResult result = new StreamResult(new File(scriptPath + AlteraCofFile));
      transformer.transform(source, result);
    } catch (ParserConfigurationException | TransformerException e) {
      Reporter.report.addError(S.get("AlteraErrorCof"));
      return false;
    }
    return true;
  }

  private void addElement(String elementName, String elementValue, Element root, Document doc) {
    final org.w3c.dom.Element namedElement = doc.createElement(elementName);
    namedElement.appendChild(doc.createTextNode(elementValue));
    root.appendChild(namedElement);
  }

}
