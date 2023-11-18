/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import static com.cburch.logisim.file.Strings.S;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.std.base.BaseLibrary;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.EventSourceWeakSupport;
import com.cburch.logisim.util.UniquelyNamedThread;
import com.cburch.logisim.vhdl.base.VhdlContent;
import com.cburch.logisim.vhdl.base.VhdlEntity;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import org.xml.sax.SAXException;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RUntainted;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RPolyTainted;

public class LogisimFile extends Library implements LibraryEventSource, CircuitListener {

  private static class WritingThread extends UniquelyNamedThread {
    final OutputStream out;
    final LogisimFile file;

    WritingThread(OutputStream out, LogisimFile file) {
      super("WritingThread");
      this.out = out;
      this.file = file;
    }

    @Override
    public void run() {
      file.write(out, file.loader);
      try {
        out.close();
      } catch (IOException e) {
        file.loader.showError(S.get("fileDuplicateError", e.toString()));
      }
    }
  }

  private final EventSourceWeakSupport<LibraryListener> listeners = new EventSourceWeakSupport<>();
  private final LinkedList<String> messages = new LinkedList<>();
  private final Options options = new Options();
  private final List<AddTool> tools = new LinkedList<>();
  private final List<Library> libraries = new LinkedList<>();
  private Loader loader;
  private Circuit main = null;
  private String name;
  private boolean isDirty = false;

  LogisimFile(Loader loader) {
    this.loader = loader;

    // Creates the default project name, adding an underscore if needed
    name = S.get("defaultProjectName");
    if (Projects.windowNamed(name)) {
      for (int i = 2; true; i++) {
        if (!Projects.windowNamed(name + "_" + i)) {
          name += "_" + i;
          break;
        }
      }
    }
  }

  @Override
  public void circuitChanged(CircuitEvent event) {
    final int act = event.getAction();
    if (act == CircuitEvent.ACTION_CHECK_NAME) {
      final java.lang.String oldname = (String) event.getData();
      final java.lang.String newname = event.getCircuit().getName();
      if (isNameInUse(newname, event.getCircuit())) {
        OptionPane.showMessageDialog(
                null,
                "\"" + newname + "\": " + S.get("circuitNameExists"),
                "",
                OptionPane.ERROR_MESSAGE);
        event.getCircuit().getStaticAttributes().setValue(CircuitAttributes.NAME_ATTR, oldname);
      }
    }
  }

  // Name check Methods
  private boolean isNameInUse(String name, Circuit changed) {
    if (name.isEmpty()) return false;
    for (final com.cburch.logisim.tools.Library mylib : getLibraries()) {
      if (isNameInLibraries(mylib, name)) return true;
    }
    for (final com.cburch.logisim.circuit.Circuit mytool : this.getCircuits()) {
      if (name.equalsIgnoreCase(mytool.getName()) && !mytool.equals(changed))
        return true;
    }
    return false;
  }

  private boolean isNameInLibraries(Library lib, String name) {
    if (name.isEmpty()) return false;
    for (final com.cburch.logisim.tools.Library mylib : lib.getLibraries()) {
      if (isNameInLibraries(mylib, name)) return true;
    }
    for (final com.cburch.logisim.tools.Tool mytool : lib.getTools()) {
      if (name.equalsIgnoreCase(mytool.getName())) return true;
    }
    return false;
  }

  //
  // creation methods
  //
  public static LogisimFile createNew(Loader loader, Project proj) {
    final com.cburch.logisim.file.LogisimFile ret = new LogisimFile(loader);
    ret.main = new Circuit("main", ret, proj);
    // The name will be changed in LogisimPreferences
    ret.tools.add(new AddTool(ret.main.getSubcircuitFactory()));
    return ret;
  }

  private static String getFirstLine(BufferedInputStream in) throws IOException {
    final byte[] first = new byte[512];
    in.mark(first.length - 1);
    in.read(first);
    in.reset();

    int lineBreak = first.length;
    for (int i = 0; i < lineBreak; i++) {
      if (first[i] == '\n') {
        lineBreak = i;
      }
    }
    return new String(first, 0, lineBreak, StandardCharsets.UTF_8);
  }

  public static LogisimFile load(File file, Loader loader) throws IOException {
    final java.io.FileInputStream inputStream = new FileInputStream(file);
    Throwable firstExcept = null;
    try {
      return loadSub(inputStream, loader, file);
    } catch (Throwable t) {
      firstExcept = t;
    } finally {
      inputStream.close();
    }

    // We'll now try to do it using a reader. This is to work around
    // Logisim versions prior to 2.5.1, when files were not saved using
    // UTF-8 as the encoding (though the XML file reported otherwise).
    try {
      final com.cburch.logisim.file.ReaderInputStream readerInputStream = new ReaderInputStream(new FileReader(file), "UTF8");
      return loadSub(readerInputStream, loader, file);
    } catch (Exception t) {
      firstExcept.printStackTrace();
      loader.showError(S.get("xmlFormatError", firstExcept.toString()));
    } finally {
      try {
        inputStream.close();
      } catch (Exception ignored) {
        // Do nothing.
      }
    }

    return null;
  }

  public static LogisimFile load(InputStream in, Loader loader) throws IOException {
    try {
      return loadSub(in, loader);
    } catch (SAXException e) {
      e.printStackTrace();
      loader.showError(S.get("xmlFormatError", e.toString()));
      return null;
    }
  }

  public static LogisimFile loadSub(InputStream in, Loader loader) throws IOException, SAXException {
    return (loadSub(in, loader, null));
  }

  public static LogisimFile loadSub(InputStream in, Loader loader, File file) throws IOException, SAXException {
    // fetch first line and then reset
    final java.io.BufferedInputStream inBuffered = new BufferedInputStream(in);
    final java.lang.String firstLine = getFirstLine(inBuffered);

    if (firstLine == null) {
      throw new IOException("File is empty");
    } else if (firstLine.equals("Logisim v1.0")) {
      // if this is a 1.0 file, then set up a pipe to translate to
      // 2.0 and then interpret as a 2.0 file
      throw new IOException("Version 1.0 files no longer supported");
    }

    final com.cburch.logisim.file.XmlReader xmlReader = new XmlReader(loader, file);
    /* Can set the project pointer to zero as it is fixed later */
    final com.cburch.logisim.file.LogisimFile ret = xmlReader.readLibrary(inBuffered, null);
    ret.loader = loader;
    return ret;
  }

  public void addCircuit(Circuit circuit) {
    addCircuit(circuit, tools.size());
  }

  public void addCircuit(Circuit circuit, int index) {
    circuit.addCircuitListener(this);
    final com.cburch.logisim.tools.AddTool tool = new AddTool(circuit.getSubcircuitFactory());
    tools.add(index, tool);
    if (tools.size() == 1) setMainCircuit(circuit);
    fireEvent(LibraryEvent.ADD_TOOL, tool);
  }

  public void addVhdlContent(VhdlContent content) {
    addVhdlContent(content, tools.size());
  }

  public void addVhdlContent(VhdlContent content, int index) {
    final com.cburch.logisim.tools.AddTool tool = new AddTool(new VhdlEntity(content));
    tools.add(index, tool);
    fireEvent(LibraryEvent.ADD_TOOL, tool);
  }

  public void addLibrary(Library lib) {
    if (!lib.getName().equals(BaseLibrary._ID)) {
      for (final com.cburch.logisim.tools.Tool tool : lib.getTools()) {
        if (tool instanceof AddTool addTool) {
          final com.cburch.logisim.data.AttributeSet atrs = addTool.getAttributeSet();
          for (final com.cburch.logisim.data.Attribute<?> attr : atrs.getAttributes()) {
            if (attr == CircuitAttributes.NAME_ATTR) atrs.setReadOnly(attr, true);
          }
        }
      }
    }
    libraries.add(lib);
    fireEvent(LibraryEvent.ADD_LIBRARY, lib);
  }

  //
  // listener methods
  //
  @Override
  public void addLibraryListener(LibraryListener what) {
    listeners.add(what);
  }

  //
  // modification actions
  //
  public void addMessage(String msg) {
    messages.addLast(msg);
  }

  @SuppressWarnings("resource")
  public LogisimFile cloneLogisimFile(Loader newloader) {
    final java.io.PipedInputStream reader = new PipedInputStream();
    final java.io.PipedOutputStream writer = new PipedOutputStream();
    try {
      reader.connect(writer);
    } catch (IOException e) {
      newloader.showError(S.get("fileDuplicateError", e.toString()));
      return null;
    }
    new WritingThread(writer, this).start();
    try {
      return LogisimFile.load(reader, newloader);
    } catch (IOException e) {
      newloader.showError(S.get("fileDuplicateError", e.toString()));
      try {
        reader.close();
      } catch (IOException ignored) {
      }
      return null;
    }
  }

  public boolean contains(Circuit circ) {
    for (final com.cburch.logisim.tools.AddTool tool : tools) {
      if (tool.getFactory() instanceof SubcircuitFactory factory) {
        if (factory.getSubcircuit() == circ) return true;
      }
    }
    return false;
  }

  public boolean contains(VhdlContent content) {
    for (final com.cburch.logisim.tools.AddTool tool : tools) {
      if (tool.getFactory() instanceof VhdlEntity factory) {
        if (factory.getContent() == content) return true;
      }
    }
    return false;
  }

  public boolean containsFactory(String name) {
    for (final com.cburch.logisim.tools.AddTool tool : tools) {
      if (tool.getFactory() instanceof VhdlEntity factory) {
        if (factory.getContent().getName().equals(name)) return true;
      } else if (tool.getFactory() instanceof SubcircuitFactory factory) {
        if (factory.getSubcircuit().getName().equals(name)) return true;
      }
    }
    return false;
  }

  private Tool findTool(Library lib, Tool query) {
    for (final com.cburch.logisim.tools.Tool tool : lib.getTools()) {
      if (tool.equals(query)) return tool;
    }
    return null;
  }

  Tool findTool(Tool query) {
    for (final com.cburch.logisim.tools.Library lib : getLibraries()) {
      final com.cburch.logisim.tools.Tool ret = findTool(lib, query);
      if (ret != null) return ret;
    }
    return null;
  }

  private void fireEvent(int action, Object data) {
    final com.cburch.logisim.file.LibraryEvent e = new LibraryEvent(this, action, data);
    for (final com.cburch.logisim.file.LibraryListener l : listeners) {
      l.libraryChanged(e);
    }
  }

  public AddTool getAddTool(Circuit circ) {
    for (final com.cburch.logisim.tools.AddTool tool : tools) {
      if (tool.getFactory() instanceof SubcircuitFactory factory) {
        if (factory.getSubcircuit() == circ) {
          return tool;
        }
      }
    }
    return null;
  }

  public AddTool getAddTool(VhdlContent content) {
    for (final com.cburch.logisim.tools.AddTool tool : tools) {
      if (tool.getFactory() instanceof VhdlEntity factory) {
        if (factory.getContent() == content) {
          return tool;
        }
      }
    }
    return null;
  }

  public Circuit getCircuit(String name) {
    if (name == null) return null;
    for (final com.cburch.logisim.tools.AddTool tool : tools) {
      if (tool.getFactory() instanceof SubcircuitFactory factory) {
        if (name.equals(factory.getName())) return factory.getSubcircuit();
      }
    }
    return null;
  }

  public VhdlContent getVhdlContent(String name) {
    if (name == null) return null;
    for (final com.cburch.logisim.tools.AddTool tool : tools) {
      if (tool.getFactory() instanceof VhdlEntity factory) {
        if (name.equals(factory.getName())) return factory.getContent();
      }
    }
    return null;
  }

  public int getCircuitCount() {
    return getCircuits().size();
  }

  public List<Circuit> getCircuits() {
    final java.util.ArrayList<com.cburch.logisim.circuit.Circuit> ret = new ArrayList<Circuit>(tools.size());
    for (final com.cburch.logisim.tools.AddTool tool : tools) {
      if (tool.getFactory() instanceof SubcircuitFactory factory) {
        ret.add(factory.getSubcircuit());
      }
    }
    return ret;
  }

  public int indexOfCircuit(Circuit circ) {
    for (int i = 0; i < tools.size(); i++) {
      final com.cburch.logisim.tools.AddTool tool = tools.get(i);
      if (tool.getFactory() instanceof SubcircuitFactory factory) {
        if (factory.getSubcircuit() == circ) {
          return i;
        }
      }
    }
    return -1;
  }

  public List<VhdlContent> getVhdlContents() {
    final java.util.ArrayList<com.cburch.logisim.vhdl.base.VhdlContent> ret = new ArrayList<VhdlContent>(tools.size());
    for (final com.cburch.logisim.tools.AddTool tool : tools) {
      if (tool.getFactory() instanceof VhdlEntity factory) {
        ret.add(factory.getContent());
      }
    }
    return ret;
  }

  public int indexOfVhdl(VhdlContent vhdl) {
    for (int i = 0; i < tools.size(); i++) {
      final com.cburch.logisim.tools.AddTool tool = tools.get(i);
      if (tool.getFactory() instanceof VhdlEntity factory) {
        if (factory.getContent() == vhdl) {
          return i;
        }
      }
    }
    return -1;
  }

  @Override
  public List<Library> getLibraries() {
    return libraries;
  }

  public Loader getLoader() {
    return loader;
  }

  public Circuit getMainCircuit() {
    return main;
  }

  public String getMessage() {
    return (messages.isEmpty()) ? null : messages.removeFirst();
  }

  //
  // access methods
  //
  @Override
  public String getName() {
    return name;
  }

  public Options getOptions() {
    return options;
  }

  @Override
  public List<AddTool> getTools() {
    return tools;
  }

  public String getUnloadLibraryMessage(Library lib) {
    final java.util.HashSet<com.cburch.logisim.comp.ComponentFactory> factories = new HashSet<ComponentFactory>();
    for (final com.cburch.logisim.tools.Tool tool : lib.getTools()) {
      if (tool instanceof AddTool addTool) {
        factories.add(addTool.getFactory());
      }
    }

    for (final com.cburch.logisim.circuit.Circuit circuit : getCircuits()) {
      for (final com.cburch.logisim.comp.Component comp : circuit.getNonWires()) {
        if (factories.contains(comp.getFactory())) {
          return S.get("unloadUsedError", circuit.getName());
        }
      }
    }

    final com.cburch.logisim.file.ToolbarData tb = options.getToolbarData();
    final com.cburch.logisim.file.MouseMappings mm = options.getMouseMappings();
    for (final com.cburch.logisim.tools.Tool t : lib.getTools()) {
      if (tb.usesToolFromSource(t)) {
        return S.get("unloadToolbarError");
      }
      if (mm.usesToolFromSource(t)) {
        return S.get("unloadMappingError");
      }
    }

    return null;
  }

  @Override
  public boolean isDirty() {
    return isDirty;
  }

  public void moveCircuit(AddTool tool, int index) {
    int oldIndex = tools.indexOf(tool);
    if (oldIndex < 0) {
      tools.add(index, tool);
      fireEvent(LibraryEvent.ADD_TOOL, tool);
    } else {
      AddTool value = tools.remove(oldIndex);
      tools.add(index, value);
      fireEvent(LibraryEvent.MOVE_TOOL, tool);
    }
  }

  public void removeCircuit(Circuit circuit) {
    if (getCircuitCount() <= 1) {
      throw new RuntimeException("Cannot remove last circuit");
    }

    int index = indexOfCircuit(circuit);
    if (index >= 0) {
      final Tool circuitTool = tools.remove(index);

      if (main == circuit) {
        setMainCircuit(((SubcircuitFactory) tools.get(0).getFactory()).getSubcircuit());
      }
      fireEvent(LibraryEvent.REMOVE_TOOL, circuitTool);
    }
  }

  public void removeVhdl(VhdlContent vhdl) {
    final int index = indexOfVhdl(vhdl);
    if (index >= 0) {
      final Tool vhdlTool = tools.remove(index);
      fireEvent(LibraryEvent.REMOVE_TOOL, vhdlTool);
    }
  }

  @Override
  public boolean removeLibrary(String name) {
    int index = -1;
    for (final com.cburch.logisim.tools.Library lib : libraries)
      if (lib.getName().equals(name))
        index = libraries.indexOf(lib);
    if (index < 0) return false;
    libraries.remove(index);
    return true;
  }

  public void removeLibrary(Library lib) {
    libraries.remove(lib);
    fireEvent(LibraryEvent.REMOVE_LIBRARY, lib);
  }

  @Override
  public void removeLibraryListener(LibraryListener what) {
    listeners.remove(what);
  }

  public void setDirty(boolean value) {
    if (isDirty != value) {
      isDirty = value;
      fireEvent(LibraryEvent.DIRTY_STATE, value ? Boolean.TRUE : Boolean.FALSE);
    }
  }

  public void setMainCircuit(Circuit circuit) {
    if (circuit == null) return;
    this.main = circuit;
    fireEvent(LibraryEvent.SET_MAIN, circuit);
  }

  public void setName(String name) {
    this.name = name;
    fireEvent(LibraryEvent.SET_NAME, name);
  }

  //
  // other methods
  //
  void write(OutputStream out, LibraryLoader loader) {
    write(out, loader, null, null);
  }

  void write(OutputStream out, LibraryLoader loader, String libraryHome) {
    write(out, loader, null, libraryHome);
  }

  void write(OutputStream out, LibraryLoader loader, File dest, String libraryHome) {
    try {
      XmlWriter.write(this, out, loader, dest, libraryHome);
    } catch (TransformerConfigurationException e) {
      loader.showError("internal error configuring transformer");
    } catch (ParserConfigurationException e) {
      loader.showError("internal error configuring parser");
    } catch (TransformerException e) {
      final java.lang.String msg = e.getMessage();
      java.lang.String err = S.get("xmlConversionError");
      if (msg == null) err += ": " + msg;
      loader.showError(err);
    }
  }
  
}
