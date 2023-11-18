/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.start;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.analyze.model.TruthTable;
import com.cburch.logisim.analyze.model.Var;
import com.cburch.logisim.circuit.Analyze;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.FileStatistics;
import com.cburch.logisim.file.LoadFailedException;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.hex.HexFile;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.io.Keyboard;
import com.cburch.logisim.std.io.Tty;
import com.cburch.logisim.std.memory.Ram;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.util.UniquelyNamedThread;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RUntainted;

public class TtyInterface {

  public static final int FORMAT_TABLE = 1;
  public static final int FORMAT_SPEED = 2;
  public static final int FORMAT_TTY = 4;
  public static final int FORMAT_HALT = 8;
  public static final int FORMAT_STATISTICS = 16;
  public static final int FORMAT_TABLE_TABBED = 32;
  public static final int FORMAT_TABLE_CSV = 64;
  public static final int FORMAT_TABLE_BIN = 128;
  public static final int FORMAT_TABLE_HEX = 256;
  static final Logger logger = LoggerFactory.getLogger(TtyInterface.class);
  private static boolean lastIsNewline = true;

  private static int countDigits(int num) {
    int digits = 1;
    int lessThan = 10;
    while (num >= lessThan) {
      digits++;
      lessThan *= 10;
    }
    return digits;
  }

  private static void displaySpeed(long tickCount, long elapse) {
    double hertz = (double) tickCount / elapse * 1000.0;
    double precision;
    if (hertz >= 100) precision = 1.0;
    else if (hertz >= 10) precision = 0.1;
    else if (hertz >= 1) precision = 0.01;
    else if (hertz >= 0.01) precision = 0.0001;
    else precision = 0.0000001;
    hertz = (int) (hertz / precision) * precision;
    java.lang.String hertzStr = hertz == (int) hertz ? "" + (int) hertz : "" + hertz;
    System.out.printf(S.get("ttySpeedMsg") + "\n", hertzStr, tickCount, elapse);
  }

  private static void displayStatistics(LogisimFile file, Circuit circuit) {
    final com.cburch.logisim.file.FileStatistics stats = FileStatistics.compute(file, circuit);
    final com.cburch.logisim.file.FileStatistics.Count total = stats.getTotalWithSubcircuits();
    int maxName = 0;
    for (final com.cburch.logisim.file.FileStatistics.Count count : stats.getCounts()) {
      final int nameLength = count.getFactory().getDisplayName().length();
      if (nameLength > maxName) maxName = nameLength;
    }
    final java.lang.String fmt =
        "%"
            + countDigits(total.getUniqueCount())
            + "d\t"
            + "%"
            + countDigits(total.getRecursiveCount())
            + "d\t";
    final java.lang.String fmtNormal = fmt + "%-" + maxName + "s\t%s\n";
    for (final com.cburch.logisim.file.FileStatistics.Count count : stats.getCounts()) {
      final com.cburch.logisim.tools.Library lib = count.getLibrary();
      final java.lang.String libName = lib == null ? "-" : lib.getDisplayName();
      System.out.printf(
          fmtNormal,
          count.getUniqueCount(),
          count.getRecursiveCount(),
          count.getFactory().getDisplayName(),
          libName);
    }
    final com.cburch.logisim.file.FileStatistics.Count totalWithout = stats.getTotalWithoutSubcircuits();
    System.out.printf(
        fmt + "%s\n",
        totalWithout.getUniqueCount(),
        totalWithout.getRecursiveCount(),
        S.get("statsTotalWithout"));
    System.out.printf(
        fmt + "%s\n",
        total.getUniqueCount(),
        total.getRecursiveCount(),
        S.get("statsTotalWith"));
  }

  private static void displayTableRow(ArrayList<Value> prevOutputs, ArrayList<Value> curOutputs) {
    boolean shouldPrint = false;
    if (prevOutputs == null) {
      shouldPrint = true;
    } else {
      for (int i = 0; i < curOutputs.size(); i++) {
        final com.cburch.logisim.data.Value a = prevOutputs.get(i);
        final com.cburch.logisim.data.Value b = curOutputs.get(i);
        if (!a.equals(b)) {
          shouldPrint = true;
          break;
        }
      }
    }
    if (shouldPrint) {
      for (int i = 0; i < curOutputs.size(); i++) {
        if (i != 0) System.out.print("\t");
        System.out.print(curOutputs.get(i));
      }
      System.out.println();
    }
  }

  private static boolean displayTableRow(boolean showHeader, ArrayList<Value> prevOutputs, ArrayList<Value> curOutputs,
                                         ArrayList<String> headers, ArrayList<String> formats, int format) {
    boolean shouldPrint = false;
    if (prevOutputs == null) {
      shouldPrint = true;
    } else {
      for (int i = 0; i < curOutputs.size(); i++) {
        final com.cburch.logisim.data.Value a = prevOutputs.get(i);
        final com.cburch.logisim.data.Value b = curOutputs.get(i);
        if (!a.equals(b)) {
          shouldPrint = true;
          break;
        }
      }
    }
    if (shouldPrint) {
      java.lang.String sep = "";
      if ((format & FORMAT_TABLE_TABBED) != 0) sep = "\t";
      else if ((format & FORMAT_TABLE_CSV) != 0) sep = ",";
      else // if ((format & FORMAT_TABLE_PRETTY) != 0)
        sep = " ";
      if (showHeader) {
        for (int i = 0; i < headers.size(); i++) {
          if ((format & FORMAT_TABLE_TABBED) != 0) formats.add("%s");
          else if ((format & FORMAT_TABLE_CSV) != 0) formats.add("%s");
          else { // if ((format & FORMAT_TABLE_PRETTY) != 0)
            int w = headers.get(i).length();
            w = Math.max(w, valueFormat(curOutputs.get(i), format).length());
            formats.add("%" + w + "s");
          }
        }
        for (int i = 0; i < headers.size(); i++) {
          if (i != 0) System.out.print(sep);
          System.out.printf(formats.get(i), headers.get(i));
        }
        System.out.println();
      }
      for (int i = 0; i < curOutputs.size(); i++) {
        if (i != 0) System.out.print(sep);
        System.out.printf(formats.get(i), valueFormat(curOutputs.get(i), format));
      }
      System.out.println();
    }
    return shouldPrint;
  }

  private static String valueFormat(Value v, int format) {
    if ((format & FORMAT_TABLE_BIN) != 0) {
      // everything in binary
      return v.toString();
    } else if ((format & FORMAT_TABLE_HEX) != 0) {
      // everything thing in hex, no prefixes
      return v.toHexString();
    } else {
      // under 6 bits or less in binary, no spaces
      // otherwise in hex, with prefix
      if (v.getWidth() <= 6) return v.toBinaryString();
      else return "0x" + v.toHexString();
    }
  }

  private static void ensureLineTerminated() {
    if (!lastIsNewline) {
      lastIsNewline = true;
      System.out.print('\n');
    }
  }

  private static boolean loadRam(CircuitState circState, File loadFile) throws IOException {
    if (loadFile == null) return false;

    boolean found = false;
    for (final com.cburch.logisim.comp.Component comp : circState.getCircuit().getNonWires()) {
      if (comp.getFactory() instanceof Ram ramFactory) {
        final com.cburch.logisim.instance.InstanceState ramState = circState.getInstanceState(comp);
        final com.cburch.logisim.std.memory.MemContents m = ramFactory.getContents(ramState);
        HexFile.open(m, loadFile);
        found = true;
      }
    }

    for (final com.cburch.logisim.circuit.CircuitState sub : circState.getSubStates()) {
      found |= loadRam(sub, loadFile);
    }
    return found;
  }

  private static boolean saveRam(CircuitState circState, File saveFile) throws IOException {
    if (saveFile == null) return false;

    boolean found = false;
    for (final com.cburch.logisim.comp.Component comp : circState.getCircuit().getNonWires()) {
      if (comp.getFactory() instanceof Ram ramFactory) {
        final com.cburch.logisim.instance.InstanceState ramState = circState.getInstanceState(comp);
        final com.cburch.logisim.std.memory.MemContents m = ramFactory.getContents(ramState);
        HexFile.save(saveFile, m, "v3.0 hex words plain");
        found = true;
      }
    }

    for (final com.cburch.logisim.circuit.CircuitState sub : circState.getSubStates()) {
      found |= saveRam(sub, saveFile);
    }
    return found;
  }

  private static boolean prepareForTty(CircuitState circState, ArrayList<InstanceState> keybStates) {
    boolean found = false;
    for (final com.cburch.logisim.comp.Component comp : circState.getCircuit().getNonWires()) {
      final Object factory = comp.getFactory();
      if (factory instanceof Tty ttyFactory) {
        final com.cburch.logisim.instance.InstanceState ttyState = circState.getInstanceState(comp);
        ttyFactory.sendToStdout(ttyState);
        found = true;
      } else if (factory instanceof Keyboard) {
        keybStates.add(circState.getInstanceState(comp));
        found = true;
      }
    }

    for (CircuitState sub : circState.getSubStates()) {
      found |= prepareForTty(sub, keybStates);
    }
    return found;
  }

  public static void run(Startup args) {
    final java.io.File fileToOpen = args.getFilesToOpen().get(0);
    final com.cburch.logisim.file.Loader loader = new Loader(null);
    LogisimFile file;
    try {
      file = loader.openLogisimFile(fileToOpen, args.getSubstitutions());
    } catch (LoadFailedException e) {
      logger.error("{}", S.get("ttyLoadError", fileToOpen.getName()));
      System.exit(-1);
      return;
    }
    final com.cburch.logisim.proj.Project proj = new Project(file);
    if (args.isFpgaDownload()) {
      if (!args.fpgaDownload(proj)) System.exit(-1);
    }

    final java.lang.String circuitToTest = args.getCircuitToTest();
    final com.cburch.logisim.circuit.Circuit circuit = (circuitToTest == null || circuitToTest.length() == 0)
        ? file.getMainCircuit()
        : file.getCircuit(circuitToTest);

    int format = args.getTtyFormat();
    if ((format & FORMAT_STATISTICS) != 0) {
      format &= ~FORMAT_STATISTICS;
      displayStatistics(file, circuit);
    }
    if (format == 0) { // no simulation remaining to perform, so just exit
      System.exit(0);
    }

    final java.util.SortedMap<com.cburch.logisim.instance.Instance,java.lang.String> pinNames = Analyze.getPinLabels(circuit);
    final java.util.ArrayList<com.cburch.logisim.instance.Instance> outputPins = new ArrayList<Instance>();
    final java.util.ArrayList<com.cburch.logisim.instance.Instance> inputPins = new ArrayList<Instance>();
    Instance haltPin = null;
    for (final java.util.Map.Entry<com.cburch.logisim.instance.Instance,java.lang.String> entry : pinNames.entrySet()) {
      final com.cburch.logisim.instance.Instance pin = entry.getKey();
      final java.lang.String pinName = entry.getValue();
      if (Pin.FACTORY.isInputPin(pin)) {
        inputPins.add(pin);
      } else {
        outputPins.add(pin);
        if (pinName.equals("halt")) {
          haltPin = pin;
        }
      }
    }
    if (haltPin == null && (format & FORMAT_TABLE) != 0) {
      doTableAnalysis(proj, circuit, pinNames, format);
      return;
    }

    CircuitState circState = new CircuitState(proj, circuit);
    // we have to do our initial propagation before the simulation starts -
    // it's necessary to populate the circuit with substates.
    circState.getPropagator().propagate();
    if (args.getLoadFile() != null) {
      try {
        final boolean loaded = loadRam(circState, args.getLoadFile());
        if (!loaded) {
          logger.error("{}", S.get("loadNoRamError"));
          System.exit(-1);
        }
      } catch (IOException e) {
        logger.error("{}: {}", S.get("loadIoError"), e.toString());
        System.exit(-1);
      }
    }
    final int ttyFormat = args.getTtyFormat();
    final int simCode = runSimulation(circState, outputPins, haltPin, ttyFormat);

    if (args.getSaveFile() != null) {
      try {
        final boolean saved = saveRam(circState, args.getSaveFile());
        if (!saved) {
          logger.error("{}", S.get("saveNoRamError"));
          System.exit(-1);
        }
      } catch (IOException e) {
        logger.error("{}: {}", S.get("saveIoError"), e.toString());
        System.exit(-1);
      }
    }

    System.exit(simCode);
  }

  private static int doTableAnalysis(Project proj, Circuit circuit, Map<Instance, String> pinLabels, int format) {

    final java.util.ArrayList<com.cburch.logisim.instance.Instance> inputPins = new ArrayList<Instance>();
    final java.util.ArrayList<com.cburch.logisim.analyze.model.Var> inputVars = new ArrayList<Var>();
    final java.util.ArrayList<java.lang.String> inputNames = new ArrayList<String>();
    final java.util.ArrayList<com.cburch.logisim.instance.Instance> outputPins = new ArrayList<Instance>();
    final java.util.ArrayList<com.cburch.logisim.analyze.model.Var> outputVars = new ArrayList<Var>();
    final java.util.ArrayList<java.lang.String> outputNames = new ArrayList<String>();
    final java.util.ArrayList<java.lang.String> formats = new ArrayList<String>();
    for (final java.util.Map.Entry<com.cburch.logisim.instance.Instance,java.lang.String> entry : pinLabels.entrySet()) {
      final com.cburch.logisim.instance.Instance pin = entry.getKey();
      final int width = pin.getAttributeValue(StdAttr.WIDTH).getWidth();
      final com.cburch.logisim.analyze.model.Var var = new Var(entry.getValue(), width);
      if (Pin.FACTORY.isInputPin(pin)) {
        inputPins.add(pin);
        for (final java.lang.String name : var) inputNames.add(name);
        inputVars.add(var);
      } else {
        outputPins.add(pin);
        for (final java.lang.String name : var) outputNames.add(name);
        outputVars.add(var);
      }
    }

    final java.util.ArrayList<java.lang.String> headers = new ArrayList<String>();
    final java.util.ArrayList<com.cburch.logisim.instance.Instance> pinList = new ArrayList<Instance>();
    /* input pins first */
    for (final java.util.Map.Entry<com.cburch.logisim.instance.Instance,java.lang.String> entry : pinLabels.entrySet()) {
      final com.cburch.logisim.instance.Instance pin = entry.getKey();
      final java.lang.String pinName = entry.getValue();
      if (Pin.FACTORY.isInputPin(pin)) {
        headers.add(pinName);
        pinList.add(pin);
      }
    }
    /* output pins last */
    for (final java.util.Map.Entry<com.cburch.logisim.instance.Instance,java.lang.String> entry : pinLabels.entrySet()) {
      final com.cburch.logisim.instance.Instance pin = entry.getKey();
      final java.lang.String pinName = entry.getValue();
      if (!Pin.FACTORY.isInputPin(pin)) {
        headers.add(pinName);
        pinList.add(pin);
      }
    }

    final int inputCount = inputNames.size();
    final int rowCount = 1 << inputCount;

    boolean needTableHeader = true;
    final java.util.HashMap<com.cburch.logisim.instance.Instance,com.cburch.logisim.data.Value> valueMap = new HashMap<Instance, Value>();
    for (int i = 0; i < rowCount; i++) {
      valueMap.clear();
      final com.cburch.logisim.circuit.CircuitState circuitState = new CircuitState(proj, circuit);
      int incol = 0;
      for (final com.cburch.logisim.instance.Instance pin : inputPins) {
        final int width = pin.getAttributeValue(StdAttr.WIDTH).getWidth();
        final com.cburch.logisim.data.Value[] v = new Value[width];
        for (int b = width - 1; b >= 0; b--) {
          final boolean value = TruthTable.isInputSet(i, incol++, inputCount);
          v[b] = value ? Value.TRUE : Value.FALSE;
        }
        final com.cburch.logisim.instance.InstanceState pinState = circuitState.getInstanceState(pin);
        Pin.FACTORY.setValue(pinState, Value.create(v));
        valueMap.put(pin, Value.create(v));
      }

      final com.cburch.logisim.circuit.Propagator prop = circuitState.getPropagator();
      prop.propagate();
      /*
       * TODO for the SimulatorPrototype class do { prop.step(); } while
       * (prop.isPending());
       */
      // TODO: Search for circuit state

      for (final com.cburch.logisim.instance.Instance pin : outputPins) {
        if (prop.isOscillating()) {
          final com.cburch.logisim.data.BitWidth width = pin.getAttributeValue(StdAttr.WIDTH);
          valueMap.put(pin, Value.createError(width));
        } else {
          final com.cburch.logisim.instance.InstanceState pinState = circuitState.getInstanceState(pin);
          final com.cburch.logisim.data.Value outValue = Pin.FACTORY.getValue(pinState);
          valueMap.put(pin, outValue);
        }
      }
      final java.util.ArrayList<com.cburch.logisim.data.Value> currValues = new ArrayList<Value>();
      for (final com.cburch.logisim.instance.Instance pin : pinList) {
        currValues.add(valueMap.get(pin));
      }
      displayTableRow(needTableHeader, null, currValues, headers, formats, format);
      needTableHeader = false;
    }

    return 0;
  }

  private static int runSimulation(CircuitState circState, ArrayList<Instance> outputPins, Instance haltPin, int format) {
    final boolean showTable = (format & FORMAT_TABLE) != 0;
    final boolean showSpeed = (format & FORMAT_SPEED) != 0;
    final boolean showTty = (format & FORMAT_TTY) != 0;
    final boolean showHalt = (format & FORMAT_HALT) != 0;

    ArrayList<InstanceState> keyboardStates = null;
    StdinThread stdinThread = null;
    if (showTty) {
      keyboardStates = new ArrayList<>();
      final boolean ttyFound = prepareForTty(circState, keyboardStates);
      if (!ttyFound) {
        logger.error("{}", S.get("ttyNoTtyError"));
        System.exit(-1);
      }
      if (keyboardStates.isEmpty()) {
        keyboardStates = null;
      } else {
        stdinThread = new StdinThread();
        stdinThread.start();
      }
    }

    int retCode = 0;
    long tickCount = 0;
    final long start = System.currentTimeMillis();
    boolean halted = false;
    ArrayList<Value> prevOutputs = null;
    final com.cburch.logisim.circuit.Propagator prop = circState.getPropagator();
    while (true) {
      final java.util.ArrayList<com.cburch.logisim.data.Value> curOutputs = new ArrayList<Value>();
      for (final com.cburch.logisim.instance.Instance pin : outputPins) {
        final com.cburch.logisim.instance.InstanceState pinState = circState.getInstanceState(pin);
        final com.cburch.logisim.data.Value val = Pin.FACTORY.getValue(pinState);
        if (pin == haltPin) {
          halted |= val.equals(Value.TRUE);
        } else if (showTable) {
          curOutputs.add(val);
        }
      }
      if (showTable) {
        displayTableRow(prevOutputs, curOutputs);
      }

      if (halted) {
        retCode = 0; // normal exit
        break;
      }
      if (prop.isOscillating()) {
        retCode = 1; // abnormal exit
        break;
      }
      if (keyboardStates != null) {
        final char[] buffer = stdinThread.getBuffer();
        if (buffer != null) {
          for (final com.cburch.logisim.instance.InstanceState keyState : keyboardStates) {
            Keyboard.addToBuffer(keyState, buffer);
          }
        }
      }
      prevOutputs = curOutputs;
      tickCount++;
      prop.toggleClocks();
      prop.propagate();
    }
    final long elapse = System.currentTimeMillis() - start;
    if (showTty) ensureLineTerminated();
    if (showHalt || retCode != 0) {
      if (retCode == 0) {
        logger.error("{}", S.get("ttyHaltReasonPin"));
      } else if (retCode == 1) {
        logger.error("{}", S.get("ttyHaltReasonOscillation"));
      }
    }
    if (showSpeed) {
      displaySpeed(tickCount, elapse);
    }
    return retCode;
  }

  public static void sendFromTty(char c) {
    lastIsNewline = c == '\n';
    System.out.print(c);
  }

  // It's possible to avoid using the separate thread using
  // System.in.available(),
  // but this doesn't quite work because on some systems, the keyboard input
  // is not interactively echoed until System.in.read() is invoked.
  private static class StdinThread extends UniquelyNamedThread {
    private final LinkedList<char[]> queue; // of char[]

    public StdinThread() {
      super("TtyInterface-StdInThread");
      queue = new LinkedList<>();
    }

    public char[] getBuffer() {
      synchronized (queue) {
        return queue.isEmpty() ? null : queue.removeFirst();
      }
    }

    @Override
    public void run() {
      final java.io.InputStreamReader stdin = new InputStreamReader(System.in);
      final char[] buffer = new char[32];
      while (true) {
        try {
          int nbytes = stdin.read(buffer);
          if (nbytes > 0) {
            final char[] add = new char[nbytes];
            System.arraycopy(buffer, 0, add, 0, nbytes);
            synchronized (queue) {
              queue.addLast(add);
            }
          }
        } catch (IOException ignored) {
        }
      }
    }
  }
}
