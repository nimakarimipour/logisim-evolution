/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.file;

import static com.cburch.logisim.analyze.Strings.S;

import com.cburch.logisim.analyze.data.CsvInterpretor;
import com.cburch.logisim.analyze.data.CsvParameter;
import com.cburch.logisim.analyze.gui.CsvReadParameterDialog;
import com.cburch.logisim.analyze.model.AnalyzerModel;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import javax.swing.JFrame;
import javax.swing.filechooser.FileFilter;

public class TruthtableCsvFile {

  public static final FileFilter FILE_FILTER =
      new TruthtableFileFilter(S.getter("tableCsvFileFilter"), ".csv");
  public static final char DEFAULT_SEPARATOR = ',';
  public static final char DEFAULT_QUOTE = '"';

  public static void doSave(File file, AnalyzerModel model) throws IOException {
    final com.cburch.logisim.analyze.model.VariableList inputs = model.getInputs();
    final com.cburch.logisim.analyze.model.VariableList outputs = model.getOutputs();
    if (inputs.vars.isEmpty() || outputs.vars.isEmpty()) return;
    try (PrintStream out = new PrintStream(file)) {
      final com.cburch.logisim.analyze.model.TruthTable tt = model.getTruthTable();
      tt.compactVisibleRows();
      for (int i = 0; i < inputs.vars.size(); i++) {
        final com.cburch.logisim.analyze.model.Var cur = inputs.vars.get(i);
        final java.lang.String name = cur.width == 1 ? cur.name : cur.name + "[" + (cur.width - 1) + "..0]";
        out.print(DEFAULT_QUOTE + name + DEFAULT_QUOTE + DEFAULT_SEPARATOR);
        for (int j = 1; j < cur.width; j++) out.print(DEFAULT_SEPARATOR);
      }
      out.print(DEFAULT_QUOTE + "|" + DEFAULT_QUOTE);
      for (int i = 0; i < outputs.vars.size(); i++) {
        out.print(DEFAULT_SEPARATOR);
        final com.cburch.logisim.analyze.model.Var cur = outputs.vars.get(i);
        final java.lang.String name = cur.width == 1 ? cur.name : cur.name + "[" + (cur.width - 1) + "..0]";
        out.print(DEFAULT_QUOTE + name + DEFAULT_QUOTE);
        for (int j = 1; j < cur.width; j++) out.print(DEFAULT_SEPARATOR);
      }
      out.println();
      for (int row = 0; row < tt.getVisibleRowCount(); row++) {
        for (int i = 0; i < inputs.bits.size(); i++) {
          final com.cburch.logisim.analyze.model.Entry entry = tt.getVisibleInputEntry(row, i);
          out.print(entry.getDescription() + DEFAULT_SEPARATOR);
        }
        out.print(DEFAULT_QUOTE + "|" + DEFAULT_QUOTE);
        for (int i = 0; i < outputs.bits.size(); i++) {
          out.print(DEFAULT_SEPARATOR);
          final com.cburch.logisim.analyze.model.Entry entry = tt.getVisibleOutputEntry(row, i);
          out.print(entry.getDescription());
        }
        out.println();
      }
    }
  }

  public static void doLoad(File file, AnalyzerModel model, JFrame parentFrame) throws IOException {
    final com.cburch.logisim.analyze.data.CsvParameter param = new CsvParameter();
    new CsvReadParameterDialog(param, file, parentFrame);
    if (!param.isValid()) return;
    final com.cburch.logisim.analyze.data.CsvInterpretor cin = new CsvInterpretor(file, param, parentFrame);
    cin.getTruthTable(model);
  }
}
