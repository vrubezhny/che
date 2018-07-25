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
package org.eclipse.che.api.languageserver;

import static org.eclipse.che.api.languageserver.LanguageServiceUtils.prefixURI;

import com.google.common.annotations.VisibleForTesting;
import java.nio.file.PathMatcher;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.languageserver.RegistryContainer.Registry;
import org.eclipse.che.api.watcher.server.FileWatcherManager;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implement <a
 * href="https://github.com/Microsoft/language-server-protocol/blob/master/protocol.md#workspace_didChangeWatchedFiles">DidChangeWatchedFiles
 * Notification</a>
 *
 * @author Yevhen Vydolob
 */
@Singleton
class LanguageServerFileWatcher {
  private static final Logger LOG = LoggerFactory.getLogger(LanguageServerFileWatcher.class);

  private final FileWatcherManager watcherManager;
  private final EventService eventService;
  private final CopyOnWriteArrayList<Integer> watcherIds = new CopyOnWriteArrayList<>();
  private final Registry<Set<PathMatcher>> pathMatcherRegistry;

  @Inject
  LanguageServerFileWatcher(
      FileWatcherManager watcherManager,
      EventService eventService,
      RegistryContainer registryContainer) {
    this.watcherManager = watcherManager;
    this.eventService = eventService;
    this.pathMatcherRegistry = registryContainer.pathMatcherRegistry;
    LOG.info("[init]: LanguageServerFileWatcher is initialized");
  }

  @PostConstruct
  protected void subscribe() {
    eventService.subscribe(this::onServerInitialized, LanguageServerInitializedEvent.class);
    LOG.info("[subscribe]: LanguageServerFileWatcher is subscribed to listen LanguageServerInitializedEvent");
  }

  private void send(LanguageServer server, String filePath, FileChangeType changeType) {
    logCallStack("[send]: reporting: ChgType: "
  +changeType.name()+
  ", File: "+filePath+", to server: "+ server.toString());

    DidChangeWatchedFilesParams params =
        new DidChangeWatchedFilesParams(
            Collections.singletonList(new FileEvent(prefixURI(filePath), changeType)));
    server.getWorkspaceService().didChangeWatchedFiles(params);
  }

  @PreDestroy
  @VisibleForTesting
  void removeAllWatchers() {
	    LOG.info("[removeAllWatchers]: removing all watchers");

	  for (Integer watcherId : watcherIds) {
      watcherManager.unRegisterByMatcher(watcherId);
    }
  }

  private void onServerInitialized(LanguageServerInitializedEvent event) {
    LOG.debug("Registering file watching operations for language server : {}", event.getId());
    logCallStack("Registering file watching operations for language server : " + event.getId());

    String id = event.getId();
    LanguageServer languageServer = event.getLanguageServer();

    Set<PathMatcher> pathMatchers = pathMatcherRegistry.getOrNull(id);
    if (pathMatchers == null) {
      return;
    }

    for (PathMatcher pathMatcher : pathMatchers) {
    	LOG.info("[onServerInitialized] registering handlers by matcher: {}",
    			pathMatcher.getClass().getName());
      int watcherId =
          watcherManager.registerByMatcher(
              pathMatcher,
              s -> send(languageServer, s, FileChangeType.Created),
              s -> send(languageServer, s, FileChangeType.Changed),
              s -> send(languageServer, s, FileChangeType.Deleted));

      watcherIds.add(watcherId);
    }
  }
  
  public static void logCallStack(String msg) {
    Exception e = new Exception(msg);
    StackTraceElement[] stElements = e.getStackTrace();
    String result = msg + ":";
    for (StackTraceElement ste : stElements) {
      result += "\n\t" + ste.toString();
    }
    LOG.info(result);
  }
}
