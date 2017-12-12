/*******************************************************************************
 * Copyright (c) 2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.plugin.csharp.ide.console;

import static com.google.gwt.regexp.shared.RegExp.compile;

import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.console.AbstractOutputRenderer;
import org.eclipse.che.ide.resource.Path;

import com.google.gwt.regexp.shared.RegExp;

/**
 * Output renderer adds an anchor link to the lines that match a .NET C#
 * compilation error or warning message and a stack trace line pattern and
 * installs the handler functions for the links. The handler parses the stack
 * trace line, searches for a candidate C# file to navigate to, opens the found
 * file in editor and reveals it to the required line and column (if available)
 * according to the line information
 * 
 * @author Victor Rubezhny
 */
public class CSharpOutputRenderer extends AbstractOutputRenderer {

    private static final RegExp COMPILATION_ERROR_OR_WARNING = compile(
            "(.+\\.(cs|CS)\\(\\d+,\\d+\\): (error|warning) .+: .+ \\[.+\\])");
    private static final RegExp LINE_AT = compile("(\\s+at .+ in .+\\.(cs|CS):line \\d+)");

    /**
     * Constructs Compound Output Renderer Object
     * 
     * @param appContext
     * @param editorAgent
     */
    public CSharpOutputRenderer(String name, AppContext appContext, EditorAgent editorAgent) {
        super(name, appContext, editorAgent);

        exportCompilationMessageAnchorClickHandlerFunction();
        exportStacktraceLineAnchorClickHandlerFunction();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.che.ide.extension.machine.client.outputspanel.console.
     * OutputRenderer#canRender(java.lang.String)
     */
    @Override
    public boolean canRender(String text) {
        return (COMPILATION_ERROR_OR_WARNING.exec(text) != null || LINE_AT.exec(text) != null);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.che.ide.extension.machine.client.outputspanel.console.
     * OutputRenderer#render(java.lang.String)
     */
    @Override
    public String render(String text) {
        if (COMPILATION_ERROR_OR_WARNING.exec(text) != null)
            return renderCompilationErrorOrWarning(text);

        if (LINE_AT.exec(text) != null)
            return renderStacktraceLine(text);

        return text;
    }

    /*
     * Processes a Compilation Error/Warning line
     */
    private String renderCompilationErrorOrWarning(String text) {
        try {
            int end = text.indexOf("):"), openBracket = text.lastIndexOf("(", end), comma = text.lastIndexOf(",", end),
                    closeSBracket = text.lastIndexOf("]"), openSBracket = text.lastIndexOf("[", closeSBracket);
            String fileName = text.substring(0, openBracket).trim();
            String projectFileName = text.substring(openSBracket + "[".length(), closeSBracket).trim();
            int lineNumber = Integer.valueOf(text.substring(openBracket + "(".length(), comma).trim());
            int columnNumber = Integer.valueOf(text.substring(comma + ",".length(), end).trim());

            String customText = "<a href='javascript:openCSCM(\"" + fileName + "\",\"" + projectFileName + "\","
                    + lineNumber + "," + columnNumber + ");'>";
            customText += text.substring(0, end + ")".length());
            customText += "</a>";
            customText += text.substring(end + ")".length());
            text = customText;
        } catch (IndexOutOfBoundsException ex) {
            // ignore
        } catch (NumberFormatException ex) {
            // ignore
        }

        return text;
    }

    /*
     * Processes a Stacktrace line
     */
    private String renderStacktraceLine(String text) {
        try {
            int start = text.lastIndexOf(" in ") + " in ".length(), end = text.indexOf(":line ", start);

            String fileName = text.substring(start, end).trim();
            int lineNumber = Integer.valueOf(text.substring(end + ":line ".length()).trim());

            String customText = text.substring(0, start);
            customText += "<a href='javascript:openCSSTL(\"" + fileName + "\"," + lineNumber + ");'>";
            customText += text.substring(start);
            customText += "</a>";
            text = customText;
        } catch (IndexOutOfBoundsException ex) {
            // ignore
        } catch (NumberFormatException ex) {
            // ignore
        }

        return text;
    }

    /**
     * A callback that is to be called for an anchor for C# Compilation
     * Error/Warning Message
     * 
     * @param fileName
     * @param projectFile
     * @param lineNumber
     * @param columnNumber
     */
    public void handleCompilationMessageAnchorClick(String fileName, String projectFile, final int lineNumber,
            final int columnNumber) {
        if (fileName == null || projectFile == null) {
            return;
        }

        openFileInEditorAndReveal(appContext, editorAgent,
                Path.valueOf(projectFile).removeFirstSegments(1).parent().append(fileName), lineNumber, columnNumber);
    }

    /**
     * A callback that is to be called for an anchor for C# Runtime Exception
     * Stacktrace line
     * 
     * @param fileName
     * @param lineNumber
     */
    public void handleStacktraceLineAnchorClick(String fileName, int lineNumber) {
        if (fileName == null) {
            return;
        }

        openFileInEditorAndReveal(appContext, editorAgent,
                Path.valueOf(fileName).removeFirstSegments(1), lineNumber, 0);
    }

    /**
     * Sets up a java callback to be called for an anchor for C# Compilation
     * Error/Warning Message
     */
    public native void exportCompilationMessageAnchorClickHandlerFunction() /*-{
        var that = this;
        $wnd.openCSCM = $entry(function(fileName,projectFile,lineNumber,columnNumber) {
            that.@org.eclipse.che.plugin.csharp.ide.console.CSharpOutputRenderer::handleCompilationMessageAnchorClick(*)(fileName,projectFile,lineNumber,columnNumber);
        });
    }-*/;

    /**
     * Sets up a java callback to be called for an anchor for C# Runtime Exception
     * Stacktrace line
     */
    public native void exportStacktraceLineAnchorClickHandlerFunction() /*-{
        var that = this;
        $wnd.openCSSTL = $entry(function(fileName,lineNumber) {
            that.@org.eclipse.che.plugin.csharp.ide.console.CSharpOutputRenderer::handleStacktraceLineAnchorClick(*)(fileName,lineNumber);
        });
    }-*/;
}
