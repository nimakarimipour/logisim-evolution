/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import javax.swing.filechooser.FileFilter;

/**
 * Code taken from Cornell's version of Logisim: http://www.cs.cornell.edu/courses/cs3410/2015sp/
 */
public class TestVector {

  private static class TestVectorFilter extends FileFilter {

    @Override
    public boolean accept(File f) {
      if (!f.isFile()) return true;

      String name = f.getName();
      int i = name.lastIndexOf('.');
      return (i > 0 && name.substring(i).equalsIgnoreCase(".txt"));
    }

    @Override
    public String getDescription() {
      return "Logisim-evolution Test Vector (*.txt)";
    }
  }

  private class TestVectorReader {
    private final BufferedReader in;
    private StringTokenizer curLine;

    public TestVectorReader(BufferedReader in) throws IOException {
      this.in = in;
      curLine = findNonemptyLine();
    }

    private StringTokenizer findNonemptyLine() throws IOException {
      java.lang.String line = in.readLine();

      while (line != null) {
        final int i = line.indexOf('#');
        if (i >= 0) line = line.substring(0, i);
        final java.util.StringTokenizer ret = new StringTokenizer(line);
        if (ret.hasMoreTokens()) return ret;
        line = in.readLine();
      }

      return null;
    }

    public void parse() throws IOException {
      if (curLine == null) throw new IOException("TestVector format error: empty file");

      parseHeader();
      data = new ArrayList<>();
      curLine = findNonemptyLine();

      while (curLine != null) {
        parseData();
        curLine = findNonemptyLine();
      }
    }

    private void parseData() throws IOException {
      final com.cburch.logisim.data.Value[] vals = new Value[columnName.length];
      for (int i = 0; i < columnName.length; i++) {
        final java.lang.String t = curLine.nextToken();

        try {
          vals[i] = Value.fromLogString(columnWidth[i], t);
        } catch (Exception e) {
          throw new IOException("Test Vector data format error: " + e.getMessage());
        }
        if (data.isEmpty()) columnRadix[i] = Value.radixOfLogString(columnWidth[i], t);
      }
      if (curLine.hasMoreTokens())
        throw new IOException("Test Vector data format error: " + curLine.nextToken());
      data.add(vals);
    }

    private void parseHeader() throws IOException {
      final int n = curLine.countTokens();
      columnName = new String[n];
      columnWidth = new BitWidth[n];
      columnRadix = new int[n];

      for (int i = 0; i < n; i++) {
        columnRadix[i] = 2;
        final java.lang.String t = (String) curLine.nextElement();
        int s = t.indexOf('[');

        if (s < 0) {
          columnName[i] = t;
          columnWidth[i] = BitWidth.ONE;
        } else {
          final int e = t.indexOf(']');

          if (e != t.length() - 1 || s == 0 || e == s + 1)
            throw new IOException("Test Vector header format error: bad spec: " + t);

          columnName[i] = t.substring(0, s);
          int w = 0;
          try {
            w = Integer.parseInt(t.substring(s + 1, e));
          } catch (NumberFormatException ignored) {
          }

          if (w < 1 || w > 64)
            throw new IOException("Test Vector header format error: bad width: " + t);
          columnWidth[i] = BitWidth.create(w);
        }
      }
    }
  }

  public static final FileFilter FILE_FILTER = new TestVectorFilter();
  public String[] columnName;
  public BitWidth[] columnWidth;
  public int[] columnRadix;

  public List<Value[]> data;

  public TestVector(File src) throws IOException {
    try (final java.io.BufferedReader in = new BufferedReader(new FileReader(src))) {
      final com.cburch.logisim.data.TestVector.TestVectorReader r = new TestVectorReader(in);
      r.parse();
    }
  }

  public TestVector(String filename) throws IOException {
    this(new File(filename));
  }
}
