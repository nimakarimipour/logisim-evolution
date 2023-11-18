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
import com.cburch.logisim.fpga.data.DriveStrength;
import com.cburch.logisim.fpga.data.IoStandards;
import com.cburch.logisim.fpga.data.MappableResourcesContainer;
import com.cburch.logisim.fpga.data.PullBehaviors;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.file.FileWriter;
import com.cburch.logisim.fpga.gui.Reporter;
import com.cburch.logisim.fpga.hdlgenerator.TickComponentHdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.ToplevelHdlGeneratorFactory;
import com.cburch.logisim.fpga.settings.VendorSoftware;
import com.cburch.logisim.util.LineBuffer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RUntainted;

public class XilinxDownload implements VendorDownload {

  private final VendorSoftware xilinxVendor = VendorSoftware.getSoftware(VendorSoftware.VENDOR_XILINX);
  private final String scriptPath;
  private final String projectPath;
  private final String sandboxPath;
  private final String ucfPath;
  private final Netlist rootNetList;
  private MappableResourcesContainer mapInfo;
  private final BoardInformation boardInfo;
  private final List<String> entities;
  private final List<String> architectures;
  private final String HdlType;
  private final String bitfileExt;
  private final boolean isCpld;
  private final boolean writeToFlash;

  private static final String VHDL_LIST_FILE = "XilinxVHDLList.prj";
  private static final String SCRIPT_FILE = "XilinxScript.cmd";
  private static final String UCF_FILE = "XilinxConstraints.ucf";
  private static final String DOWNLOAD_FILE = "XilinxDownload";
  private static final String MCS_FILE = "XilinxProm.mcs";

  private static final Integer BUFFER_SIZE = 16 * 1024;

  public XilinxDownload(
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
    this.ucfPath = DownloadBase.getDirectoryLocation(projectPath, DownloadBase.UCF_PATH);
    this.rootNetList = rootNetList;
    this.boardInfo = boardInfo;
    this.entities = entities;
    this.architectures = architectures;
    this.HdlType = hdlType;
    this.writeToFlash = writeToFlash;

    final java.lang.String part = boardInfo.fpga.getPart().toUpperCase();
    isCpld = part.startsWith("XC2C") || part.startsWith("XA2C") || part.startsWith("XCR3") || part.startsWith("XC9500") || part.startsWith("XA9500");
    bitfileExt = isCpld ? "jed" : "bit";
  }

  @Override
  public int getNumberOfStages() {
    return 5;
  }

  @Override
  public String getStageMessage(int stage) {
    return switch (stage) {
      case 0 -> S.get("XilinxSynth");
      case 1 -> S.get("XilinxContraints");
      case 2 -> S.get("XilinxMap");
      case 3 -> S.get("XilinxPAR");
      case 4 -> S.get("XilinxBit");
      default -> "unknown";
    };
  }

  @Override
  public ProcessBuilder performStep(int stage) {
    return switch (stage) {
      case 0 -> stage0Synth();
      case 1 -> stage1Constraints();
      case 2 -> stage2Map();
      case 3 -> stage3Par();
      case 4 -> stage4Bit();
      default -> null;
    };
  }

  @Override
  public boolean readyForDownload() {
    return new File(sandboxPath + ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME + "." + bitfileExt).exists();
  }

  @Override
  public ProcessBuilder downloadToBoard() {
    if (!boardInfo.fpga.isUsbTmcDownloadRequired()) {
      java.util.ArrayList<java.lang.@RUntainted String> command = new ArrayList<@RUntainted String>();
      command.add(xilinxVendor.getBinaryPath(5));
      command.add("-batch");
      command.add(scriptPath.replace(projectPath, "../") + File.separator + DOWNLOAD_FILE);
      final java.lang.ProcessBuilder xilinx = new ProcessBuilder(command);
      xilinx.directory(new File(sandboxPath));
      return xilinx;
    } else {
      Reporter.report.clearConsole();
      /* Here we do the USBTMC Download */
      boolean usbtmcdevice = new File("/dev/usbtmc0").exists();
      if (!usbtmcdevice) {
        Reporter.report.addFatalError(S.get("XilinxUsbTmc"));
        return null;
      }
      java.io.File bitfile = new File(sandboxPath + ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME + "." + bitfileExt);
      byte[] bitfileBuffer = new byte[BUFFER_SIZE];
      int bitfileBufferSize = 0;
      BufferedInputStream bitfileIn;
      try {
        bitfileIn = new BufferedInputStream(new FileInputStream(bitfile));
      } catch (FileNotFoundException e) {
        Reporter.report.addFatalError(S.get("XilinxOpenFailure", bitfile));
        return null;
      }
      java.io.File usbtmc = new File("/dev/usbtmc0");
      BufferedOutputStream usbtmcOut;
      try {
        usbtmcOut = new BufferedOutputStream(new FileOutputStream(usbtmc));
        usbtmcOut.write("FPGA ".getBytes());
        bitfileBufferSize = bitfileIn.read(bitfileBuffer, 0, BUFFER_SIZE);
        while (bitfileBufferSize > 0) {
          usbtmcOut.write(bitfileBuffer, 0, bitfileBufferSize);
          bitfileBufferSize = bitfileIn.read(bitfileBuffer, 0, BUFFER_SIZE);
        }
        usbtmcOut.close();
        bitfileIn.close();
      } catch (IOException e) {
        Reporter.report.addFatalError(S.get("XilinxUsbTmcError"));
      }
    }
    return null;
  }

  @Override
  public boolean createDownloadScripts() {
    final java.lang.String jtagPos = String.valueOf(boardInfo.fpga.getFpgaJTAGChainPosition());
    java.io.File scriptFile = FileWriter.getFilePointer(scriptPath, SCRIPT_FILE);
    java.io.File vhdlListFile = FileWriter.getFilePointer(scriptPath, VHDL_LIST_FILE);
    java.io.File ucfFile = FileWriter.getFilePointer(ucfPath, UCF_FILE);
    java.io.File downloadFile = FileWriter.getFilePointer(scriptPath, DOWNLOAD_FILE);
    if (scriptFile == null || vhdlListFile == null || ucfFile == null || downloadFile == null) {
      scriptFile = new File(scriptPath + SCRIPT_FILE);
      vhdlListFile = new File(scriptPath + VHDL_LIST_FILE);
      ucfFile = new File(ucfPath + UCF_FILE);
      downloadFile = new File(scriptPath + DOWNLOAD_FILE);
      return scriptFile.exists()
          && vhdlListFile.exists()
          && ucfFile.exists()
          && downloadFile.exists();
    }
    final com.cburch.logisim.util.LineBuffer contents = LineBuffer.getBuffer()
            .pair("JTAGPos", jtagPos)
            .pair("fileExt", bitfileExt)
            .pair("fileBaseName", ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME)
            .pair("mcsFile", scriptPath + File.separator + MCS_FILE)
            .pair("hdlType", HdlType.toUpperCase());

    for (java.lang.String entity : entities) contents.add("{{hdlType}} work \"{{1}}\"", entity);
    for (java.lang.String arch : architectures) contents.add("{{hdlType}} work \"{{1}}\"", arch);
    if (!FileWriter.writeContents(vhdlListFile, contents.get())) return false;

    contents
          .clear()
          .add(
              "run -top {{1}} -ofn logisim.ngc -ofmt NGC -ifn {{2}}{{3}} -ifmt mixed -p {{4}}",
              ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME,
              scriptPath.replace(projectPath, "../"),
              VHDL_LIST_FILE,
              getFpgaDeviceString(boardInfo));

    if (!FileWriter.writeContents(scriptFile, contents.get())) return false;

    contents.clear();
    contents.add("setmode -bscan");

    if (writeToFlash && boardInfo.fpga.isFlashDefined()) {
      if (boardInfo.fpga.getFlashName() == null) {
        Reporter.report.addFatalError(S.get("XilinxFlashMissing", boardInfo.getBoardName()));
      }

      contents.pair("flashPos", String.valueOf(boardInfo.fpga.getFlashJTAGChainPosition()))
              .pair("flashName", boardInfo.fpga.getFlashName())
              .add("""
                setmode -pff
                setSubMode -pffserial
                addPromDevice -p {{JTAGPos}} -size 0 -name {{flashName}}
                addDesign -version 0 -name "0"
                addDeviceChain -index 0
                addDevice -p {{JTAGPos}} -file {{fileBaseName}}.{{fileExt}}
                generate -format mcs -fillvalue FF -output {{mcsFile}}
                setMode -bs
                setCable -port auto
                identify
                assignFile -p {{flashPos}} -file {{mcsFile}}
                program -p {{flashPos}} -e -v
                """);
    } else {
      contents.add("setcable -p auto").add("identify");
      if (!isCpld) {
        contents.add("""
            assignFile -p {{JTAGPos}} -file {{fileBaseName}}.{{fileExt}}
            program -p {{JTAGPos}} -onlyFpga
            """);
      } else {
        contents.add("""
            assignFile -p {{JTAGPos}} -file logisim.{{fileExt}}
            program -p {{JTAGPos}} -e
            """);
      }
    }
    contents.add("quit");
    if (!FileWriter.writeContents(downloadFile, contents.get())) return false;

    contents.clear();
    if (rootNetList.numberOfClockTrees() > 0 || rootNetList.requiresGlobalClockConnection()) {
      contents
          .pair("clock", TickComponentHdlGeneratorFactory.FPGA_CLOCK)
          .pair("clockFreq", Download.getClockFrequencyString(boardInfo))
          .pair("clockPin", getXilinxClockPin(boardInfo))
          .add("""
            NET "{{clock}}" {{clockPin}} ;
            NET "{{clock}}" TNM_NET = "{{clock}}" ;
            TIMESPEC "TS_{{clock}}" = PERIOD "{{clock}}" {{clockFreq}} HIGH 50 % ;
            """);
    }
    contents.add(getPinLocStrings());
    return FileWriter.writeContents(ucfFile, contents.get());
  }

  private ArrayList<String> getPinLocStrings() {
    java.util.ArrayList<java.lang.String> contents = new ArrayList<String>();
    java.lang.StringBuilder temp = new StringBuilder();
    for (java.util.ArrayList<java.lang.String> key : mapInfo.getMappableResources().keySet()) {
      com.cburch.logisim.fpga.data.MapComponent map = mapInfo.getMappableResources().get(key);
      for (int i = 0; i < map.getNrOfPins(); i++) {
        if (map.isMapped(i) && !map.isOpenMapped(i) && !map.isConstantMapped(i) && !map.isInternalMapped(i)) {
          temp.setLength(0);
          temp.append("NET \"");
          if (map.isExternalInverted(i)) temp.append("n_");
          temp.append(map.getHdlString(i)).append("\" ");
          temp.append("LOC = \"").append(map.getPinLocation(i)).append("\" ");
          final com.cburch.logisim.fpga.data.FpgaIoInformationContainer info = map.getFpgaInfo(i);
          if (info != null) {
            if (info.getPullBehavior() != PullBehaviors.UNKNOWN
                && info.getPullBehavior() != PullBehaviors.FLOAT) {
              temp.append("| ")
                  .append(PullBehaviors.getConstrainedPullString(info.getPullBehavior()))
                  .append(" ");
            }
            if (info.getDrive() != DriveStrength.UNKNOWN
                && info.getDrive() != DriveStrength.DEFAULT_STENGTH) {
              temp.append("| DRIVE = ")
                  .append(DriveStrength.getConstrainedDriveStrength(info.getDrive())).append(" ");
            }
            if (info.getIoStandard() != IoStandards.UNKNOWN
                && info.getIoStandard() != IoStandards.DEFAULT_STANDARD) {
              temp.append("| IOSTANDARD = ")
                  .append(IoStandards.getConstraintedIoStandard(info.getIoStandard()))
                  .append(" ");
            }
          }
          temp.append(";");
          contents.add(temp.toString());
        }
      }
    }
    final java.util.Map<java.lang.String,java.lang.String> LedArrayMap = DownloadBase.getLedArrayMaps(mapInfo, rootNetList, boardInfo);
    for (java.lang.String key : LedArrayMap.keySet()) {
      contents.add("NET \"" + LedArrayMap.get(key) + "\" LOC=\"" + key + "\";");
    }
    return contents;
  }

  @Override
  public void setMapableResources(MappableResourcesContainer resources) {
    mapInfo = resources;
  }

  private ProcessBuilder stage0Synth() {
    final List<@RUntainted String> command = new ArrayList<>();
    command.add(xilinxVendor.getBinaryPath(0));
    command.add("-ifn");
    command.add(scriptPath.replace(projectPath, "../") + File.separator + SCRIPT_FILE);
    command.add("-ofn");
    command.add("logisim.log");
    final java.lang.ProcessBuilder stage0 = new ProcessBuilder(command);
    stage0.directory(new File(sandboxPath));
    return stage0;
  }

  private ProcessBuilder stage1Constraints() {
    final List<@RUntainted String> command = new ArrayList<>();
    command.add(xilinxVendor.getBinaryPath(1));
    command.add("-intstyle");
    command.add("ise");
    command.add("-uc");
    command.add(ucfPath.replace(projectPath, "../") + File.separator + UCF_FILE);
    command.add("logisim.ngc");
    command.add("logisim.ngd");
    final java.lang.ProcessBuilder stage1 = new ProcessBuilder(command);
    stage1.directory(new File(sandboxPath));
    return stage1;
  }

  private ProcessBuilder stage2Map() {
    if (isCpld) return null; /* mapping is skipped for the CPLD target*/
    final List<@RUntainted String> command = new ArrayList<>();
    command
        .add(xilinxVendor.getBinaryPath(2));
       command .add("-intstyle");
    command  .add("ise");
    command.add("-o");
    command.add("logisim_map");
    command.add("logisim.ngd");
    final java.lang.ProcessBuilder stage2 = new ProcessBuilder(command);
    stage2.directory(new File(sandboxPath));
    return stage2;
  }

  private ProcessBuilder stage3Par() {
    final List<@RUntainted String> command = new ArrayList<>();
    if (!isCpld) {
      command.add(xilinxVendor.getBinaryPath(3));
      command .add("-w");
      command .add("-intstyle");
      command .add("ise");
      command .add("-ol");
      command .add("high");
      command .add("logisim_map");
      command .add("logisim_par");
      command .add("logisim_map.pcf");
    } else {
      final java.lang.String pinPullBehavior = switch (boardInfo.fpga.getUnusedPinsBehavior()) {
        case PullBehaviors.PULL_UP -> "pullup";
        case PullBehaviors.PULL_DOWN -> "pulldown";
        default -> "float";
      };
      final com.cburch.logisim.fpga.data.FpgaClass fpga = boardInfo.fpga;
      command
          .add(xilinxVendor.getBinaryPath(6));
      command.add("-p");
      command .add(String.format("{{1}}-{{2}}-{{3}}", fpga.getPart().toUpperCase(), fpga.getSpeedGrade(), fpga.getPackage().toUpperCase()));
      command .add("-intstyle");
      command .add("ise");
          /* TODO: do correct termination type */
      command .add("-terminate");
      command .add(pinPullBehavior);
      command .add("-loc");
      command .add("on");
      command .add("-log");
      command .add("logisim_cpldfit.log");
      command .add("logisim.ngd");
    }
    final java.lang.ProcessBuilder stage3 = new ProcessBuilder(command);
    stage3.directory(new File(sandboxPath));
    return stage3;
  }

  private ProcessBuilder stage4Bit() {
    final List<@RUntainted String> command = new ArrayList<>();
    if (!isCpld) {
      command.add(xilinxVendor.getBinaryPath(4));
      command.add("-w");
      if (boardInfo.fpga.getUnusedPinsBehavior() == PullBehaviors.PULL_UP) command.add("-g");
      command.add("UnusedPin:PULLUP");
      if (boardInfo.fpga.getUnusedPinsBehavior() == PullBehaviors.PULL_DOWN) command.add("-g");
      command.add("UnusedPin:PULLDOWN");
      command.add("-g");
      command.add("StartupClk:CCLK");
      command.add("logisim_par");
      command.add(String.format("{{1}}.bit", ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME));
    } else {
      command.add(xilinxVendor.getBinaryPath(7));
      command.add("-i");
      command.add("logisim.vm6");
    }
    final java.lang.ProcessBuilder stage4 = new ProcessBuilder(command);
    stage4.directory(new File(sandboxPath));
    return stage4;
  }

  private static String getFpgaDeviceString(BoardInformation currentBoard) {
    final com.cburch.logisim.fpga.data.FpgaClass fpga = currentBoard.fpga;
    return String.format("%s-%s-%s", fpga.getPart(), fpga.getPackage(), fpga.getSpeedGrade());
  }

  private static String getXilinxClockPin(BoardInformation currentBoard) {
    final java.lang.StringBuilder result = new StringBuilder();
    result.append("LOC = \"").append(currentBoard.fpga.getClockPinLocation()).append("\"");
    if (currentBoard.fpga.getClockPull() == PullBehaviors.PULL_UP) {
      result.append(" | PULLUP");
    }
    if (currentBoard.fpga.getClockPull() == PullBehaviors.PULL_DOWN) {
      result.append(" | PULLDOWN");
    }
    if (currentBoard.fpga.getClockStandard() != IoStandards.DEFAULT_STANDARD
        && currentBoard.fpga.getClockStandard() != IoStandards.UNKNOWN) {
      result.append(" | IOSTANDARD = ")
          .append(IoStandards.BEHAVIOR_STRINGS[currentBoard.fpga.getClockStandard()]);
    }
    return result.toString();
  }

  @Override
  public boolean isBoardConnected() {
    // TODO: Detect if a board is connected, and in case of multiple boards select the one that should be used
    return true;
  }

}
