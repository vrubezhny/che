/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.plugin.java.plain.server.inject;

import static java.nio.file.Files.isDirectory;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.eclipse.che.api.languageserver.LanguageServiceUtils.prefixURI;
import static org.eclipse.che.api.languageserver.LanguageServiceUtils.removePrefixUri;
import static org.eclipse.che.api.languageserver.LanguageServiceUtils.removeUriScheme;
import static org.eclipse.che.plugin.java.plain.shared.PlainJavaProjectConstants.DEFAULT_SOURCE_FOLDER_VALUE;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.languageserver.LanguageServerInitializedEvent;
import org.eclipse.che.api.project.server.ProjectManager;
import org.eclipse.che.api.project.server.notification.ProjectUpdatedEvent;
import org.eclipse.che.api.project.shared.RegisteredProject;
import org.eclipse.che.api.watcher.server.FileWatcherManager;
import org.eclipse.che.api.watcher.server.impl.FileWatcherByPathMatcher;
import org.eclipse.che.commons.lang.concurrent.LoggingUncaughtExceptionHandler;
import org.eclipse.che.plugin.java.languageserver.JavaLanguageServerExtensionService;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports the create/update/delete changes on project source folders to jdt.ls
 *
 * @author V. Rubezhny
 */
public class PlainJavaProjectSourceFolderWatcher {
  private static final Logger LOG =
      LoggerFactory.getLogger(PlainJavaProjectSourceFolderWatcher.class);

  private static final List<String> EMPTY_SOURCE_FOLDERS = emptyList();

  private final FileWatcherManager manager;
  private final FileWatcherByPathMatcher matcher;
  private final EventService eventService;
  private final ExecutorService executorService;

  private final CopyOnWriteArrayList<Integer> watcherIds = new CopyOnWriteArrayList<>();
  private final JavaLanguageServerExtensionService extensionService;
  private final CopyOnWriteArrayList<LanguageServer> languageServers = new CopyOnWriteArrayList<>();

  private final ProjectManager projectManager;

  @Inject
  public PlainJavaProjectSourceFolderWatcher(
      FileWatcherManager manager,
      FileWatcherByPathMatcher matcher,
      EventService eventService,
      JavaLanguageServerExtensionService extensionService,
      ProjectManager projectManager) {
    this.manager = manager;
    this.matcher = matcher;
    this.eventService = eventService;
    this.extensionService = extensionService;
    this.projectManager = projectManager;
    this.executorService =
        Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder()
                .setNameFormat("WorkspaceUpdater-%d")
                .setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.getInstance())
                .setDaemon(true)
                .build());
  }

  @PostConstruct
  protected void startWatchers() {
    int watcherId =
        manager.registerByMatcher(
            folderMatcher(),
            s -> report(s, FileChangeType.Created),
            s -> report(s, FileChangeType.Changed),
            s -> report(s, FileChangeType.Deleted));

    watcherIds.add(watcherId);
    eventService.subscribe(this::onServerInitialized, LanguageServerInitializedEvent.class);
    eventService.subscribe(this::onProjectUpdated, ProjectUpdatedEvent.class);
  }

  @PreDestroy
  public void stopWatchers() {
    watcherIds.stream().forEach(id -> manager.unRegisterByMatcher(id));
  }

  private void onServerInitialized(LanguageServerInitializedEvent event) {
    LOG.debug(
        "Registering source folder watching operations for language server : {}", event.getId());

    String id = event.getId();
    LanguageServer languageServer = event.getLanguageServer();
    languageServers.add(languageServer);
  }

  private void onProjectUpdated(ProjectUpdatedEvent event) {
    executorService.submit(
        () -> {
          setInitialPathsToWatch(prefixURI(event.getProjectPath()));
        });
  }

  private PathMatcher folderMatcher() {
    return it -> isSourceFolder(it);
  }

  private void report(String path, FileChangeType changeType) {
    languageServers.stream().forEach(ls -> send(ls, path, changeType));
  }

  private void send(LanguageServer server, String path, FileChangeType changeType) {
    RegisteredProject project = projectManager.getClosestOrNull(path);
    if (project == null) {
      return;
    }
    DidChangeWatchedFilesParams params =
        new DidChangeWatchedFilesParams(
            Collections.singletonList(new FileEvent(prefixURI(path), changeType)));
    LOG.info("[send] Server {}, Path {}, ChangeType: {}", server, path, changeType);
    server.getWorkspaceService().didChangeWatchedFiles(params);
  }

  private boolean isSourceFolder(Path path) {
    if (!isDirectory(path)) {
      LOG.info("[isSourceFolder] Path {}: [!isDirectory]: FALSE", path);
      return false;
    }

    String wsPath = removePrefixUri(path.toUri().toString());
    RegisteredProject project = projectManager.getClosestOrNull(wsPath);
    if (project == null) {
      LOG.info("[isSourceFolder] Path {}: [getClosestOrNull(wsPath) == null]: FALSE", path);
      return false;
    }

    boolean res =
        (getSourceFolders(project.getPath())
                .stream()
                .filter(p -> Paths.get(wsPath).startsWith(Paths.get(p)))
                .count()
            > 0);

    LOG.info("[isSourceFolder] Path {}: result: {}", path, (res ? "TRUE" : "FALSE"));

    return res;
  }

  private void setInitialPathsToWatch(String projectUri) {
    //    LOG.info("[setInitialPathsToWatch] project {}", projectUri);
    List<String> sourceFolderLocations;
    try {
      sourceFolderLocations = extensionService.getAllSourceFoldersLocations(projectUri);
    } catch (Exception e) {
      return;
    }
    if (sourceFolderLocations != null) {
      sourceFolderLocations
          .stream()
          .forEach(f -> matcher.accept(Paths.get(removeUriScheme(prefixURI(f)))));
    }
  }

  private List<String> getSourceFolders(String path) {
    List<String> sourceFolders;
    try {
      sourceFolders = extensionService.getSourceFolders(path);
    } catch (Exception e) {
      return EMPTY_SOURCE_FOLDERS;
    }

    List<String> filteredResult =
        sourceFolders
            .stream()
            .filter(p -> Paths.get(p).startsWith(Paths.get(path)))
            .collect(toList());

    return filteredResult.isEmpty() ? singletonList(DEFAULT_SOURCE_FOLDER_VALUE) : filteredResult;
  }
}
