/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.plugin.java.languageserver;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.annotation.PostConstruct;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.languageserver.LanguageServiceUtils;
import org.eclipse.che.api.project.server.ProjectManager;
import org.eclipse.che.api.project.server.notification.PreProjectDeletedEvent;
import org.eclipse.che.api.project.server.notification.ProjectCreatedEvent;
import org.eclipse.che.api.project.server.notification.ProjectItemModifiedEvent;
import org.eclipse.che.jdt.ls.extension.api.dto.UpdateWorkspaceParameters;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitors projects activity and updates jdt.ls workspace.
 *
 * @author Anatolii Bazko
 */
@Singleton
public class ProjectsListener {
	  private static final Logger LOG = LoggerFactory.getLogger(ProjectsListener.class);

	  private final EventService eventService;
  private final ProjectManager projectManager;
  private final WorkspaceSynchronizer workspaceSynchronizer;

  @Inject
  public ProjectsListener(
      EventService eventService,
      ProjectManager projectManager,
      WorkspaceSynchronizer workspaceSynchronizer) {
    this.eventService = eventService;
    this.projectManager = projectManager;
    this.workspaceSynchronizer = workspaceSynchronizer;
  }

  @PostConstruct
  protected void initializeListeners() {
    eventService.subscribe(
        new EventSubscriber<ProjectCreatedEvent>() {
          @Override
          public void onEvent(ProjectCreatedEvent event) {
            onProjectCreated(event);
          }
        });

    eventService.subscribe(
        new EventSubscriber<PreProjectDeletedEvent>() {
          @Override
          public void onEvent(PreProjectDeletedEvent event) {
            onPreProjectDeleted(event);
          }
        });
    
    eventService.subscribe(
            new EventSubscriber<ProjectItemModifiedEvent>() {
              @Override
              public void onEvent(ProjectItemModifiedEvent event) {
                onProjectItemModified(event);
              }
            });
  }

  private void onProjectCreated(ProjectCreatedEvent event) {
    if (!isProjectRegistered(event.getProjectPath())) {
      return;
    }

    String projectUri = LanguageServiceUtils.prefixURI(event.getProjectPath());
    UpdateWorkspaceParameters params =
        new UpdateWorkspaceParameters(singletonList(projectUri), emptyList());
    workspaceSynchronizer.syncronizerWorkspaceAsync(params);
  }

  private void onPreProjectDeleted(PreProjectDeletedEvent event) {
    if (!isProjectRegistered(event.getProjectPath())) {
      return;
    }

    String projectUri = LanguageServiceUtils.prefixURI(event.getProjectPath());
    UpdateWorkspaceParameters params =
        new UpdateWorkspaceParameters(emptyList(), singletonList(projectUri));
    workspaceSynchronizer.syncronizerWorkspaceAsync(params);
  }

  private boolean isProjectRegistered(String path) {
    return projectManager.isRegistered(path);
  }
  
  private void onProjectItemModified(ProjectItemModifiedEvent event) {
      LOG.info("onProjectItemModified(): start: " + event.toString());
	  	   
	  if (!isProjectRegistered(event.getProject())) {
		  LOG.info("onProjectItemModified(): done: [!isProjectRegistered(event.getProject())]: " + event.toString());
        return;
      } 
	  
	  if (event.getType() == ProjectItemModifiedEvent.EventType.CREATED 
    		  && event.isFolder()) {
	    try {
	          LOG.error("onProjectItemModified(): FOLDER IS CREATED: " + event.getPath() 
	          	+ ": REFRESHING WORKSPACE: start");
			ResourcesPlugin.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, null);
          LOG.error("onProjectItemModified(): FOLDER IS CREATED: " + event.getPath() 
          	+ ": REFRESHING WORKSPACE: done");
		} catch (CoreException e) {
	          LOG.error("Can't refresh projects: " + event.getPath(), e);
		}
	  }
	  LOG.info("onProjectItemModified(): done: " + event.toString());
  }
}
