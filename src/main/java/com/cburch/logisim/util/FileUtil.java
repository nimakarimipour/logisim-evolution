/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RPolyTainted;

public final class FileUtil {

  private FileUtil() {
    throw new IllegalStateException("Utility class. No instantiation allowed.");
  }

  public static @RPolyTainted String correctPath(@RPolyTainted String path) {
    return path.endsWith(File.separator) ? path : path + File.separator;
  }

  public static @RPolyTainted File createTmpFile(String content, @RPolyTainted String prefix, @RPolyTainted String suffix)
      throws IOException {
    final java.io.File tmp = File.createTempFile(prefix, suffix);

    try (final java.io.BufferedWriter out = new BufferedWriter(new FileWriter(tmp))) {
      out.write(content, 0, content.length());
    }
    return tmp;
  }

  public static byte[] getBytes(InputStream is) throws IOException {
    int len;
    int size = 1024;
    byte[] buf;

    if (is instanceof ByteArrayInputStream) {
      size = is.available();
      buf = new byte[size];
      len = is.read(buf, 0, size);
    } else {
      final java.io.ByteArrayOutputStream bos = new ByteArrayOutputStream();
      buf = new byte[size];
      while ((len = is.read(buf, 0, size)) != -1) bos.write(buf, 0, len);
      buf = bos.toByteArray();
    }
    return buf;
  }
}
