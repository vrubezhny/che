/*
 * Copyright (c) 2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.plugin.cpp.ide.console;

import static com.google.gwt.regexp.shared.RegExp.compile;

import com.google.gwt.regexp.shared.RegExp;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.console.AbstractOutputRenderer;
import org.eclipse.che.ide.resource.Path;

/**
 * Output renderer adds an anchor link to the lines that match C/CPP compilation error or warning
 * message and installs the handler functions for the links. The handler parses the C/CPP
 * compilation error or warning message line, searches for a candidate C/CPP file to navigate to,
 * opens the found file in editor and reveals it to the required line and column (if available)
 * according to the line information
 *
 * @author Victor Rubezhny
 */
public class CPPOutputRenderer extends AbstractOutputRenderer {

  private static final RegExp COMPILATION_MESSAGE =
      compile(
          "(.+\\.(c|C|cc|CC|cpp|CPP|h|H|hpp|HPP):\\d+:\\d+: (error|fatal error|note|warning): .+)");
  private static final RegExp LINKER_MESSAGE =
      compile("(.+\\.(c|C|cc|CC|cpp|CPP|h|H|hpp|HPP):\\d+: .+)");

  /**
   * Constructs C/CPP Output Renderer Object
   *
   * @param appContext
   * @param editorAgent
   */
  public CPPOutputRenderer(String name, AppContext appContext, EditorAgent editorAgent) {
    super(name, appContext, editorAgent);

    exportCompilationMessageAnchorClickHandlerFunction();
    exportLinkerMessageAnchorClickHandlerFunction();
  }

  @Override
  public boolean canRender(String text) {
    return (COMPILATION_MESSAGE.test(text) || LINKER_MESSAGE.test(text));
  }

  @Override
  public String render(String text) {
    if (COMPILATION_MESSAGE.test(text)) return renderCompilationMessage(text);

    if (LINKER_MESSAGE.test(text)) return renderLinkerMessage(text);

    return text;
  }

  /*
   * Processes a compilation message line
   */
  private String renderCompilationMessage(String text) {
    try {
      int lineStart = text.indexOf(":") + ":".length();
      int lineEnd = text.indexOf(":", lineStart);
      int columnStart = lineEnd + ":".length();
      int columnEnd = text.indexOf(":", columnStart);
      String fileName = text.substring(0, text.indexOf(":")).trim();
      int lineNumber = Integer.valueOf(text.substring(lineStart, lineEnd).trim());
      int columnNumber = Integer.valueOf(text.substring(columnStart, columnEnd).trim());

      String customText =
          "<a href='javascript:openCM(\""
              + fileName
              + "\","
              + lineNumber
              + ","
              + columnNumber
              + ");'>";
      customText += text.substring(0, columnEnd);
      customText += "</a>";
      customText += text.substring(columnEnd);
      text = customText;
    } catch (IndexOutOfBoundsException ex) {
      // ignore
    } catch (NumberFormatException ex) {
      // ignore
    }

    return text;
  }

  /*
   * Processes a linker message line
   */
  private String renderLinkerMessage(String text) {
    try {
      int lineStart = text.indexOf(":") + ":".length(), lineEnd = text.indexOf(":", lineStart);
      String fileName = text.substring(0, text.indexOf(":")).trim();
      int lineNumber = Integer.valueOf(text.substring(lineStart, lineEnd).trim());

      String customText = "<a href='javascript:openLM(\"" + fileName + "\"," + lineNumber + ");'>";
      customText += text.substring(0, lineEnd);
      customText += "</a>";
      customText += text.substring(lineEnd);
      text = customText;
    } catch (IndexOutOfBoundsException ex) {
      // ignore
    } catch (NumberFormatException ex) {
      // ignore
    }

    return text;
  }

  /**
   * A callback that is to be called for a compilation message anchor
   *
   * @param fileName
   * @param lineNumber
   * @param columnNumber
   */
  public void handleCompilationMessageAnchorClick(
      String fileName, final int lineNumber, int columnNumber) {
    if (fileName == null) {
      return;
    }

    collectChildren(appContext.getWorkspaceRoot(), Path.valueOf(fileName))
        .then(
            files -> {
              if (!files.isEmpty()) {
                openFileInEditorAndReveal(
                    appContext, editorAgent, files.get(0).getLocation(), lineNumber, columnNumber);
              }
            });
  }

  /**
   * A callback that is to be called for a linker message anchor
   *
   * @param fileName
   * @param lineNumber
   */
  public void handleLinkerMessageAnchorClick(String fileName, final int lineNumber) {
    if (fileName == null) {
      return;
    }

    openFileInEditorAndReveal(
        appContext, editorAgent, Path.valueOf(fileName).removeFirstSegments(1), lineNumber, 0);
  }

  /** Sets up a C/CPP callback to be called for a compilation message anchor */
  public native void exportCompilationMessageAnchorClickHandlerFunction() /*-{
        var that = this;
        $wnd.openCM = $entry(function(fileName,lineNumber,columnNumber) {
            that.@org.eclipse.che.plugin.cpp.ide.console.CPPOutputRenderer::handleCompilationMessageAnchorClick(*)(fileName,lineNumber,columnNumber);
        });
    }-*/;

  /** Sets up a C/CPP callback to be called for a compilation message anchor */
  public native void exportLinkerMessageAnchorClickHandlerFunction() /*-{
        var that = this;
        $wnd.openLM = $entry(function(fileName,lineNumber) {
            that.@org.eclipse.che.plugin.cpp.ide.console.CPPOutputRenderer::handleLinkerMessageAnchorClick(*)(fileName,lineNumber);
        });
    }-*/;
}
