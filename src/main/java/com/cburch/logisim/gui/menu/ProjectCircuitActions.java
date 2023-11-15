/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.menu;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.contracts.BaseWindowFocusListenerContract;
import com.cburch.logisim.analyze.gui.Analyzer;
import com.cburch.logisim.analyze.gui.AnalyzerManager;
import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.Var;
import com.cburch.logisim.circuit.Analyze;
import com.cburch.logisim.circuit.AnalyzeException;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.fpga.designrulecheck.CorrectLabel;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.util.SyntaxChecker;
import com.cburch.logisim.vhdl.base.VhdlContent;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RUntainted;

public class ProjectCircuitActions {
  private ProjectCircuitActions() {
    // dummy, private
  }

  private static void analyzeError(Project proj, String message) {
    OptionPane.showMessageDialog(
        proj.getFrame(), message, S.get("analyzeErrorTitle"), OptionPane.ERROR_MESSAGE);
  }

  private static void configureAnalyzer(
      Project proj,
      Circuit circuit,
      Analyzer analyzer,
      Map<Instance, String> pinNames,
      ArrayList<Var> inputVars,
      ArrayList<Var> outputVars) {
    analyzer.getModel().setVariables(inputVars, outputVars);

    // If there are no inputs or outputs, we stop with that tab selected
    if (inputVars.size() == 0 || outputVars.size() == 0) {
      analyzer.setSelectedTab(Analyzer.IO_TAB);
      return;
    }

    // Attempt to show the corresponding expression
    try {
      Analyze.computeExpression(analyzer.getModel(), circuit, pinNames);
      analyzer.setSelectedTab(Analyzer.EXPRESSION_TAB);
      return;
    } catch (AnalyzeException ex) {
      OptionPane.showMessageDialog(
          proj.getFrame(),
          ex.getMessage(),
          S.get("analyzeNoExpressionTitle"),
          OptionPane.INFORMATION_MESSAGE);
    }

    // As a backup measure, we compute a truth table.
    Analyze.computeTable(analyzer.getModel(), proj, circuit, pinNames);
    analyzer.setSelectedTab(Analyzer.TABLE_TAB);
  }

  public static void doAddCircuit(Project proj) {
    final java.lang.String name = promptForCircuitName(proj.getFrame(), proj.getLogisimFile(), "");
    if (name != null) {
      JLabel error = null;
      /* Checking for valid names */
      if (name.isEmpty()) {
        error = new JLabel(S.get("circuitNameMissingError"));
      } else if (CorrectLabel.isKeyword(name, false)) {
        error = new JLabel("\"" + name + "\": " + S.get("circuitNameKeyword"));
      } else if (!SyntaxChecker.isVariableNameAcceptable(name, false)) {
        error = new JLabel("\"" + name + "\": " + S.get("circuitNameInvalidName"));
      } else if (nameIsInUse(proj, name)) {
        error = new JLabel("\"" + name + "\": " + S.get("circuitNameExists"));
      }
      if (error != null) {
        OptionPane.showMessageDialog(
            proj.getFrame(), error, S.get("circuitCreateTitle"), OptionPane.ERROR_MESSAGE);
      } else {
        final com.cburch.logisim.circuit.Circuit circuit = new Circuit(name, proj.getLogisimFile(), proj);
        proj.doAction(LogisimFileActions.addCircuit(circuit));
        proj.setCurrentCircuit(circuit);
      }
    }
  }

  private static boolean nameIsInUse(Project proj, String name) {
    for (Library mylib : proj.getLogisimFile().getLibraries()) {
      if (nameIsInLibraries(mylib, name)) return true;
    }
    for (AddTool mytool : proj.getLogisimFile().getTools()) {
      if (name.equalsIgnoreCase(mytool.getName())) return true;
    }
    return false;
  }

  private static boolean nameIsInLibraries(Library lib, String name) {
    for (final com.cburch.logisim.tools.Library myLib : lib.getLibraries()) {
      if (nameIsInLibraries(myLib, name)) return true;
    }
    for (final com.cburch.logisim.tools.Tool myTool : lib.getTools()) {
      if (name.equalsIgnoreCase(myTool.getName())) return true;
    }
    return false;
  }

  public static void doAddVhdl(Project proj) {
    final java.lang.String name = promptForVhdlName(proj.getFrame(), proj.getLogisimFile(), "");
    if (name != null) {
      final com.cburch.logisim.vhdl.base.VhdlContent content = VhdlContent.create(name, proj.getLogisimFile());
      if (content != null) {
        proj.doAction(LogisimFileActions.addVhdl(content));
        proj.setCurrentHdlModel(content);
      }
    }
  }

  public static void doImportVhdl(Project proj) {
    final java.lang.String vhdl = proj.getLogisimFile().getLoader().vhdlImportChooser(proj.getFrame());
    if (vhdl == null) return;

    final com.cburch.logisim.vhdl.base.VhdlContent content = VhdlContent.parse(null, vhdl, proj.getLogisimFile());
    if (content != null) return;
    if (VhdlContent.labelVHDLInvalidNotify(content.getName(), proj.getLogisimFile())) return;

    proj.doAction(LogisimFileActions.addVhdl(content));
    proj.setCurrentHdlModel(content);
  }

  public static void doAnalyze(Project proj, Circuit circuit) {
    final java.util.SortedMap<com.cburch.logisim.instance.Instance,java.lang.String> pinNames = Analyze.getPinLabels(circuit);
    final java.util.ArrayList<com.cburch.logisim.analyze.model.Var> inputVars = new ArrayList<Var>();
    final java.util.ArrayList<com.cburch.logisim.analyze.model.Var> outputVars = new ArrayList<Var>();
    int numInputs = 0;
    int numOutputs = 0;
    for (final java.util.Map.Entry<com.cburch.logisim.instance.Instance,java.lang.String> entry : pinNames.entrySet()) {
      final com.cburch.logisim.instance.Instance pin = entry.getKey();
      final boolean isInput = Pin.FACTORY.isInputPin(pin);
      final int width = pin.getAttributeValue(StdAttr.WIDTH).getWidth();
      final com.cburch.logisim.analyze.model.Var v = new Var(entry.getValue(), width);
      if (isInput) {
        inputVars.add(v);
        numInputs += width;
      } else {
        outputVars.add(v);
        numOutputs += width;
      }
    }
    if (numInputs > AnalyzerModel.MAX_INPUTS) {
      analyzeError(proj, S.get("analyzeTooManyInputsError", "" + AnalyzerModel.MAX_INPUTS));
      return;
    }
    if (numOutputs > AnalyzerModel.MAX_OUTPUTS) {
      analyzeError(proj, S.get("analyzeTooManyOutputsError", "" + AnalyzerModel.MAX_OUTPUTS));
      return;
    }

    final com.cburch.logisim.analyze.gui.Analyzer analyzer = AnalyzerManager.getAnalyzer(proj.getFrame());
    analyzer.getModel().setCurrentCircuit(proj, circuit);
    configureAnalyzer(proj, circuit, analyzer, pinNames, inputVars, outputVars);
    if (!analyzer.isVisible()) {
      analyzer.setVisible(true);
    }
    analyzer.toFront();
  }

  public static void doMoveCircuit(Project proj, Circuit cur, int delta) {
    final com.cburch.logisim.tools.AddTool tool = proj.getLogisimFile().getAddTool(cur);
    if (tool != null) {
      final int oldPos = proj.getLogisimFile().indexOfCircuit(cur);
      final int newPos = oldPos + delta;
      final int toolsCount = proj.getLogisimFile().getTools().size();
      if (newPos >= 0 && newPos < toolsCount) {
        proj.doAction(LogisimFileActions.moveCircuit(tool, newPos));
      }
    }
  }

  public static void doRemoveCircuit(Project proj, Circuit circuit) {
    if (proj.getLogisimFile().getCircuits().size() == 1) {
      OptionPane.showMessageDialog(
          proj.getFrame(),
          S.get("circuitRemoveLastError"),
          S.get("circuitRemoveErrorTitle"),
          OptionPane.ERROR_MESSAGE);
    } else if (!proj.getDependencies().canRemove(circuit)) {
      OptionPane.showMessageDialog(
          proj.getFrame(),
          S.get("circuitRemoveUsedError"),
          S.get("circuitRemoveErrorTitle"),
          OptionPane.ERROR_MESSAGE);
    } else {
      proj.doAction(LogisimFileActions.removeCircuit(circuit));
    }
  }

  public static void doRemoveVhdl(Project proj, VhdlContent vhdl) {
    if (!proj.getDependencies().canRemove(vhdl)) {
      OptionPane.showMessageDialog(
          proj.getFrame(),
          S.get("circuitRemoveUsedError"),
          S.get("circuitRemoveErrorTitle"),
          OptionPane.ERROR_MESSAGE);
    } else {
      proj.doAction(LogisimFileActions.removeVhdl(vhdl));
    }
  }

  public static void doSetAsMainCircuit(Project proj, Circuit circuit) {
    proj.doAction(LogisimFileActions.setMainCircuit(circuit));
  }

  /**
   * Ask the user for the name of the new circuit to create. If the name is valid, then it returns
   * it, otherwise it displays an error message and returns null.
   *
   * @param frame Project's frame
   * @param lib Project's logisim file
   * @param initialValue Default suggested value (can be empty if no initial value)
   */
  private static String promptForCircuitName(JFrame frame, Library lib, String initialValue) {
    return promptForNewName(frame, lib, initialValue, false);
  }

  private static String promptForVhdlName(JFrame frame, LogisimFile file, String initialValue) {
    final java.lang.String name = promptForNewName(frame, file, initialValue, true);
    if (name == null) return null;
    if (VhdlContent.labelVHDLInvalidNotify(name, file)) return null;
    return name;
  }

  private static String promptForNewName(
      JFrame frame, Library lib, String initialValue, boolean vhdl) {
    String title;
    String prompt;
    if (vhdl) {
      title = S.get("vhdlNameDialogTitle");
      prompt = S.get("vhdlNamePrompt");
    } else {
      title = S.get("circuitNameDialogTitle");
      prompt = S.get("circuitNamePrompt");
    }
    final javax.swing.JTextField field = new JTextField(15);
    field.setText(initialValue);
    final java.awt.GridBagLayout gb = new GridBagLayout();
    final java.awt.GridBagConstraints gc = new GridBagConstraints();
    final javax.swing.JPanel strut = new JPanel(null);
    strut.setPreferredSize(new Dimension(3 * field.getPreferredSize().width / 2, 0));
    gc.gridx = 0;
    gc.gridy = GridBagConstraints.RELATIVE;
    gc.weightx = 1.0;
    gc.fill = GridBagConstraints.NONE;
    gc.anchor = GridBagConstraints.LINE_START;
    final javax.swing.JLabel label = new JLabel(prompt);
    gb.setConstraints(label, gc);
    final javax.swing.JPanel panel = new JPanel(gb);
    panel.add(label);
    gb.setConstraints(field, gc);
    panel.add(field);
    final javax.swing.JLabel error = new JLabel(" ");
    gb.setConstraints(error, gc);
    panel.add(error);
    gb.setConstraints(strut, gc);
    panel.add(strut);
    final javax.swing.JOptionPane pane =
        new JOptionPane(panel, OptionPane.QUESTION_MESSAGE, OptionPane.OK_CANCEL_OPTION);
    pane.setInitialValue(field);
    final javax.swing.JDialog dlog = pane.createDialog(frame, title);
    dlog.addWindowFocusListener(
        new BaseWindowFocusListenerContract() {
          @Override
          public void windowGainedFocus(WindowEvent arg0) {
            field.requestFocus();
          }
        });

    field.selectAll();
    dlog.pack();
    dlog.setVisible(true);
    field.requestFocusInWindow();

    final java.lang.Object action = pane.getValue();
    if (!(action instanceof Integer) || (Integer) action != OptionPane.OK_OPTION) {
      return null;
    }

    return field.getText().trim();
  }
}
