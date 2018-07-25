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
package org.eclipse.che.ide.ext.java.client.project.classpath;

import static com.google.common.base.Preconditions.checkState;
import static org.eclipse.che.ide.ext.java.shared.ClasspathEntryKind.LIBRARY;
import static org.eclipse.che.ide.ext.java.shared.ClasspathEntryKind.PROJECT;
import static org.eclipse.che.ide.ext.java.shared.ClasspathEntryKind.SOURCE;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseProvider;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.ext.java.client.service.JavaLanguageExtensionServiceClient;
import org.eclipse.che.ide.ext.java.shared.ClasspathEntryKind;
import org.eclipse.che.ide.project.node.ProjectClasspathChangedEvent;
import org.eclipse.che.jdt.ls.extension.api.dto.ClasspathEntry;
import org.eclipse.che.plugin.java.plain.client.service.ClasspathUpdaterServiceClient;

/**
 * Class supports project classpath. It reads classpath content, parses its and writes.
 *
 * @author Valeriy Svydenko
 */
@Singleton
public class ClasspathResolver
    implements ProjectClasspathChangedEvent.ProjectClasspathChangedHandler {
  private static final String WORKSPACE_PATH = "/projects";

  private final ClasspathUpdaterServiceClient classpathUpdater;
  private final JavaLanguageExtensionServiceClient extensionService;
  private final PromiseProvider promiseProvider;
  private final NotificationManager notificationManager;
  private final EventBus eventBus;
  private final AppContext appContext;
  private final DtoFactory dtoFactory;

  private Set<String> libs;
  private Set<String> sources;
  private Set<String> projects;
  private Set<ClasspathEntry> containers;

  @Inject
  public ClasspathResolver(
      ClasspathUpdaterServiceClient classpathUpdater,
      JavaLanguageExtensionServiceClient extensionService,
      PromiseProvider promiseProvider,
      NotificationManager notificationManager,
      EventBus eventBus,
      AppContext appContext,
      DtoFactory dtoFactory) {
    this.classpathUpdater = classpathUpdater;
    this.extensionService = extensionService;
    this.promiseProvider = promiseProvider;
    this.notificationManager = notificationManager;
    this.eventBus = eventBus;
    this.appContext = appContext;
    this.dtoFactory = dtoFactory;

    this.eventBus.addHandler(ProjectClasspathChangedEvent.getType(), this);
  }

  /** Reads and parses classpath entries. */
  public void resolveClasspathEntries(List<ClasspathEntry> entries) {
    libs = new HashSet<>();
    containers = new HashSet<>();
    sources = new HashSet<>();
    projects = new HashSet<>();
    for (ClasspathEntry entry : entries) {
      switch (entry.getEntryKind()) {
        case ClasspathEntryKind.LIBRARY:
          libs.add(entry.getPath());
          break;
        case ClasspathEntryKind.CONTAINER:
          containers.add(entry);
          break;
        case ClasspathEntryKind.SOURCE:
          sources.add(entry.getPath());
          break;
        case ClasspathEntryKind.PROJECT:
          projects.add(WORKSPACE_PATH + entry.getPath());
          break;
        default:
          // do nothing
      }
    }
  }

  /** Concatenates classpath entries and update classpath file. */
  public Promise<Void> updateClasspath() {

    final Resource resource = appContext.getResource();

    checkState(resource != null);

    Project optProject = resource.getProject();

    final List<ClasspathEntry> entries = new ArrayList<>();
    for (String path : libs) {
      entries.add(dtoFactory.createDto(ClasspathEntry.class).withPath(path).withEntryKind(LIBRARY));
    }

    entries.addAll(containers);

    for (String path : sources) {
      entries.add(dtoFactory.createDto(ClasspathEntry.class).withPath(path).withEntryKind(SOURCE));
    }

    for (String path : projects) {
      entries.add(dtoFactory.createDto(ClasspathEntry.class).withPath(path).withEntryKind(PROJECT));
    }

    String sStr = "";
    for (ClasspathEntry e : entries) {
      sStr += "\n\tPath: " + e.getPath();
    }

    log(
        "["
            + System.currentTimeMillis()
            + "] ClasspathResolver.updateClasspath(): about to call CP Updater: "
            + optProject.getLocation().toString() + " [" + optProject.hashCode() 
            + "], K_SOURCE: "
            + sStr);
    Promise<Void> promise =
        classpathUpdater.setRawClasspath(optProject.getLocation().toString(), entries);

    log(
        "["
            + System.currentTimeMillis()
            + "] ClasspathResolver.updateClasspath(): about to fire CP Updated Event: "
            + optProject.getLocation().toString()
            + ", K_SOURCE: "
            + sStr);
    /*
         promise
             .then(
                emptyResponse -> {
                  log(
                      "["
                          + System.currentTimeMillis()
                          + "] ClasspathResolver.updateClasspath(): about to sinch project: "
                          + optProject.getLocation().toString());

                  optProject
                      .synchronize()
                      .then(
                          resources -> {
                            String rStr = "";
                            for (Resource r : resources) {
                              rStr += "\n\t" + r.getName();
                            }
                            log(
                                "["
                                    + System.currentTimeMillis()
                                    + "] ClasspathResolver.updateClasspath(): sinch done: "
                                    + optProject.getLocation().toString()
                                    + ", resources: ["
                                    + rStr
                                    + "\n]");

                            eventBus.fireEvent(
                                new ClasspathChangedEvent(
                                    optProject.getLocation().toString(), entries));

                            appContext.getWorkspaceRoot().synchronize();
                            log(
                                "["
                                    + System.currentTimeMillis()
                                    + "] ClasspathResolver.updateClasspath(): ClasspathChangedEvent is sent: "
                                    + optProject.getLocation().toString());
                          });
                  log(
                      "["
                          + System.currentTimeMillis()
                          + "] ClasspathResolver.updateClasspath(): done with sinch project: "
                          + optProject.getLocation().toString());
                })
            .catchError(
                error -> {
                  notificationManager.notify(
                      "Problems with updating classpath", error.getMessage(), FAIL, EMERGE_MODE);
                });
    */
    log("[" + System.currentTimeMillis() + "] ClasspathResolver.updateClasspath(): done");
    return promise;
  }

  public static native void log(String message) /*-{
  if (window.console && console.log) console.log(message);
}-*/;

  /** Returns list of libraries from classpath. */
  public Set<String> getLibs() {
    return libs;
  }

  /** Returns list of containers from classpath. */
  public Set<ClasspathEntry> getContainers() {
    return containers;
  }

  /** Returns list of sources from classpath. */
  public Set<String> getSources() {
    return sources;
  }

  /** Returns list of projects from classpath. */
  public Set<String> getProjects() {
    return projects;
  }

  @Override
  public void onProjectClasspathChanged(ProjectClasspathChangedEvent event) {
    getEntriesFromServer(event.getProject())
    .then(entries -> {
	    String sStr = "";
	    for (ClasspathEntry e : entries) {
	      sStr += "\n\t\tPath: " + e.getPath();
	    }
	
	    log(
	        "["
	            + System.currentTimeMillis()
	            + "] ClasspathResolver.onProjectClasspathChanged(): start: reporting event:\n\tProject: "
	            + event.getProject()
	            + "\n\tSources: ["
	            + sStr
	            + "\n\t]");
	    eventBus.fireEvent(new ClasspathChangedEvent(event.getProject(), entries));
	    log("[" + System.currentTimeMillis() + "] ClasspathResolver.onProjectClasspathChanged(): done");
    	});
    }

  private Promise<List<ClasspathEntry>> getEntriesFromServer(String projectPath) {
        return
                extensionService
                    .classpathTree(projectPath)
                    .catchErrorPromise(
                        error -> {
                          return promiseProvider.reject(error);
                        });
   }
}
