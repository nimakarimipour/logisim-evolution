/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.tools;

import static com.cburch.logisim.tools.Strings.S;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.LoadedLibrary;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.generic.OptionPane;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LibraryTools {

  private LibraryTools() {
    throw new IllegalStateException("Utility class. No instantiation allowed.");
  }

  public static void showErrors(String libName, Map<String, String> messages) {
    OptionPane.showMessageDialog(
        null,
        message(libName, messages),
        S.get("LibLoadErrors") + " " + libName + " !",
        OptionPane.ERROR_MESSAGE);
  }

  private static String message(String libName, Map<String, String> messages) {
    java.lang.String message = "";
    int item = 0;
    for (final java.lang.String myerror : messages.keySet()) {
      item++;
      message = message.concat(item + ") " + messages.get(myerror) + " \"" + myerror + "\".\n");
    }
    return message;
  }

  public static void buildToolList(Library lib, Set<String> tools) {
    for (final com.cburch.logisim.tools.Tool tool : lib.getTools()) {
      tools.add(tool.getName().toUpperCase());
    }
    for (final com.cburch.logisim.tools.Library sublib : lib.getLibraries()) {
      buildToolList(sublib, tools);
    }
  }

  public static boolean buildToolList(Library lib, Map<String, AddTool> tools) {
    boolean ret = true;
    if (!"Base".equals(lib.getName())) {
      for (final com.cburch.logisim.tools.Tool tool1 : lib.getTools()) {
        if (tools.containsKey(tool1.getName().toUpperCase())) {
          ret = false;
        } else {
          tools.put(tool1.getName().toUpperCase(), (AddTool) tool1);
        }
      }
    }
    for (final com.cburch.logisim.tools.Library sublib : lib.getLibraries()) {
      ret &= buildToolList(sublib, tools);
    }
    return ret;
  }

  // FIXME: why `upperCaseName` even matters here if we do case insensitive comparision?
  public static Circuit getCircuitFromLibs(Library lib, String upperCaseName) {
    if (lib instanceof LogisimFile llib) {
      for (final com.cburch.logisim.circuit.Circuit circ : llib.getCircuits()) {
        if (circ.getName().equalsIgnoreCase(upperCaseName)) return circ;
      }
    }

    Circuit ret = null;
    for (final com.cburch.logisim.tools.Library libs : lib.getLibraries()) {
      if (libs instanceof LoadedLibrary lib1) {
        ret = getCircuitFromLibs(lib1.getBase(), upperCaseName);
      } else ret = getCircuitFromLibs(libs, upperCaseName);
      if (ret != null) return ret;
    }
    return null;
  }

  // FIXME: method name is odd.
  public static List<String> libraryCanBeMerged(Set<String> sourceTools, Set<String> newTools) {
    final java.util.ArrayList<java.lang.String> ret = new ArrayList<String>();
    for (final java.lang.String This : newTools) {
      if (sourceTools.contains(This)) {
        ret.add(This);
      }
    }
    return ret;
  }

  // Why name case matters that it is reflected in argument `uppercasedNames` name?
  public static Map<String, String> getToolLocation(Library lib, String location, List<String> upercasedNames) {
    final java.util.Iterator<? extends com.cburch.logisim.tools.Tool> toolIter = lib.getTools().iterator();
    final java.util.HashMap<java.lang.String,java.lang.String> ret = new HashMap<String, String>();
    final java.lang.String MyLocation = (location.isEmpty()) ? lib.getName() : location + "->" + lib.getName();
    while (toolIter.hasNext()) {
      final com.cburch.logisim.tools.Tool tool = toolIter.next();
      if (upercasedNames.contains(tool.getName().toUpperCase())) {
        ret.put(tool.getName(), MyLocation);
      }
    }
    for (final com.cburch.logisim.tools.Library sublib : lib.getLibraries()) {
      ret.putAll(getToolLocation(sublib, MyLocation, upercasedNames));
    }
    return ret;
  }

  public static boolean isLibraryConform(Library lib, Set<String> names, Set<String> tools, Map<String, String> error) {
    final java.util.Iterator<? extends com.cburch.logisim.tools.Tool> toolIter = lib.getTools().iterator();
    boolean hasErrors = false;
    while (toolIter.hasNext()) {
      final com.cburch.logisim.tools.Tool tool = toolIter.next();
      if (tools.contains(tool.getName().toUpperCase())) {
        hasErrors = true;
        if (!error.containsKey(tool.getName())) {
          error.put(tool.getName(), S.get("LibraryHasDuplicatedTools"));
        }
      }
      tools.add(tool.getName().toUpperCase());
    }
    for (final com.cburch.logisim.tools.Library sublib : lib.getLibraries()) {
      if (names.contains(sublib.getName().toUpperCase())) {
        hasErrors = true;
        if (!error.containsKey(sublib.getName())) {
          error.put(sublib.getName(), S.get("LibraryHasDuplicatedSublibraries"));
        }
      }
      names.add(sublib.getName().toUpperCase());
      hasErrors |= !isLibraryConform(sublib, names, tools, error);
    }
    return !hasErrors;
  }

  public static void buildLibraryList(Library lib, Map<String, Library> names) {
    names.put(lib.getName().toUpperCase(), lib);
    for (final com.cburch.logisim.tools.Library sublib : lib.getLibraries()) {
      buildLibraryList(sublib, names);
    }
  }

  public static void removePresentLibraries(Library lib, Map<String, Library> knownLibs, boolean addToSet) {
    /* we work top -> down */
    final java.util.HashSet<java.lang.String> toBeRemoved = new HashSet<String>();
    for (final com.cburch.logisim.tools.Library sublib : lib.getLibraries()) {
      if (knownLibs.containsKey(sublib.getName().toUpperCase())) {
        toBeRemoved.add(sublib.getName());
      } else if (addToSet) {
        knownLibs.put(sublib.getName().toUpperCase(), sublib);
      }
    }
    for (final java.lang.String remove : toBeRemoved) {
      lib.removeLibrary(remove);
    }
    for (final com.cburch.logisim.tools.Library sublib : lib.getLibraries())
      removePresentLibraries(sublib, knownLibs, addToSet);
  }
}
