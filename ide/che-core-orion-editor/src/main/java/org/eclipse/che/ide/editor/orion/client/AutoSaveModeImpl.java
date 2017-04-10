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
package org.eclipse.che.ide.editor.orion.client;

import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import org.eclipse.che.api.project.shared.dto.TextChangeDto;
import org.eclipse.che.ide.api.editor.EditorInput;
import org.eclipse.che.ide.api.editor.EditorOpenedEvent;
import org.eclipse.che.ide.api.editor.EditorOpenedEventHandler;
import org.eclipse.che.ide.api.editor.EditorPartPresenter;
import org.eclipse.che.ide.api.editor.document.DocumentHandle;
import org.eclipse.che.ide.api.editor.events.DocumentChangeEvent;
import org.eclipse.che.ide.api.editor.reconciler.DirtyRegion;
import org.eclipse.che.ide.api.editor.reconciler.DirtyRegionQueue;
import org.eclipse.che.ide.api.editor.text.Region;
import org.eclipse.che.ide.api.editor.texteditor.TextEditor;
import org.eclipse.che.ide.api.event.ActivePartChangedEvent;
import org.eclipse.che.ide.api.event.ActivePartChangedHandler;
import org.eclipse.che.ide.api.event.EditorSettingsChangedEvent;
import org.eclipse.che.ide.api.event.EditorSettingsChangedEvent.EditorSettingsChangedHandler;
import org.eclipse.che.ide.api.parts.PartPresenter;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.api.resources.VirtualFile;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.editor.preferences.EditorPreferencesManager;
import org.eclipse.che.ide.jsonrpc.RequestTransmitter;
import org.eclipse.che.ide.util.loging.Log;

import java.util.HashSet;

import static org.eclipse.che.ide.api.editor.reconciler.DirtyRegion.REMOVE;
import static org.eclipse.che.ide.editor.preferences.editorproperties.EditorProperties.ENABLE_AUTO_SAVE;

/**
 * Default implementation of {@link AutoSaveMode} which provides autosave function.
 *
 * @author Roman Nikitenko
 */
public class AutoSaveModeImpl implements AutoSaveMode, EditorSettingsChangedHandler, ActivePartChangedHandler, EditorOpenedEventHandler {

    private static final int DELAY = 1000;

    private EventBus                 eventBus;
    private EditorPreferencesManager editorPreferencesManager;
    private DtoFactory               dtoFactory;
    private RequestTransmitter       requestTransmitter;
    private DocumentHandle           documentHandle;
    private TextEditor               editor;
    private EditorPartPresenter      activeEditor;
    private boolean                  isAutoSaveActivated;
    private DirtyRegionQueue         dirtyRegionQueue;

    private HashSet<HandlerRegistration> handlerRegistrations = new HashSet<>(4);

    private final Timer saveTimer = new Timer() {

        @Override
        public void run() {
//            Log.error(getClass(), "before save " + AutoSaveModeImpl.this.hashCode());
            save();
        }
    };


    @Inject
    public AutoSaveModeImpl(EventBus eventBus,
                            EditorPreferencesManager editorPreferencesManager,
                            DtoFactory dtoFactory,
                            RequestTransmitter requestTransmitter) {
        this.eventBus = eventBus;
        this.editorPreferencesManager = editorPreferencesManager;
        this.dtoFactory = dtoFactory;
        this.requestTransmitter = requestTransmitter;
        addHandlers();
    }

    @Override
    public void install(TextEditor editor) {
        this.editor = editor;
        this.dirtyRegionQueue = new DirtyRegionQueue();
        updateAutoSaveState();
    }

    @Override
    public void uninstall() {
        saveTimer.cancel();
        handlerRegistrations.forEach(HandlerRegistration::removeHandler);
    }

    @Override
    public void onDocumentChange(final DocumentChangeEvent event) {
        Log.error(getClass(), "//////////////////////////////////////////////////////////////// onDocumentChange " + this.hashCode());
//        Log.error(getClass(), "onDocumentChange " + this.hashCode());
        if (documentHandle == null || !documentHandle.isSameAs(event.getDocument())) {
            Log.error(getClass(), "onDocumentChange RETURN ");
            return;
        }

        if (editor != activeEditor) {
            Log.error(getClass(), "/////////////////////// editor != activeEditor" + activeEditor + "/// " + editor);
            return;
        }
        Log.error(getClass(), "/////////////////////// onDocumentChange Text " + event.getText());
        Log.error(getClass(), "/////////////////////// onDocumentChange Offset " + event.getOffset());
        Log.error(getClass(), "/////////////////////// onDocumentChange Length " + event.getLength());
        Log.error(getClass(), "/////////////////////// onDocumentChange RemoveCharCount " + event.getRemoveCharCount());

        createDirtyRegion(event);

        saveTimer.cancel();
        saveTimer.schedule(DELAY);
    }

    @Override
    public void onEditorSettingsChanged(EditorSettingsChangedEvent event) {
        updateAutoSaveState();
    }

    @Override
    public DocumentHandle getDocumentHandle() {
        return this.documentHandle;
    }

    @Override
    public void setDocumentHandle(final DocumentHandle handle) {
        this.documentHandle = handle;
    }

    @Override
    public void activate() {
        Boolean autoSaveValue = editorPreferencesManager.getBooleanValueFor(ENABLE_AUTO_SAVE);
//        Log.error(getClass(), "------ activate " + isAutoSaveActivated + " /// " + autoSaveValue);
        if (autoSaveValue != null && !autoSaveValue) {
//            Log.error(getClass(), "------ can not activate - false in preference");
            return;
        }

        isAutoSaveActivated = true;
//        Log.error(getClass(), "/// enableAutoSave " + isAutoSaveActivated);
        saveTimer.schedule(DELAY);
    }

    @Override
    public void deactivate() {
        isAutoSaveActivated = false;
//        Log.error(getClass(), "/// disableAutoSave " + isAutoSaveActivated);
        saveTimer.cancel();
    }

    @Override
    public boolean isActivated() {
//        Log.error(getClass(), "------ isActivated " + isAutoSaveActivated);
        return isAutoSaveActivated;
    }

    @Override
    public void onActivePartChanged(ActivePartChangedEvent event) {
        PartPresenter activePart = event.getActivePart();
        if (!(activePart instanceof EditorPartPresenter)) {
            return;
        }
        Log.error(getClass(), " onActivePartChanged active editor = " + activePart);
        activeEditor = (EditorPartPresenter)activePart;
    }

    private void addHandlers() {
        HandlerRegistration activePartChangedHandlerRegistration = eventBus.addHandler(ActivePartChangedEvent.TYPE, this);
        handlerRegistrations.add(activePartChangedHandlerRegistration);

        HandlerRegistration editorSettingsChangedHandlerRegistration = eventBus.addHandler(EditorSettingsChangedEvent.TYPE, this);
        handlerRegistrations.add(editorSettingsChangedHandlerRegistration);

        HandlerRegistration editorOpenedHandlerRegistration = eventBus.addHandler(EditorOpenedEvent.TYPE, this);
        handlerRegistrations.add(editorOpenedHandlerRegistration);
    }

    private void updateAutoSaveState() {
        Boolean autoSaveValue = editorPreferencesManager.getBooleanValueFor(ENABLE_AUTO_SAVE);
//        Log.error(getClass(), "------ updateAutoSaveState " + isAutoSaveActivated + " /// " + autoSaveValue);
        if (autoSaveValue == null) {
//            Log.error(getClass(), "------ updateAutoSaveState NULL");
            return;
        }

        if (isAutoSaveActivated && !autoSaveValue) {
            deactivate();
        } else if (!isAutoSaveActivated && autoSaveValue) {
            activate();
        }
    }

    private void save() {
        if (isAutoSaveActivated && editor.isDirty()) {
//            Log.error(getClass(),                      "1111 save isAutoSaveActivated && editor.isDirty()" + ((File)editor.getEditorInput().getFile()).getModificationStamp());

            editor.doSave(new AsyncCallback<EditorInput>() {
                @Override
                public void onFailure(Throwable throwable) {
                    Log.error(AutoSaveModeImpl.class, throwable);
                }

                @Override
                public void onSuccess(EditorInput editorInput) {
//                    Log.error(getClass(), "1111 save  onSuccess");

                }
            });
        } else {
//            Log.error(getClass(), "222 ELSE save isAutoSaveActivated && editor.isDirty()");
        }

        while (dirtyRegionQueue.getSize() > 0) {
            final DirtyRegion region = dirtyRegionQueue.removeNextDirtyRegion();
            parse(region);
        }
    }

    private VirtualFile getFile() {
        return editor.getEditorInput().getFile();
    }

    private void parse(final DirtyRegion dirtyRegion) {
        final VirtualFile file = getFile();

        if (file instanceof Resource) {
            final Project project = ((Resource)file).getProject();

            if (!project.exists()) {
                return;
            }


            //TODO handle illegal argument exception
//            final String fqn = resolveFQN(file);
            final String projectPath = project.getPath();
            final int length = dirtyRegion.getLength();
            final TextChangeDto change = dtoFactory.createDto(TextChangeDto.class)
                                                .withWorkingCopyOwnerID("")
                                                .withProjectPath(projectPath)
                                                .withFileLocation(file.getLocation().toString())
                                                .withOffset(dirtyRegion.getOffset())
                                                .withText(dirtyRegion.getText());
            if (REMOVE.equals(dirtyRegion.getType())) {
                change.setRemovedCharCount(length);
            } else {
                change.setLength(length);
            }

            Log.error(getClass(), "====///////////////////////////  before transmit dirty region " );
//            Log.error(getClass(), "====///////////////////////////  before transmit dirty change removed " + change.getRemovedCharCount());
//            Log.error(getClass(), "====///////////////////////////  before transmit dirty region " + ID);
            requestTransmitter.transmitOneToNone("ws-agent", "track:editor-changes", change);
        }
    }

    private void parse(Region region) {
        final VirtualFile file = getFile();
        if (file instanceof Resource) {
            final Project project = ((Resource)file).getProject();

            if (!project.exists()) {
                return;
            }

//            final String fqn = resolveFQN(file);
            final TextChangeDto change = dtoFactory.createDto(TextChangeDto.class)
                                                .withWorkingCopyOwnerID("")
                                                .withProjectPath(project.getPath())
                                                .withFileLocation(file.getLocation().toString())
                                                .withOffset(region.getOffset())
                                                .withLength(region.getLength())
                                                .withText(editor.getDocument().getContents());

//            Log.error(getClass(), "====///////////////////////////  before transmit whole document ");
            requestTransmitter.transmitOneToNone("ws-agent", "track:editor-changes", change);
        }
    }

    /**
     * Creates a dirty region for a document event and adds it to the queue.
     *
     * @param event the document event for which to create a dirty region
     */
    private void createDirtyRegion(final DocumentChangeEvent event) {
//        Log.error(getClass(), "-------------------- type offset " + event.getOffset());
//        Log.error(getClass(), "-------------------- type text " + event.getText());
//        Log.error(getClass(), "-------------------- type length " + event.getLength());
//        Log.error(getClass(), "-------------------- type RemoveCharCount " + event.getRemoveCharCount());
        if (event.getRemoveCharCount() == 0 && event.getText() != null && !event.getText().isEmpty()) {
//            Log.error(getClass(), "**** INSERT ");
            // Insert
            dirtyRegionQueue.addDirtyRegion(new DirtyRegion(event.getOffset(),
                                                            event.getLength(),
                                                            DirtyRegion.INSERT,
                                                            event.getText()));

        } else if (event.getText() == null || event.getText().isEmpty()) {
//            Log.error(getClass(), "**** REMOVE ");
            // Remove
            dirtyRegionQueue.addDirtyRegion(new DirtyRegion(event.getOffset(),
                                                            event.getRemoveCharCount(),
                                                            DirtyRegion.REMOVE,
                                                            null));

        } else {
//            Log.error(getClass(), "**** REMOVE + INSERT ");
            // Replace (Remove + Insert)
            dirtyRegionQueue.addDirtyRegion(new DirtyRegion(event.getOffset(),
                                                            event.getRemoveCharCount(),
                                                            DirtyRegion.REMOVE,
                                                            null));
            dirtyRegionQueue.addDirtyRegion(new DirtyRegion(event.getOffset(),
                                                            event.getLength(),
                                                            DirtyRegion.INSERT,
                                                            event.getText()));
        }
    }

    @Override
    public void onEditorOpened(EditorOpenedEvent event) {
        if (editor != event.getEditor()) {
            return;
        }

        if (documentHandle != null) {
            Log.error(getClass(), "************** subscribe to onDocumentChange event ");
            HandlerRegistration documentChangeHandlerRegistration =  documentHandle.getDocEventBus().addHandler(DocumentChangeEvent.TYPE, this);
            handlerRegistrations.add(documentChangeHandlerRegistration);
        }
    }
}
