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
package org.eclipse.che.ide.ext.java.client.console;

import static com.google.gwt.regexp.shared.RegExp.compile;

import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.console.AbstractOutputRenderer;
import org.eclipse.che.ide.resource.Path;

import com.google.gwt.regexp.shared.MatchResult;
import com.google.gwt.regexp.shared.RegExp;

/**
 * Java renderer adds an anchor link to the lines that match a stack trace
 * line pattern and installs a handler function for the link. The handler parses
 * the stack trace line, searches for the candidate Java files to navigate to,
 * opens the first file (of the found candidates) in editor and reveals it to
 * the required line according to the stack trace line information
 * 
 * @author Victor Rubezhny
 */
public class JavaOutputRenderer extends AbstractOutputRenderer {

    private static final RegExp LINE_AT = compile("(\\s+at .+\\(.+\\.java:\\d+\\))");

    /**
     * Constructs Java Output Renderer Object
     * 
     * @param appContext
     * @param editorAgent
     */
    public JavaOutputRenderer(String name, AppContext appContext, EditorAgent editorAgent) {
        super(name, appContext, editorAgent);
 
        exportAnchorClickHandlerFunction();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.che.ide.extension.machine.client.outputspanel.console.
     * OutputRenderer#canRender(java.lang.String)
     */
    @Override
    public boolean canRender(String text) {
        return (LINE_AT.exec(text) != null);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.che.ide.extension.machine.client.outputspanel.console.
     * OutputRenderer#render(java.lang.String)
     */
    @Override
    public String render(String text) {
        MatchResult matcher = LINE_AT.exec(text);
        if (matcher != null) {
            try {
                int start = text.indexOf("at", 0) + "at".length(), openBracket = text.indexOf("(", start),
                        column = text.indexOf(":", openBracket), closingBracket = text.indexOf(")", column);
                String qualifiedName = text.substring(start, openBracket).trim();
                String fileName = text.substring(openBracket + "(".length(), column).trim();
                int lineNumber = Integer.valueOf(text.substring(column + ":".length(), closingBracket).trim());

                String customText = text.substring(0, openBracket + "(".length());
                customText += "<a href='javascript:open(\"" + qualifiedName + "\", \"" + fileName + "\", " + lineNumber
                        + ");'>";
                customText += text.substring(openBracket + "(".length(), closingBracket);
                customText += "</a>";
                customText += text.substring(closingBracket);
                text = customText;
            } catch (IndexOutOfBoundsException ex) {
                // ignore
            } catch (NumberFormatException ex) {
                // ignore
            }
        }

        return text;
    }

    /**
     * A callback that is to be called for an anchor
     * 
     * @param qualifiedName
     * @param fileName
     * @param lineNumber
     */
    public void handleAnchorClick(String qualifiedName, String fileName, final int lineNumber) {
        if (qualifiedName == null || fileName == null) {
            return;
        }

        String qualifiedClassName = qualifiedName.lastIndexOf('.') != -1
                ? qualifiedName.substring(0, qualifiedName.lastIndexOf('.'))
                : qualifiedName;
        final String packageName = qualifiedClassName.lastIndexOf('.') != -1
                ? qualifiedClassName.substring(0, qualifiedClassName.lastIndexOf('.'))
                : "";

        String relativeFilePath = (packageName.isEmpty() ? "" : (packageName.replace(".", "/") + "/")) + fileName;

        collectChildren(appContext.getWorkspaceRoot(), Path.valueOf(relativeFilePath))
        .then(files -> {
            if (!files.isEmpty()) {
                openFileInEditorAndReveal(appContext, editorAgent, files.get(0).getLocation(), lineNumber, 0);
            }
        });
    }

    /*
     * Sets up a java callback to be called for an anchor
     */
    private native void exportAnchorClickHandlerFunction() /*-{
        var that = this;
        $wnd.open = $entry(function(qualifiedName,fileName,lineNumber) {
            that.@org.eclipse.che.ide.ext.java.client.console.JavaOutputRenderer::handleAnchorClick(*)(qualifiedName,fileName,lineNumber);
        });
    }-*/;
}
