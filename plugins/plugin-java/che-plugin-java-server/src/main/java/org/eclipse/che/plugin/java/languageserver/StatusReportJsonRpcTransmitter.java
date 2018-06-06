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

import static org.eclipse.che.ide.ext.java.shared.Constants.STATUS_REPORT;
import static org.eclipse.che.ide.ext.java.shared.Constants.STATUS_REPORT_SUBSCRIBE;
import static org.eclipse.che.ide.ext.java.shared.Constants.STATUS_REPORT_UNSUBSCRIBE;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.jsonrpc.commons.RequestHandlerConfigurator;
import org.eclipse.che.api.core.jsonrpc.commons.RequestTransmitter;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.languageserver.server.dto.DtoServerImpls;
import org.eclipse.che.api.languageserver.server.dto.DtoServerImpls.StatusReportParamsDto;
import org.eclipse.che.api.languageserver.shared.model.StatusReportParams;

/** Transmits 'workspace/executeClientCommand' over the JSON-RPC */
@Singleton
public class StatusReportJsonRpcTransmitter {
  private final Set<String> endpointIds = new CopyOnWriteArraySet<>();

  private final RequestTransmitter requestTransmitter;

  @Inject
  public StatusReportJsonRpcTransmitter(RequestTransmitter requestTransmitter) {
    this.requestTransmitter = requestTransmitter;
  }

  @Inject
  private void subscribe(EventService eventService, RequestTransmitter requestTransmitter) {
    eventService.subscribe(
        event ->
            endpointIds.forEach(
                endpointId ->
                    requestTransmitter
                        .newRequest()
                        .endpointId(endpointId)
                        .methodName(STATUS_REPORT)
                        .paramsAsDto(new StatusReportParamsDto(event))
                        .sendAndSkipResult()),
        StatusReportParams.class);
  }

  @Inject
  private void configureSubscribeHandler(RequestHandlerConfigurator requestHandler) {
    requestHandler
        .newConfiguration()
        .methodName(STATUS_REPORT_SUBSCRIBE)
        .noParams()
        .noResult()
        .withConsumer(endpointIds::add);
  }

  @Inject
  private void configureUnSubscribeHandler(RequestHandlerConfigurator requestHandler) {
    requestHandler
        .newConfiguration()
        .methodName(STATUS_REPORT_UNSUBSCRIBE)
        .noParams()
        .noResult()
        .withConsumer(endpointIds::remove);
  }

  public void sendStatusReport(StatusReportParams requestParams) {
    StatusReportParamsDto paramsDto = (StatusReportParamsDto) DtoServerImpls.makeDto(requestParams);

    for (String endpointId : endpointIds) {
      requestTransmitter
          .newRequest()
          .endpointId(endpointId)
          .methodName(STATUS_REPORT)
          .paramsAsDto(paramsDto)
          .sendAndSkipResult();
    }
  }
}
