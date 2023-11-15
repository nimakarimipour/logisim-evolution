/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.proj;

import static com.cburch.logisim.proj.Strings.S;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.LoadFailedException;
import com.cburch.logisim.file.LoadedLibrary;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.start.SplashScreen;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.LibraryTools;
import com.cburch.logisim.util.JFileChoosers;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RUntainted;

public final class ProjectActions {
  private static final String FILE_NAME_FORMAT_ERROR = "FileNameError";
  private static final String FILE_NAME_KEYWORD_ERROR = "ExistingToolName";

  private ProjectActions() {}

  private static class CreateFrame implements Runnable {
    private final Loader loader;
    private final Project proj;
    private final boolean isStartupScreen;

    public CreateFrame(Loader loader, Project proj, boolean isStartup) {
      this.loader = loader;
      this.proj = proj;
      this.isStartupScreen = isStartup;
    }

    @Override
    public void run() {
      try {
        final com.cburch.logisim.gui.main.Frame frame = createFrame(null, proj);
        frame.setVisible(true);
        frame.toFront();
        frame.getCanvas().requestFocus();
        loader.setParent(frame);
        if (isStartupScreen) {
          proj.setStartupScreen(true);
        }
      } catch (Exception e) {
        final java.io.StringWriter result = new StringWriter();
        final java.io.PrintWriter printWriter = new PrintWriter(result);
        e.printStackTrace(printWriter);
        OptionPane.showMessageDialog(null, result.toString());
        System.exit(-1);
      }
    }
  }

  /**
   * Returns true if the filename contains valid characters only, that is, alphanumeric characters
   * and underscores.
   */
  private static boolean checkValidFilename(
      String filename, Project proj, HashMap<String, String> errors) {
    boolean isOk = true;
    java.util.HashMap<java.lang.String,com.cburch.logisim.tools.Library> tempSet = new HashMap<String, Library>();
    java.util.HashSet<java.lang.String> forbiddenNames = new HashSet<String>();
    LibraryTools.buildLibraryList(proj.getLogisimFile(), tempSet);
    LibraryTools.buildToolList(proj.getLogisimFile(), forbiddenNames);
    forbiddenNames.addAll(tempSet.keySet());
    java.util.regex.Pattern pattern = Pattern.compile("[^a-z\\d_.]", Pattern.CASE_INSENSITIVE);
    java.util.regex.Matcher matcher = pattern.matcher(filename);
    if (matcher.find()) {
      isOk = false;
      errors.put(FILE_NAME_FORMAT_ERROR, S.get("InvalidFileFormatError"));
    }
    if (forbiddenNames.contains(filename.toUpperCase())) {
      isOk = false;
      errors.put(FILE_NAME_KEYWORD_ERROR, S.get("UsedLibraryToolnameError"));
    }
    return isOk;
  }

  private static Project completeProject(
      SplashScreen monitor, Loader loader, LogisimFile file, boolean isStartup) {
    if (monitor != null) monitor.setProgress(SplashScreen.PROJECT_CREATE);
    final com.cburch.logisim.proj.Project ret = new Project(file);
    if (monitor != null) monitor.setProgress(SplashScreen.FRAME_CREATE);
    SwingUtilities.invokeLater(new CreateFrame(loader, ret, isStartup));
    updatecircs(file, ret);
    return ret;
  }

  private static LogisimFile createEmptyFile(Loader loader, Project proj) {
    InputStream templReader = AppPreferences.getEmptyTemplate().createStream();
    LogisimFile file;
    try {
      file = loader.openLogisimFile(templReader);
    } catch (Exception t) {
      file = LogisimFile.createNew(loader, proj);
      file.addCircuit(new Circuit("main", file, proj));
    } finally {
      try {
        templReader.close();
      } catch (IOException ignored) {
      }
    }
    return file;
  }

  private static Frame createFrame(Project sourceProject, Project newProject) {
    if (sourceProject != null) {
      final com.cburch.logisim.gui.main.Frame frame = sourceProject.getFrame();
      if (frame != null) {
        frame.savePreferences();
      }
    }
    final com.cburch.logisim.gui.main.Frame newFrame = new Frame(newProject);
    newProject.setFrame(newFrame);
    return newFrame;
  }

  public static LogisimFile createNewFile(Project baseProject) {
    final com.cburch.logisim.gui.main.Frame parent = (baseProject == null) ? null : baseProject.getFrame();
    final com.cburch.logisim.file.Loader loader = new Loader(parent);
    final java.io.InputStream templReader = AppPreferences.getTemplate().createStream();
    LogisimFile file;
    try {
      file = loader.openLogisimFile(templReader);
    } catch (IOException ex) {
      displayException(baseProject.getFrame(), ex);
      file = createEmptyFile(loader, baseProject);
    } finally {
      try {
        templReader.close();
      } catch (IOException ignored) {
        // Do nothing.
      }
    }
    return file;
  }

  private static void displayException(Component parent, Exception ex) {
    String msg = S.get("templateOpenError", ex.toString());
    String ttl = S.get("templateOpenErrorTitle");
    OptionPane.showMessageDialog(parent, msg, ttl, OptionPane.ERROR_MESSAGE);
  }

  public static Project doNew(Project baseProject) {
    final com.cburch.logisim.file.LogisimFile file = createNewFile(baseProject);
    final com.cburch.logisim.proj.Project newProj = new Project(file);
    final com.cburch.logisim.gui.main.Frame frame = createFrame(baseProject, newProj);
    frame.setVisible(true);
    frame.getCanvas().requestFocus();
    newProj.getLogisimFile().getLoader().setParent(frame);
    updatecircs(file, newProj);
    return newProj;
  }

  public static Project doNew(SplashScreen monitor) {
    return doNew(monitor, false);
  }

  public static Project doNew(SplashScreen monitor, boolean isStartupScreen) {
    if (monitor != null) monitor.setProgress(SplashScreen.FILE_CREATE);
    final com.cburch.logisim.file.Loader loader = new Loader(monitor);
    final java.io.InputStream templReader = AppPreferences.getTemplate().createStream();
    LogisimFile file = null;
    try {
      file = loader.openLogisimFile(templReader);
    } catch (IOException ex) {
      displayException(monitor, ex);
    } finally {
      try {
        templReader.close();
      } catch (IOException ignored) {
      }
    }
    if (file == null) file = createEmptyFile(loader, null);
    return completeProject(monitor, loader, file, isStartupScreen);
  }

  public static void doMerge(Component parent, Project baseProject) {
    JFileChooser chooser;
    if (baseProject != null) {
      final com.cburch.logisim.file.Loader oldLoader = baseProject.getLogisimFile().getLoader();
      chooser = oldLoader.createChooser();
      if (oldLoader.getMainFile() != null) {
        chooser.setSelectedFile(oldLoader.getMainFile());
      }
    } else {
      chooser = JFileChoosers.create();
    }
    chooser.setFileFilter(Loader.LOGISIM_FILTER);
    chooser.setDialogTitle(S.get("FileMergeItem"));

    LogisimFile mergelib;
    int returnVal = chooser.showOpenDialog(parent);
    if (returnVal != JFileChooser.APPROVE_OPTION) return;
    final java.io.File selected = chooser.getSelectedFile();
    final com.cburch.logisim.file.Loader loader = new Loader(baseProject == null ? parent : baseProject.getFrame());
    try {
      mergelib = loader.openLogisimFile(selected);
      if (mergelib == null) return;
    } catch (LoadFailedException ex) {
      if (!ex.isShown()) {
        OptionPane.showMessageDialog(
            parent,
            S.get("fileMergeError", ex.toString()),
            S.get("FileMergeErrorItem"),
            OptionPane.ERROR_MESSAGE);
      }
      return;
    }
    baseProject.doAction(LogisimFileActions.mergeFile(mergelib, baseProject.getLogisimFile()));
  }

  private static void updatecircs(LogisimFile lib, Project proj) {
    for (final com.cburch.logisim.circuit.Circuit circ : lib.getCircuits()) {
      circ.setProject(proj);
    }
    for (final com.cburch.logisim.tools.Library libs : lib.getLibraries()) {
      if (libs instanceof LoadedLibrary test) {
        if (test.getBase() instanceof LogisimFile lsFile) {
          updatecircs(lsFile, proj);
        }
      }
    }
  }

  public static Project doOpen(Component parent, Project baseProject) {
    JFileChooser chooser;
    if (baseProject != null) {
      final com.cburch.logisim.file.Loader oldLoader = baseProject.getLogisimFile().getLoader();
      chooser = oldLoader.createChooser();
      if (oldLoader.getMainFile() != null) {
        chooser.setSelectedFile(oldLoader.getMainFile());
      }
    } else {
      chooser = JFileChoosers.create();
    }
    chooser.setFileFilter(Loader.LOGISIM_FILTER);
    chooser.setDialogTitle(S.get("FileOpenItem"));

    final int returnVal = chooser.showOpenDialog(parent);
    if (returnVal != JFileChooser.APPROVE_OPTION) return null;
    final java.io.File selected = chooser.getSelectedFile();
    if (selected == null) return null;
    return doOpen(parent, baseProject, selected);
  }

  public static Project doOpen(Component parent, Project baseProject, File f) {
    com.cburch.logisim.proj.Project proj = Projects.findProjectFor(f);
    Loader loader = null;
    if (proj != null) {
      proj.getFrame().toFront();
      loader = proj.getLogisimFile().getLoader();
      if (proj.isFileDirty()) {
        String message = S.get("openAlreadyMessage", proj.getLogisimFile().getName());
        String[] options = {
          S.get("openAlreadyLoseChangesOption"),
          S.get("openAlreadyNewWindowOption"),
          S.get("openAlreadyCancelOption"),
        };
        int result =
            OptionPane.showOptionDialog(
                proj.getFrame(),
                message,
                S.get("openAlreadyTitle"),
                0,
                OptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[2]);
        if (result == 0) { // keep proj as is, so that load happens into the window
        } else if (result == 1) {
          proj = null; // we'll create a new project
        } else {
          return proj;
        }
      }
    }

    if (proj == null && baseProject != null && baseProject.isStartupScreen()) {
      proj = baseProject;
      proj.setStartupScreen(false);
      loader = baseProject.getLogisimFile().getLoader();
    } else {
      loader = new Loader(baseProject == null ? parent : baseProject.getFrame());
    }

    try {
      final com.cburch.logisim.file.LogisimFile lib = loader.openLogisimFile(f);
      AppPreferences.updateRecentFile(f);
      if (lib == null) return null;
      LibraryTools.removePresentLibraries(lib, new HashMap<>(), true);
      if (proj == null) {
        proj = new Project(lib);
        updatecircs(lib, proj);
      } else {
        updatecircs(lib, proj);
        proj.setLogisimFile(lib);
      }
    } catch (LoadFailedException ex) {
      if (!ex.isShown()) {
        OptionPane.showMessageDialog(
            parent,
            S.get("fileOpenError", ex.toString()),
            S.get("fileOpenErrorTitle"),
            OptionPane.ERROR_MESSAGE);
      }
      return null;
    }

    com.cburch.logisim.gui.main.Frame frame = proj.getFrame();
    if (frame == null) {
      frame = createFrame(baseProject, proj);
    }
    frame.setVisible(true);
    frame.toFront();
    frame.getCanvas().requestFocus();
    proj.getLogisimFile().getLoader().setParent(frame);
    return proj;
  }

  public static Project doOpen(SplashScreen monitor, File source, Map<File, File> substitutions)
      throws LoadFailedException {
    if (monitor != null) monitor.setProgress(SplashScreen.FILE_LOAD);
    final com.cburch.logisim.file.Loader loader = new Loader(monitor);
    final com.cburch.logisim.file.LogisimFile file = loader.openLogisimFile(source, substitutions);
    AppPreferences.updateRecentFile(source);

    return completeProject(monitor, loader, file, false);
  }

  public static Project doOpenNoWindow(SplashScreen monitor, File source)
      throws LoadFailedException {
    final com.cburch.logisim.file.Loader loader = new Loader(monitor);
    final com.cburch.logisim.file.LogisimFile file = loader.openLogisimFile(source);
    final com.cburch.logisim.proj.Project ret = new Project(file);
    updatecircs(file, ret);
    return ret;
  }

  public static void doQuit() {
    final com.cburch.logisim.gui.main.Frame top = Projects.getTopFrame();
    top.savePreferences();

    for (Project proj : new ArrayList<>(Projects.getOpenProjects())) {
      if (!proj.confirmClose(S.get("confirmQuitTitle"))) return;
    }
    System.exit(0);
  }

  public static boolean doSave(Project proj) {
    final com.cburch.logisim.file.Loader loader = proj.getLogisimFile().getLoader();
    final java.io.File f = loader.getMainFile();
    if (f == null) return doSaveAs(proj);
    else return doSave(proj, f);
  }

  public static boolean doSave(Project proj, File f) {
    final com.cburch.logisim.file.Loader loader = proj.getLogisimFile().getLoader();
    final com.cburch.logisim.tools.Tool oldTool = proj.getTool();
    proj.setTool(null);
    final boolean ret = loader.save(proj.getLogisimFile(), f);
    if (ret) {
      AppPreferences.updateRecentFile(f);
      proj.setFileAsClean();
    }
    proj.setTool(oldTool);
    return ret;
  }

  /**
   * Exports a Logisim project in a seperate directory
   *
   * <p>It is the action listener for the File->Export project... menu option.
   *
   * @param proj Project to be exported
   * @return true if success, false otherwise
   */
  public static boolean doExportProject(Project proj) {
    boolean ret = proj.isFileDirty() ? doSave(proj) : true;
    if (ret) {
      final com.cburch.logisim.file.Loader loader = proj.getLogisimFile().getLoader();
      final com.cburch.logisim.tools.Tool oldTool = proj.getTool();
      proj.setTool(null);
      final javax.swing.JFileChooser chooser = loader.createChooser();
      chooser.setFileFilter(Loader.LOGISIM_DIRECTORY);
      chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      chooser.setAcceptAllFileFilterUsed(false);
      boolean isCorrectDirectory = false;
      java.lang.@RUntainted String exportRootDir = "";
      do {
        ret &= chooser.showSaveDialog(proj.getFrame()) == JFileChooser.APPROVE_OPTION;
        if (!ret) {
          proj.setTool(oldTool);
          return false;
        }
        final java.io.File exportHome = chooser.getSelectedFile();
        final java.lang.String exportRoot = loader.getMainFile().getName().replace(".circ", "");
        exportRootDir = String.format("%s%s%s", exportHome, File.separator, exportRoot);
        final java.lang.@RUntainted String exportLibDir = String.format("%s%s%s", exportRootDir, File.separator, Loader.LOGISIM_LIBRARY_DIR);
        final java.lang.@RUntainted String exportCircDir = String.format("%s%s%s", exportRootDir, File.separator, Loader.LOGISIM_CIRCUIT_DIR);
        try {
          final java.nio.file.Path path = Paths.get(exportRootDir);
          if (Files.exists(path)) {
            OptionPane.showMessageDialog(proj.getFrame(), S.get("ProjExistsUnableToCreate", exportRoot));
          } else {
            isCorrectDirectory = true;
          }
          if (isCorrectDirectory) {
            Files.createDirectories(Paths.get(exportLibDir));
            Files.createDirectories(Paths.get(exportCircDir));
          }
        } catch (IOException e) {
          OptionPane.showMessageDialog(proj.getFrame(), S.get("ProjUnableToCreate", e.getMessage()));
          proj.setTool(oldTool);
          return false;
        }
      } while (!isCorrectDirectory);
      ret &= loader.export(proj.getLogisimFile(), exportRootDir);
      proj.setTool(oldTool);
    }
    return ret;
  }

  /**
   * Saves a Logisim project in a .circ file.
   *
   * <p>It is the action listener for the File->Save as... menu option.
   *
   * @param proj project to be saved
   * @return true if success, false otherwise
   */
  public static boolean doSaveAs(Project proj) {
    com.cburch.logisim.file.Loader loader = proj.getLogisimFile().getLoader();
    javax.swing.JFileChooser chooser = loader.createChooser();
    chooser.setFileFilter(Loader.LOGISIM_FILTER);
    if (loader.getMainFile() != null) {
      chooser.setSelectedFile(loader.getMainFile());
    }

    int returnVal;
    boolean validFilename = false;
    java.util.HashMap<java.lang.String,java.lang.String> errors = new HashMap<String, String>();
    do {
      errors.clear();
      returnVal = chooser.showSaveDialog(proj.getFrame());
      if (returnVal != JFileChooser.APPROVE_OPTION) {
        return false;
      }
      validFilename = checkValidFilename(chooser.getSelectedFile().getName(), proj, errors);
      if (!validFilename) {
        java.lang.String message = "\"" + chooser.getSelectedFile() + "\":\n";
        for (String key : errors.keySet()) {
          message = message.concat("=> " + S.get(errors.get(key)) + "\n");
        }
        OptionPane.showMessageDialog(
            chooser, message, S.get("FileSaveAsItem"), OptionPane.ERROR_MESSAGE);
      }
    } while (!validFilename);

    java.io.File selectedFile = chooser.getSelectedFile();
    if (!selectedFile.getName().endsWith(Loader.LOGISIM_EXTENSION)) {
      java.lang.String old = selectedFile.getName();
      int ext0 = old.lastIndexOf('.');
      if (ext0 < 0 || !Pattern.matches("\\.\\p{L}{2,}\\d?", old.substring(ext0))) {
        selectedFile = new File(selectedFile.getParentFile(), old + Loader.LOGISIM_EXTENSION);
      } else {
        java.lang.String ext = old.substring(ext0);
        java.lang.String ttl = S.get("replaceExtensionTitle");
        java.lang.String msg = S.get("replaceExtensionMessage", ext);
        Object[] options = {
          S.get("replaceExtensionReplaceOpt", ext),
          S.get("replaceExtensionAddOpt", Loader.LOGISIM_EXTENSION),
          S.get("replaceExtensionKeepOpt")
        };
        javax.swing.JOptionPane dlog = new JOptionPane(msg);
        dlog.setMessageType(OptionPane.QUESTION_MESSAGE);
        dlog.setOptions(options);
        dlog.createDialog(proj.getFrame(), ttl).setVisible(true);

        Object result = dlog.getValue();
        if (result == options[0]) {
          java.lang.String name = old.substring(0, ext0) + Loader.LOGISIM_EXTENSION;
          selectedFile = new File(selectedFile.getParentFile(), name);
        } else if (result == options[1]) {
          selectedFile = new File(selectedFile.getParentFile(), old + Loader.LOGISIM_EXTENSION);
        }
      }
    }

    if (selectedFile.exists()) {
      int confirm =
          OptionPane.showConfirmDialog(
              proj.getFrame(),
              S.get("confirmOverwriteMessage"),
              S.get("confirmOverwriteTitle"),
              OptionPane.YES_NO_OPTION);
      if (confirm != OptionPane.YES_OPTION) return false;
    }
    return doSave(proj, selectedFile);
  }
}
