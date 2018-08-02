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
package org.eclipse.che.plugin.java.plain.server.rest;

import static org.eclipse.che.api.fs.server.WsPathUtils.absolutize;
import static org.eclipse.che.api.languageserver.LanguageServiceUtils.prefixURI;
import static org.eclipse.che.api.languageserver.LanguageServiceUtils.removePrefixUri;
import static org.eclipse.che.api.languageserver.util.JsonUtil.convertToJson;
import static org.eclipse.che.jdt.ls.extension.api.Commands.CLIENT_UPDATE_ON_PROJECT_CLASSPATH_CHANGED;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.project.server.ProjectManager;
import org.eclipse.che.api.project.server.impl.NewProjectConfigImpl;
import org.eclipse.che.api.project.shared.NewProjectConfig;
import org.eclipse.che.api.project.shared.RegisteredProject;
import org.eclipse.che.jdt.ls.extension.api.dto.ClasspathEntry;
import org.eclipse.che.plugin.java.languageserver.JavaLanguageServerExtensionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for updating classpath.
 *
 * @author Valeriy Svydenko
 */
@Path("jdt/classpath/update")
public class ClasspathUpdaterService {
  private static final Logger LOG = LoggerFactory.getLogger(ClasspathUpdaterService.class);

  private final ProjectManager projectManager;
  private JavaLanguageServerExtensionService extensionService;

  @Inject
  public ClasspathUpdaterService(
      ProjectManager projectManager, JavaLanguageServerExtensionService extensionService) {
    this.projectManager = projectManager;
    this.extensionService = extensionService;
  }

  /**
   * Updates the information about classpath.
   *
   * @param projectPath path to the current project
   * @param entries list of classpath entries which need to set
   * @throws ServerException if some server error
   * @throws ForbiddenException if operation is forbidden
   * @throws ConflictException if update operation causes conflicts
   * @throws NotFoundException if Project with specified path doesn't exist in workspace
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateClasspath(
      @QueryParam("projectpath") String projectPath, List<ClasspathEntry> entries)
      throws ServerException, ForbiddenException, ConflictException, NotFoundException, IOException,
          BadRequestException {
    LOG.info("[" + System.currentTimeMillis() + "] updateClasspath({}): start", projectPath);

    try {
      extensionService.updateClasspathWithResult(prefixURI(projectPath), entries).get();
    } catch (InterruptedException | ExecutionException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    LOG.info(
        "[" + System.currentTimeMillis() + "] updateClasspath({}): start: updating project config",
        projectPath);
    try {
      updateProjectConfig(projectPath).get();
    } catch (InterruptedException | ExecutionException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    LOG.info(
        "[" + System.currentTimeMillis() + "] updateClasspath({}): done: updating project config",
        projectPath);
    notifyClient(projectPath);

    LOG.info("[" + System.currentTimeMillis() + "] updateClasspath({}): done", projectPath);
  }

  private CompletableFuture<Object> updateProjectConfig(String projectWsPath)
      throws IOException, ForbiddenException, ConflictException, NotFoundException, ServerException,
          BadRequestException {
    LOG.info("[" + System.currentTimeMillis() + "] updateProjectConfig({}): start", projectWsPath);
    String wsPath = absolutize(projectWsPath);
    RegisteredProject project =
        projectManager
            .get(wsPath)
            .orElseThrow(() -> new NotFoundException("Can't find project: " + projectWsPath));

    NewProjectConfig projectConfig =
        new NewProjectConfigImpl(
            projectWsPath, project.getName(), project.getType(), project.getSource());
    RegisteredProject result = projectManager.update(projectConfig);
    LOG.info("[" + System.currentTimeMillis() + "] updateProjectConfig({}): done", projectWsPath);
    return CompletableFuture.completedFuture(result.getPath());
  }

  private void notifyClient(String projectPath) {
    List<Object> parameters = new ArrayList<>();
    parameters.add(removePrefixUri(convertToJson(projectPath).getAsString()));
    extensionService.executeClientCommand(CLIENT_UPDATE_ON_PROJECT_CLASSPATH_CHANGED, parameters);
  }
}
