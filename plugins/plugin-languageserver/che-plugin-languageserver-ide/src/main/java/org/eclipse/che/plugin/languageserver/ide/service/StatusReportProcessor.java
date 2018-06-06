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

import com.google.gwt.json.client.JSONString;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import org.eclipse.che.api.languageserver.shared.model.StatusReportParams;
import org.eclipse.che.ide.util.loging.Log;

/**
 * A processor for incoming <code>workspace/ClasspathChanged</code> notifications sent by a language
 * server.
 *
 * @author V. Rubezhny
 */
@Singleton
public class StatusReportProcessor {
  private static final String CLIENT_UPDATE_PROJECTS_CLASSPATH =
      "che.jdt.ls.extension.workspace.clientUpdateProjectsClasspath";

  private EventBus eventBus;

  @Inject
  public StatusReportProcessor(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  public void onStatusReportReceived(StatusReportParams params) {
    Log.info(
        getClass(), "Received a 'Status Report': " + params.getType() + ", " + params.getMessage());
    /*
        if (CLIENT_UPDATE_PROJECTS_CLASSPATH.equals(params.getCommand())) {
          for (Object project : params.getArguments()) {
            eventBus.fireEvent(new ProjectClasspathChangedEvent(stringValue(project)));
          }
        }
    */
  }

  private String stringValue(Object value) {
    return value instanceof JSONString ? ((JSONString) value).stringValue() : String.valueOf(value);
  }
}
