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
package org.eclipse.che.api.watcher.server.impl;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.walkFileTree;
import static java.util.stream.Collectors.toSet;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.PostConstruct;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.project.server.impl.RootDirPathProvider;
import org.eclipse.che.commons.schedule.ScheduleRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Walks a file system tree, register addition, update and removal of file system items. On events
 * runs corresponding consumers that can be registered in DI configuration modules.
 */
@Singleton
public class FileTreeWalker {
  private static final Logger LOG = LoggerFactory.getLogger(FileTreeWalker.class);

  private final Path root;

  private final Set<Consumer<Path>> directoryUpdateConsumers;
  private final Set<Consumer<Path>> directoryCreateConsumers;
  private final Set<Consumer<Path>> directoryDeleteConsumers;
  private final Set<PathMatcher> directoryExcludes;

  private final Set<Consumer<Path>> fileUpdateConsumers;
  private final Set<Consumer<Path>> fileCreateConsumers;
  private final Set<Consumer<Path>> fileDeleteConsumers;
  private final Set<PathMatcher> fileExcludes;

  private final Map<Path, Long> files = new HashMap<>();
  private final Map<Path, Long> directories = new HashMap<>();

  private boolean initialized;

  @Inject
  public FileTreeWalker(
      RootDirPathProvider pathProvider,
      @Named("che.fs.directory.update") Set<Consumer<Path>> directoryUpdateConsumers,
      @Named("che.fs.directory.create") Set<Consumer<Path>> directoryCreateConsumers,
      @Named("che.fs.directory.delete") Set<Consumer<Path>> directoryDeleteConsumers,
      @Named("che.fs.directory.excludes") Set<PathMatcher> directoryExcludes,
      @Named("che.fs.file.update") Set<Consumer<Path>> fileUpdateConsumers,
      @Named("che.fs.file.create") Set<Consumer<Path>> fileCreateConsumers,
      @Named("che.fs.file.delete") Set<Consumer<Path>> fileDeleteConsumers,
      @Named("che.fs.file.excludes") Set<PathMatcher> fileExcludes) {
    this.root = Paths.get(pathProvider.get());

    this.directoryUpdateConsumers = directoryUpdateConsumers;
    this.directoryCreateConsumers = directoryCreateConsumers;
    this.directoryDeleteConsumers = directoryDeleteConsumers;

    this.fileUpdateConsumers = fileUpdateConsumers;
    this.fileCreateConsumers = fileCreateConsumers;
    this.fileDeleteConsumers = fileDeleteConsumers;

    this.directoryExcludes = directoryExcludes;
    this.fileExcludes = fileExcludes;
  }

  @PostConstruct
  void initialize() {
    try {
      walkFileTree(
          root,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              for (PathMatcher matcher : directoryExcludes) {
                if (matcher.matches(dir)) {
                  return SKIP_SUBTREE;
                }
              }

              directories.put(dir, attrs.lastModifiedTime().toMillis());

              return CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              for (PathMatcher matcher : fileExcludes) {
                if (matcher.matches(file)) {
                  return CONTINUE;
                }
              }

              files.put(file, attrs.lastModifiedTime().toMillis());

              return CONTINUE;
            }
          });
    } catch (IOException e) {
      LOG.error("Error while walking file tree", e);
    }

    initialized = true;
  }

  @ScheduleRate(period = 10)
  void walk() {
    if (!initialized) {
      return;
    }

    try {
      LOG.debug("Tree walk started");

      Set<Path> deletedFiles = files.keySet().stream().filter(it -> !exists(it)).collect(toSet());
      fileDeleteConsumers.forEach(deletedFiles::forEach);
      files.keySet().removeAll(deletedFiles);

      Set<Path> deletedDirectories =
          directories.keySet().stream().filter(it -> !exists(it)).collect(toSet());
      directoryDeleteConsumers.forEach(deletedDirectories::forEach);
      directories.keySet().removeAll(deletedDirectories);

      walkFileTree(
          root,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
              for (PathMatcher matcher : directoryExcludes) {
                if (matcher.matches(dir)) {
                  return SKIP_SUBTREE;
                }
              }

              updateFsTreeAndAcceptConsumables(
                  directories, directoryUpdateConsumers, directoryCreateConsumers, dir, attrs);

              return CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              for (PathMatcher matcher : fileExcludes) {
                if (matcher.matches(file)) {
                  return CONTINUE;
                }
              }

              updateFsTreeAndAcceptConsumables(
                  files, fileUpdateConsumers, fileCreateConsumers, file, attrs);

              return CONTINUE;
            }
          });
      LOG.debug("Tree walk finished");
    } catch (NoSuchFileException e) {
      LOG.debug(
          "Trying to process a file, however seems like it is already not present: {}",
          e.getMessage());
    } catch (Exception e) {
      LOG.error("Error while walking file tree", e);
    }
  }

  private void updateFsTreeAndAcceptConsumables(
      Map<Path, Long> items,
      Set<Consumer<Path>> updateConsumer,
      Set<Consumer<Path>> createConsumer,
      Path path,
      BasicFileAttributes attrs) {
//	 LOG.info("[updateFsTreeAndAcceptConsumables] updating " 
//			 + (items == directories ? "DIRs" : "FILEs") 
//			 + ", Path: " + path.toString());
    Long lastModifiedActual = attrs.lastModifiedTime().toMillis();

    if (items.containsKey(path)) {
      Long lastModifiedStored = items.get(path);
      if (!lastModifiedActual.equals(lastModifiedStored)) {
          LOG.info("\"[updateFsTreeAndAcceptConsumables] reporting to updateConsumer: size: {}, path: {}", updateConsumer.size(),path.toString());
        items.put(path, lastModifiedActual);
        updateConsumer.forEach(it -> {
            LOG.info("\"[updateFsTreeAndAcceptConsumables] reporting to updateConsumer: {}, path: {}", it.getClass().getName(),path.toString());
          it.accept(path);
        });
      }
    } else {
      items.put(path, lastModifiedActual);
      LOG.info("\"[updateFsTreeAndAcceptConsumables] reporting to updateConsumer: size:  {}, path: {}", createConsumer.size(),path.toString());
      createConsumer.forEach(it -> {
          LOG.info("\"[updateFsTreeAndAcceptConsumables] reporting to createConsumer: {}, path: {}", it.getClass().getName(),path.toString());
    	  it.accept(path);
      });
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
