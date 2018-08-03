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

import static com.google.common.collect.Sets.newConcurrentHashSet;
import static java.nio.file.Files.exists;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.inject.Singleton;
import org.eclipse.che.api.fs.server.PathTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FileWatcherByPathMatcher implements Consumer<Path> {

  private static final Logger LOG = LoggerFactory.getLogger(FileWatcherByPathMatcher.class);

  private final AtomicInteger operationIdCounter = new AtomicInteger();

  private final FileWatcherByPathValue watcher;

  /** Operation ID -> Operation (create, modify, delete) */
  private final Map<Integer, Operation> operations = new ConcurrentHashMap<>();
  /** Operation ID -> Registered paths */
  private final Map<Integer, Set<Path>> paths = new ConcurrentHashMap<>();
  /** Matcher -> Operation IDs */
  private final Map<PathMatcher, Set<Integer>> matchers = new ConcurrentHashMap<>();
  /** Registered path -> Path watch operation IDs */
  private final Map<Path, Set<Integer>> pathWatchRegistrations = new ConcurrentHashMap<>();

  private PathTransformer pathTransformer;

  @Inject
  public FileWatcherByPathMatcher(FileWatcherByPathValue watcher, PathTransformer pathTransformer) {
    this.watcher = watcher;
    this.pathTransformer = pathTransformer;
  }

  @Override
  public void accept(Path path) {
    if (!exists(path)) {
    	LOG.info("[accept] Reported path doesn't exist: {}", path.toString());
      if (pathWatchRegistrations.containsKey(path)) {
        pathWatchRegistrations.remove(path).forEach(watcher::unwatch);
      }
      paths.values().forEach(it -> {
      	LOG.info("[accept] Removing path: {} from '{}' [{}]", path.toString(),
      			it, it.getClass().getName());
        it.remove(path);
      });
      paths.entrySet().removeIf(it -> it.getValue().isEmpty());
      return;
    }

    for (PathMatcher matcher : matchers.keySet()) {
    	LOG.info("[accept] start: Reporting path {} to matcher '{}' [{}]", path.toString(),
    			matcher, matcher.getClass().getName());
      if (matcher.matches(path)) {
        for (int operationId : matchers.get(matcher)) {
          paths.putIfAbsent(operationId, newConcurrentHashSet());
          if (paths.get(operationId).contains(path)) {
          	LOG.info("[accept] Skipping path {} that is already reported to matcher '{}' [{}]", path.toString(),
        			matcher, matcher.getClass().getName());
            return;
          }

          paths.get(operationId).add(path);

          Operation operation = operations.get(operationId);
        	LOG.info("[accept] Start watching new path {} with operation {} for matcher '{}' [{}]", path.toString(),
        			operationId, matcher, matcher.getClass().getName());
          int pathWatcherOperationId =
              watcher.watch(path, operation.create, operation.modify, operation.delete);
          pathWatchRegistrations.putIfAbsent(path, newConcurrentHashSet());
          pathWatchRegistrations.get(path).add(pathWatcherOperationId);
      	LOG.info("[accept] Reporting new path {} with operation {} ('{}' [{}]) for matcher '{}' [{}]", path.toString(),
    			operationId, operation.create, operation.create.getClass().getName(), matcher, matcher.getClass().getName());
          
          operation.create.accept(pathTransformer.transform(path));
        }
      } else {
    	LOG.info("[accept] Path {} doesn't match matcher '{}' [{}]", path.toString(),
    			matcher, matcher.getClass().getName());
    	
      }
  	  LOG.info("[accept] done: Reporting path to matchers: {}", path.toString());
    } 
  }

  int watch(
      PathMatcher matcher,
      Consumer<String> create,
      Consumer<String> modify,
      Consumer<String> delete) {
    LOG.debug("Watching matcher '{}'", matcher);
    logCallStack("Watching matcher '" + matcher + "' [" + matcher.getClass().getName() + "]");
    int operationId = operationIdCounter.getAndIncrement();

    matchers.putIfAbsent(matcher, newConcurrentHashSet());
    matchers.get(matcher).add(operationId);

    operations.put(operationId, new Operation(create, modify, delete));

    LOG.debug("Registered matcher operation set with id '{}'", operationId);
    LOG.info("Registered matcher operation set with id '{}'", operationId);
    return operationId;
  }

  void unwatch(int operationId) {
    LOG.debug("Unwatching matcher operation set with id '{}'", operationId);
    logCallStack("UnWatching matcher  operation set with id '" + operationId + "'");
    for (Entry<PathMatcher, Set<Integer>> entry : matchers.entrySet()) {
      PathMatcher matcher = entry.getKey();
      Set<Integer> operationsIdList = entry.getValue();
      Iterator<Integer> iterator = operationsIdList.iterator();
      while (iterator.hasNext()) {
        if (iterator.next() == operationId) {
          pathWatchRegistrations
              .keySet()
              .stream()
              .filter(matcher::matches)
              .flatMap(it -> pathWatchRegistrations.remove(it).stream())
              .forEach(watcher::unwatch);

          paths.values().forEach(it -> it.removeIf(matcher::matches));
          paths.entrySet().removeIf(it -> it.getValue().isEmpty());
          iterator.remove();
          operations.remove(operationId);

          break;
        }
      }

      if (matchers.get(matcher) == null || matchers.get(matcher).isEmpty()) {
        matchers.remove(matcher);
      }
    }
  }

  private static class Operation {

    final Consumer<String> create;
    final Consumer<String> modify;
    final Consumer<String> delete;

    private Operation(Consumer<String> create, Consumer<String> modify, Consumer<String> delete) {
      this.create = create;
      this.modify = modify;
      this.delete = delete;
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
