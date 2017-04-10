/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.plugin.languageserver.ide.editor;

import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.editor.EditorPartPresenter;
import org.eclipse.che.ide.api.editor.annotation.AnnotationModel;
import org.eclipse.che.ide.api.editor.editorconfig.TextEditorConfiguration;
import org.eclipse.che.ide.api.editor.texteditor.TextEditor;
import org.eclipse.che.ide.resource.Path;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * @author Anatolii Bazko
 */
@Singleton
public class PublishDiagnosticsProcessor {
    
    private final EditorAgent editorAgent;
    
    @Inject 
    public PublishDiagnosticsProcessor(EditorAgent editorAgent) {
        this.editorAgent = editorAgent;
    }

    public void processDiagnostics(PublishDiagnosticsParams diagnosticsMessage) {
        EditorPartPresenter openedEditor = editorAgent.getOpenedEditor(new Path(diagnosticsMessage.getUri()));
        //TODO add markers
        if (openedEditor == null) {
            return;
        }

        if (openedEditor instanceof TextEditor) {
            TextEditorConfiguration editorConfiguration = ((TextEditor) openedEditor).getConfiguration();
            AnnotationModel annotationModel = editorConfiguration.getAnnotationModel();
            if (annotationModel != null && annotationModel instanceof DiagnosticCollector) {
                DiagnosticCollector collector = (DiagnosticCollector)annotationModel;
                collector.beginReporting();
                try {
                    for (Diagnostic diagnostic : diagnosticsMessage.getDiagnostics()) {
                        collector.acceptDiagnostic(diagnostic);
                    }
                } finally {
                    collector.endReporting();
                }
            }
        }
    }

}
