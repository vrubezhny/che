/*******************************************************************************
 * Copyright (c) 2012-2015 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/

package org.eclipse.che.jdt.javaeditor;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.jsonrpc.RequestTransmitter;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.project.server.ProjectManager;
import org.eclipse.che.api.project.server.VirtualFileEntry;
import org.eclipse.che.api.project.server.notification.WorkingCopyChangedEvent;
import org.eclipse.che.api.project.shared.dto.TextChangeDto;
import org.eclipse.che.api.project.shared.dto.event.FileTrackingOperationDto;
import org.eclipse.che.api.vfs.impl.file.event.detectors.FileTrackingOperationEvent;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.ide.ext.java.shared.dto.HighlightedPosition;
import org.eclipse.che.ide.ext.java.shared.dto.Problem;
import org.eclipse.che.ide.ext.java.shared.dto.ReconcileResult;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.core.ClassFileWorkingCopy;
import org.eclipse.jdt.internal.core.JavaModel;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.ui.javaeditor.DocumentAdapter;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.TextEdit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Arrays.asList;
import static org.eclipse.jdt.core.IJavaElement.COMPILATION_UNIT;

/**
 * @author Evgen Vidolob
 */
@Singleton
public class JavaReconciler {
    private static final Logger    LOG             = LoggerFactory.getLogger(JavaReconciler.class);
    private static final JavaModel JAVA_MODEL      = JavaModelManager.getJavaModelManager().getJavaModel();
    private static final String    OUTGOING_METHOD = "event:java-reconcile-state-changed";

    //TODO clean up when close/refresh
    private final Map<String, WorkingCopyOwner> workingCopyOwnersStorage;
    private final Map<String, ProblemRequestor> problemRequestorStorage;

    private final RequestTransmitter             transmitter;
    private final ProjectManager                 projectManager;
    private final SemanticHighlightingReconciler semanticHighlighting;

    @Inject
    public JavaReconciler(SemanticHighlightingReconciler semanticHighlighting,
                          EventService eventService,
                          RequestTransmitter transmitter,
                          ProjectManager projectManager) {
        this.semanticHighlighting = semanticHighlighting;
        this.transmitter = transmitter;
        this.projectManager = projectManager;
        workingCopyOwnersStorage = new HashMap<>();
        problemRequestorStorage = new HashMap<>();
        eventService.subscribe(new EventSubscriber<FileTrackingOperationEvent>() {
            @Override
            public void onEvent(FileTrackingOperationEvent event) {
                onFileOperation(event.getEndpointId(), event.getFileTrackingOperation());
            }
        });

        eventService.subscribe(new EventSubscriber<WorkingCopyChangedEvent>() {
            @Override
            public void onEvent(WorkingCopyChangedEvent event) {
                onWorkingCopyChanged(event.getEndpointId(), event.getTextChange());
            }
        });
    }

    private void onWorkingCopyChanged(String endpointId, TextChangeDto textChange) {
        try {
            String filePath = textChange.getFileLocation();
            String projectPath = textChange.getProjectPath();
            ICompilationUnit compilationUnit = getCompilationUnit(filePath, projectPath);
            if (compilationUnit == null) {
                return;
            }

            ReconcileResult result = reconcile(getJavaProject(projectPath), textChange, compilationUnit);
            result.setWorkingCopyOwnerID(filePath);
            transmitter.transmitOneToNone(endpointId, OUTGOING_METHOD, result);
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
    }

    private void onFileOperation(String endpointId, FileTrackingOperationDto operation) {
        try {
            String filePath = operation.getPath();
            VirtualFileEntry fileEntry = projectManager.getProjectsRoot().getChild(filePath);
            String projectPath = fileEntry.getProject();

            FileTrackingOperationDto.Type type = operation.getType();
            switch (type) {
                case START: {
                    ICompilationUnit compilationUnit = getCompilationUnit(filePath, projectPath);
                    if (compilationUnit == null) {
                        return;
                    }
                    WorkingCopyOwner workingCopyOwner = provideWorkingCopyOwner(filePath);
                    ProblemRequestor problemRequestor = problemRequestorStorage.get(filePath);


                    ReconcileResult result = reconcile(compilationUnit, getJavaProject(projectPath), workingCopyOwner, problemRequestor);
                    result.setWorkingCopyOwnerID(filePath);
                    transmitter.transmitOneToNone(endpointId, OUTGOING_METHOD, result);
                    break;
                }
                case STOP: {
                    discardWorkingCopy(filePath, projectPath);
                    break;
                }

                case MOVE: {
                    String oldPath = operation.getOldPath();
                    WorkingCopyOwner workingCopyOwner = workingCopyOwnersStorage.remove(oldPath);
                    if (workingCopyOwner != null) {
                        workingCopyOwnersStorage.put(filePath, workingCopyOwner);
                    }

                    ProblemRequestor problemRequestor = problemRequestorStorage.remove(oldPath);
                    if (problemRequestor != null) {
                        problemRequestorStorage.put(filePath, problemRequestor);
                    }
                    break;
                }

                default: {


                    break;
                }
            }

        } catch (ServerException e) {
            e.printStackTrace();
        } catch (JavaModelException e) {
            //TODO
        }

    }

    private void discardWorkingCopy(String filePath, String projectPath) throws JavaModelException {
        problemRequestorStorage.remove(filePath);
        WorkingCopyOwner wcOwner = workingCopyOwnersStorage.remove(filePath);

        ICompilationUnit compilationUnit = getCompilationUnit(filePath, projectPath);
        if (compilationUnit == null) {
            return;
        }

        ICompilationUnit workingCopy = compilationUnit.getWorkingCopy(wcOwner, null);
        if (workingCopy != null && workingCopy.isWorkingCopy()) {
            try {
                workingCopy.getBuffer().close();
                workingCopy.discardWorkingCopy();
            } catch (JavaModelException e) {
                //ignore
            }
        }
    }

    @Nullable
    private ICompilationUnit getCompilationUnit(String filePath, String projectPath) throws JavaModelException {
        IJavaProject javaProject = getJavaProject(projectPath);
        if (javaProject == null) {
            return null;
        }

        List<IClasspathEntry> classpathEntries = asList(javaProject.getRawClasspath());
        for (IClasspathEntry classpathEntry : classpathEntries) {
            String entryPath = classpathEntry.getPath().toString();
            if (!filePath.contains(entryPath)) {
                continue;
            }

            String fileRelativePath = filePath.substring(entryPath.length() + 1);
            IJavaElement javaElement = javaProject.findElement(new Path(fileRelativePath));
            int elementType = javaElement.getElementType();
            if (COMPILATION_UNIT == elementType) {
                return (ICompilationUnit)javaElement;
            }
        }
        return null;
    }

    @Nullable
    private IJavaProject getJavaProject(String projectPath) throws JavaModelException {
        IJavaProject project = JAVA_MODEL.getJavaProject(projectPath);
        List<IJavaProject> javaProjects = asList(JAVA_MODEL.getJavaProjects());
        return javaProjects.contains(project) ? project : null;
    }

    //TODO close buffer and discard working copy when save? close
    public ReconcileResult reconcile(IJavaProject javaProject, TextChangeDto change, ICompilationUnit compilationUnit)
            throws JavaModelException {


        final String fileLocation = change.getFileLocation();
        checkState(!isNullOrEmpty(fileLocation), "Can not recognize working copy owner for " + change.getFileLocation());

        final WorkingCopyOwner wcOwner = provideWorkingCopyOwner(fileLocation);
        final ProblemRequestor requestor = problemRequestorStorage.get(fileLocation);
        final ICompilationUnit workingCopy = compilationUnit.getWorkingCopy(wcOwner, null);
        final int offset = change.getOffset();
        final String text = change.getText();
        final int removedCharCount = change.getRemovedCharCount();

        TextEdit textEdit = null;
        if (text != null && !text.isEmpty()) {
            textEdit = new InsertEdit(offset, text);
        } else if (removedCharCount > 0) {
            textEdit = new DeleteEdit(offset, removedCharCount);
        }

        if (textEdit != null) {
            workingCopy.applyTextEdit(textEdit, null);
        }

        ReconcileResult reconcileResult = reconcile(compilationUnit, javaProject, wcOwner, requestor);
        reconcileResult.setWorkingCopyOwnerID(fileLocation);
        return reconcileResult;
    }

    private ReconcileResult reconcile(ICompilationUnit compilationUnit, IJavaProject javaProject, WorkingCopyOwner wcOwner,
                                      ProblemRequestor requestor)
            throws JavaModelException {
        List<HighlightedPosition> positions;
        try {


            final ICompilationUnit workingCopy = compilationUnit.getWorkingCopy(wcOwner, null);
            requestor.reset();

            final CompilationUnit unit = workingCopy.reconcile(AST.JLS8, true, wcOwner, null);
            positions = semanticHighlighting.reconcileSemanticHighlight(unit);

            if (workingCopy instanceof ClassFileWorkingCopy) {
                //we don't wont to show any errors from ".class" files
                requestor.reset();
            }
        } catch (JavaModelException e) {
            LOG.error(
                    "Can't reconcile class: " + compilationUnit.getPath().toString() + " in project:" + javaProject.getPath().toOSString(),
                    e);
            throw e;
        }

        ReconcileResult result = DtoFactory.getInstance().createDto(ReconcileResult.class);
        result.setProblems(convertProblems(requestor.problems));
        result.setHighlightedPositions(positions);
        return result;
    }

    //TODO from reconcile service
    public ReconcileResult reconcile(IJavaProject javaProject, String fqn) throws JavaModelException {
        final IType type = getType(fqn, javaProject);
        final ProblemRequestor requestor = new ProblemRequestor();
        final WorkingCopyOwner wcOwner = new WorkingCopyOwner() {
            public IProblemRequestor getProblemRequestor(ICompilationUnit unit) {
                return requestor;
            }

            @Override
            public IBuffer createBuffer(ICompilationUnit workingCopy) {
                return new DocumentAdapter(workingCopy, (IFile)workingCopy.getResource());
            }
        };

        try {
            return reconcile(type.getCompilationUnit(), javaProject, wcOwner, requestor);
        } finally {
            final ICompilationUnit compilationUnit = type.getCompilationUnit().getWorkingCopy(wcOwner, null);
            if (compilationUnit != null && compilationUnit.isWorkingCopy()) {
                try {
                    compilationUnit.getBuffer().close();
                    compilationUnit.discardWorkingCopy();
                } catch (JavaModelException e) {
                    //ignore
                }
            }
        }
    }

    private WorkingCopyOwner provideWorkingCopyOwner(String filePath) {
        if (workingCopyOwnersStorage.containsKey(filePath)) {
            return workingCopyOwnersStorage.get(filePath);
        }

        final ProblemRequestor requestor = new ProblemRequestor();
        final WorkingCopyOwner wcOwner = new WorkingCopyOwner() {
            public IProblemRequestor getProblemRequestor(ICompilationUnit unit) {
                return requestor;
            }

            @Override
            public IBuffer createBuffer(ICompilationUnit workingCopy) {
                return new DocumentAdapter(workingCopy, (IFile)workingCopy.getResource());
            }
        };

        workingCopyOwnersStorage.put(filePath, wcOwner);
        problemRequestorStorage.put(filePath, requestor);
        return wcOwner;
    }

    private List<Problem> convertProblems(List<IProblem> problems) {
        List<Problem> result = new ArrayList<>(problems.size());
        for (IProblem problem : problems) {
            result.add(convertProblem(problem));
        }
        return result;
    }

    private Problem convertProblem(IProblem problem) {
        Problem result = DtoFactory.getInstance().createDto(Problem.class);

        result.setArguments(asList(problem.getArguments()));
        result.setID(problem.getID());
        result.setMessage(problem.getMessage());
        result.setOriginatingFileName(new String(problem.getOriginatingFileName()));
        result.setError(problem.isError());
        result.setWarning(problem.isWarning());
        result.setSourceEnd(problem.getSourceEnd());
        result.setSourceStart(problem.getSourceStart());
        result.setSourceLineNumber(problem.getSourceLineNumber());

        return result;
    }

    private IType getType(String fqn, IJavaProject javaProject) throws JavaModelException {
        checkState(!isNullOrEmpty(fqn), "Incorrect fully qualified name is specified");

        final IType type = javaProject.findType(fqn);
        checkState(type != null, "Can not find type for " + fqn);
        checkState(!type.isBinary(), "Can't reconcile binary type: " + fqn);
        return type;
    }

    private class ProblemRequestor implements IProblemRequestor {

        private List<IProblem> problems = new ArrayList<>();

        @Override
        public void acceptProblem(IProblem problem) {
            problems.add(problem);
        }

        @Override
        public void beginReporting() {

        }

        @Override
        public void endReporting() {

        }

        @Override
        public boolean isActive() {
            return true;
        }

        public void reset() {
            problems.clear();
        }
    }
}
