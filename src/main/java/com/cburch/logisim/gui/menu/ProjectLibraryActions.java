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

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Library;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JScrollPane;

public class ProjectLibraryActions {
  private ProjectLibraryActions() {}

  public static void doLoadBuiltinLibrary(Project proj) {
    final com.cburch.logisim.file.LogisimFile file = proj.getLogisimFile();
    final java.util.List<com.cburch.logisim.tools.Library> baseBuilt = file.getLoader().getBuiltin().getLibraries();
    final java.util.ArrayList<com.cburch.logisim.tools.Library> builtins = new ArrayList<>(baseBuilt);
    builtins.removeAll(file.getLibraries());
    if (builtins.isEmpty()) {
      OptionPane.showMessageDialog(
          proj.getFrame(),
          S.get("loadBuiltinNoneError"),
          S.get("loadBuiltinErrorTitle"),
          OptionPane.INFORMATION_MESSAGE);
      return;
    }
    final com.cburch.logisim.gui.menu.ProjectLibraryActions.LibraryJList list = new LibraryJList(builtins);
    final javax.swing.JScrollPane listPane = new JScrollPane(list);
    int action =
        OptionPane.showConfirmDialog(
            proj.getFrame(),
            listPane,
            S.get("loadBuiltinDialogTitle"),
            OptionPane.OK_CANCEL_OPTION,
            OptionPane.QUESTION_MESSAGE);
    if (action == OptionPane.OK_OPTION) {
      final com.cburch.logisim.tools.Library[] libs = list.getSelectedLibraries();
      if (libs != null)
        proj.doAction(LogisimFileActions.loadLibraries(libs, proj.getLogisimFile()));
    }
  }

  public static void doLoadJarLibrary(Project proj) {
    final com.cburch.logisim.file.Loader loader = proj.getLogisimFile().getLoader();
    final javax.swing.JFileChooser chooser = loader.createChooser();
    chooser.setDialogTitle(S.get("loadJarDialogTitle"));
    chooser.setFileFilter(Loader.JAR_FILTER);
    int check = chooser.showOpenDialog(proj.getFrame());
    if (check == JFileChooser.APPROVE_OPTION) {
      final java.io.File f = chooser.getSelectedFile();
      String className = null;

      // try to retrieve the class name from the "Library-Class"
      // attribute in the manifest. This section of code was contributed
      // by Christophe Jacquet (Request Tracker #2024431).
      try (final java.util.jar.JarFile jarFile = new JarFile(f)) {
        final java.util.jar.Manifest manifest = jarFile.getManifest();
        className = manifest.getMainAttributes().getValue("Library-Class");
      } catch (IOException e) {
        // if opening the JAR file failed, do nothing
      }

      // if the class name was not found, go back to the good old dialog
      if (className == null) {
        className =
            OptionPane.showInputDialog(
                proj.getFrame(),
                S.get("jarClassNamePrompt"),
                S.get("jarClassNameTitle"),
                OptionPane.QUESTION_MESSAGE);
        // if user canceled selection, abort
        if (className == null) return;
      }

      final com.cburch.logisim.tools.Library lib = loader.loadJarLibrary(f, className);
      if (lib != null) {
        proj.doAction(LogisimFileActions.loadLibrary(lib, proj.getLogisimFile()));
      }
    }
  }

  public static void doLoadLogisimLibrary(Project proj) {
    final com.cburch.logisim.file.Loader loader = proj.getLogisimFile().getLoader();
    final javax.swing.JFileChooser chooser = loader.createChooser();
    chooser.setDialogTitle(S.get("loadLogisimDialogTitle"));
    chooser.setFileFilter(Loader.LOGISIM_FILTER);
    final int check = chooser.showOpenDialog(proj.getFrame());
    if (check == JFileChooser.APPROVE_OPTION) {
      final java.io.File f = chooser.getSelectedFile();
      final com.cburch.logisim.tools.Library lib = loader.loadLogisimLibrary(f);
      if (lib != null) {
        proj.doAction(LogisimFileActions.loadLibrary(lib, proj.getLogisimFile()));
      }
    }
  }

  public static void doUnloadLibraries(Project proj) {
    final com.cburch.logisim.file.LogisimFile file = proj.getLogisimFile();
    final java.util.ArrayList<com.cburch.logisim.tools.Library> canUnload = new ArrayList<Library>();
    for (final com.cburch.logisim.tools.Library lib : file.getLibraries()) {
      final java.lang.String message = file.getUnloadLibraryMessage(lib);
      if (message == null) canUnload.add(lib);
    }
    if (canUnload.isEmpty()) {
      OptionPane.showMessageDialog(
          proj.getFrame(),
          S.get("unloadNoneError"),
          S.get("unloadErrorTitle"),
          OptionPane.INFORMATION_MESSAGE);
      return;
    }
    final com.cburch.logisim.gui.menu.ProjectLibraryActions.LibraryJList list = new LibraryJList(canUnload);
    final javax.swing.JScrollPane listPane = new JScrollPane(list);
    final int action =
        OptionPane.showConfirmDialog(
            proj.getFrame(),
            listPane,
            S.get("unloadLibrariesDialogTitle"),
            OptionPane.OK_CANCEL_OPTION,
            OptionPane.QUESTION_MESSAGE);
    if (action == OptionPane.OK_OPTION) {
      final com.cburch.logisim.tools.Library[] libs = list.getSelectedLibraries();
      if (libs != null) proj.doAction(LogisimFileActions.unloadLibraries(libs));
    }
  }

  public static void doUnloadLibrary(Project proj, Library lib) {
    final java.lang.String message = proj.getLogisimFile().getUnloadLibraryMessage(lib);
    if (message != null) {
      OptionPane.showMessageDialog(
          proj.getFrame(), message, S.get("unloadErrorTitle"), OptionPane.ERROR_MESSAGE);
    } else {
      proj.doAction(LogisimFileActions.unloadLibrary(lib));
    }
  }

  private static class BuiltinOption {
    final Library lib;

    BuiltinOption(Library lib) {
      this.lib = lib;
    }

    @Override
    public String toString() {
      return lib.getDisplayName();
    }
  }

  @SuppressWarnings("rawtypes")
  private static class LibraryJList extends JList {
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unchecked")
    LibraryJList(List<Library> libraries) {
      final java.util.ArrayList<com.cburch.logisim.gui.menu.ProjectLibraryActions.BuiltinOption> options = new ArrayList<BuiltinOption>();
      for (final com.cburch.logisim.tools.Library lib : libraries) {
        options.add(new BuiltinOption(lib));
      }
      setListData(options.toArray());
    }

    Library[] getSelectedLibraries() {
      final java.lang.Object[] selected = getSelectedValuesList().toArray();
      if (selected != null && selected.length > 0) {
        final com.cburch.logisim.tools.Library[] libs = new Library[selected.length];
        for (int i = 0; i < selected.length; i++) {
          libs[i] = ((BuiltinOption) selected[i]).lib;
        }
        return libs;
      } else {
        return null;
      }
    }
  }
}
