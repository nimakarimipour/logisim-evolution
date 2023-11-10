/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class ArgonXml {

  private final String name;
  private String content;

  private final Map<String, String> nameAttributes = new HashMap<>();

  private final Map<String, ArrayList<ArgonXml>> nameChildren = new HashMap<>();

  private ArgonXml(Element element) {
    this.name = element.getNodeName();
    this.content = element.getTextContent();
    final org.w3c.dom.NamedNodeMap namedNodeMap = element.getAttributes();
    int n = namedNodeMap.getLength();
    for (int i = 0; i < n; i++) {
      final org.w3c.dom.Node node = namedNodeMap.item(i);
      final java.lang.String name = node.getNodeName();
      addAttribute(name, node.getNodeValue());
    }
    final org.w3c.dom.NodeList nodes = element.getChildNodes();
    n = nodes.getLength();
    for (int i = 0; i < n; i++) {
      final org.w3c.dom.Node node = nodes.item(i);
      int type = node.getNodeType();
      if (type == Node.ELEMENT_NODE) {
        final com.cburch.logisim.util.ArgonXml child = new ArgonXml((Element) node);
        addChild(node.getNodeName(), child);
      }
    }
  }

  public ArgonXml(InputStream inputStream, String rootName) {
    this(rootElement(inputStream, rootName));
  }

  public ArgonXml(String rootName) {
    this.name = rootName;
  }

  public ArgonXml(String filename, String rootName) {
    this(fileInputStream(filename), rootName);
  }

  private static FileInputStream fileInputStream(String filename) {
    try {
      return new FileInputStream(filename);
    } catch (IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  private static Element rootElement(InputStream inputStream, String rootName) {
    try {
      final javax.xml.parsers.DocumentBuilderFactory builderFactory = XmlUtil.getHardenedBuilderFactory();
      final javax.xml.parsers.DocumentBuilder builder = builderFactory.newDocumentBuilder();
      final org.w3c.dom.Document document = builder.parse(inputStream);
      final org.w3c.dom.Element rootElement = document.getDocumentElement();
      if (!rootElement.getNodeName().equals(rootName)) {
        throw new RuntimeException("Could not find root node: " + rootName);
      }
      return rootElement;
    } catch (IOException | SAXException | ParserConfigurationException exception) {
      throw new RuntimeException(exception);
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (Exception exception) {
          throw new RuntimeException(exception);
        }
      }
    }
  }

  public void addAttribute(String name, String value) {
    nameAttributes.put(name, value);
  }

  public void addChild(ArgonXml xml) {
    addChild(xml.name(), xml);
  }

  private void addChild(String name, ArgonXml child) {
    final java.util.ArrayList<com.cburch.logisim.util.ArgonXml> children = nameChildren.computeIfAbsent(name, k -> new ArrayList<>());
    children.add(child);
  }

  public void addChildren(ArgonXml... xmls) {
    for (final com.cburch.logisim.util.ArgonXml xml : xmls) addChild(xml.name(), xml);
  }

  public ArgonXml child(String name) {
    final com.cburch.logisim.util.ArgonXml child = optChild(name);
    if (child == null) throw new RuntimeException("Could not find child node: " + name);
    return child;
  }

  public List<ArgonXml> children(String name) {
    final java.util.ArrayList<com.cburch.logisim.util.ArgonXml> children = nameChildren.get(name);
    return children == null ? new ArrayList<>() : children;
  }

  public String content() {
    return content;
  }

  public double doubleValue(String name) {
    return Double.parseDouble(optString(name));
  }

  public int integer(String name) {
    return Integer.parseInt(string(name));
  }

  public String name() {
    return name;
  }

  public ArgonXml optChild(String name) {
    final java.util.List<com.cburch.logisim.util.ArgonXml> children = children(name);
    final int n = children.size();
    if (n > 1) throw new RuntimeException("Could not find individual child node: " + name);
    return n == 0 ? null : children.get(0);
  }

  public Double optDouble(String name) {
    final java.lang.String string = optString(name);
    return string == null ? null : doubleValue(name);
  }

  public Integer optInteger(String name) {
    final java.lang.String string = optString(name);
    return string == null ? null : integer(name);
  }

  public boolean option(String name) {
    return optChild(name) != null;
  }

  public String optString(String name) {
    return nameAttributes.get(name);
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String string(String name) {
    final java.lang.String value = optString(name);
    if (value == null) {
      throw new RuntimeException("Could not find attribute: " + name + ", in node: " + this.name);
    }
    return value;
  }
}
