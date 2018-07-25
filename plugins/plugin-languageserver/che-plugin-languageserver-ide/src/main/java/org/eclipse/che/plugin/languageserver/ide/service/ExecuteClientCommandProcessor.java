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
package org.eclipse.che.plugin.languageserver.ide.service;

import static org.eclipse.che.jdt.ls.extension.api.Commands.CLIENT_UPDATE_ON_PROJECT_CLASSPATH_CHANGED;
import static org.eclipse.che.jdt.ls.extension.api.Commands.CLIENT_UPDATE_PROJECT;
import static org.eclipse.che.jdt.ls.extension.api.Commands.CLIENT_UPDATE_PROJECTS_CLASSPATH;

import com.google.common.base.Optional;
import com.google.gwt.json.client.JSONString;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import org.eclipse.che.api.promises.client.Function;
import org.eclipse.che.api.promises.client.FunctionException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseProvider;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.resources.Container;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.project.node.ProjectClasspathChangedEvent;
import org.eclipse.lsp4j.ExecuteCommandParams;

/**
 * A processor for incoming <code>workspace/ClasspathChanged</code> notifications sent by a language
 * server.
 *
 * @author V. Rubezhny
 */
@Singleton
public class ExecuteClientCommandProcessor {
  private EventBus eventBus;
  private AppContext appContext;
  private final PromiseProvider promises;

  @Inject
  public ExecuteClientCommandProcessor(
      EventBus eventBus, AppContext appContext, PromiseProvider promises) {
    this.eventBus = eventBus;
    this.appContext = appContext;
    this.promises = promises;
  }

  public void execute(ExecuteCommandParams params) {
    switch (params.getCommand()) {
      case CLIENT_UPDATE_PROJECTS_CLASSPATH:
        for (Object project : params.getArguments()) {
          eventBus.fireEvent(new ProjectClasspathChangedEvent(stringValue(project)));
        }
        break;
      case CLIENT_UPDATE_PROJECT:
        updateProject(stringValue(params.getArguments()));
        break;
      case CLIENT_UPDATE_ON_PROJECT_CLASSPATH_CHANGED:
          for (Object project : params.getArguments()) {
	         updateProject(stringValue(project))
	            .then(
	                container -> {
	                    log(
	                            "["
	                                + System.currentTimeMillis()
	                                + "] ExecuteClientCommandProcessor.execute("
	                                + CLIENT_UPDATE_ON_PROJECT_CLASSPATH_CHANGED
	                                + "): start: reporting events");             	
	                    eventBus.fireEvent(
	                        new ProjectClasspathChangedEvent(
	                            stringValue(container.getLocation().toString())));
	                    log(
	                            "["
	                                + System.currentTimeMillis()
	                                + "] ExecuteClientCommandProcessor.execute("
	                                + CLIENT_UPDATE_ON_PROJECT_CLASSPATH_CHANGED
	                                + "): reported event:" + container.getLocation().toString());

	                  log(
	                          "["
	                              + System.currentTimeMillis()
	                              + "] ExecuteClientCommandProcessor.execute("
	                              + CLIENT_UPDATE_ON_PROJECT_CLASSPATH_CHANGED
	                              + "): done: reporting events");
	                });
          }

        break;
      default:
        break;
    }
  }

  private Promise<Container> updateProject(String project) {
    log(
        "["
            + System.currentTimeMillis()
            + "] ExecuteClientCommandProcessor.updateProject("
            + project
            + "): start");
   	   
   	    Promise<Container> result =
   	    		appContext
   	            .getWorkspaceRoot()
   	            .getContainer(project)
   	            .thenPromise(
   	            		optContainer -> {
                      if (optContainer.isPresent()) {
                    	  Container container = optContainer.get();
                      log(
                          "["
                              + System.currentTimeMillis()
                              + "] ExecuteClientCommandProcessor.updateProject(): updating project: "
                              + optContainer.get().getName());
                      container.synchronize()
                      .then(resources -> {
                    	  for(Resource r : resources) {
                              log(
                                      "["
                                          + System.currentTimeMillis()
                                          + "] ExecuteClientCommandProcessor.updateProject(): affected resource: "
                                          + r.getLocation().toString());
             
                    	  }
                      });
                      
                      log(
                          "["
                              + System.currentTimeMillis()
                              + "] ExecuteClientCommandProcessor.updateProject(): updated project: "
                              + container.getName());
                      return promises.resolve(container);
                    }
                      return promises.resolve(null);
                  }
                );
    log(
        "["
            + System.currentTimeMillis()
            + "] ExecuteClientCommandProcessor.updateProject("
            + project
            + "): done");
    return result;
  }


  public static native void log(String message) /*-{
  if (window.console && console.log) console.log(message);
}-*/;

  private String stringValue(Object value) {
    return value instanceof JSONString ? ((JSONString) value).stringValue() : String.valueOf(value);
  }
}
