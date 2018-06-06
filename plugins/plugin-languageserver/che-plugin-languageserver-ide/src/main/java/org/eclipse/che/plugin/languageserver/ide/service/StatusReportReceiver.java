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

import static org.eclipse.che.ide.api.jsonrpc.Constants.WS_AGENT_JSON_RPC_ENDPOINT_ID;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.che.api.core.jsonrpc.commons.RequestHandlerConfigurator;
import org.eclipse.che.api.core.jsonrpc.commons.RequestTransmitter;
import org.eclipse.che.api.languageserver.shared.model.StatusReportParams;

/**
 * Subscribes and receives JSON-RPC messages related to ClassPath Notification
 * 'workspace/executeClientCommand' events
 */
@Singleton
public class StatusReportReceiver {
  public static final String STATUS_REPORT = "language/status";
  public static final String STATUS_REPORT_SUBSCRIBE = "language/status/subscribe";
  private final RequestTransmitter transmitter;

  @Inject
  public StatusReportReceiver(RequestTransmitter transmitter) {
    this.transmitter = transmitter;
  }

  public void subscribe() {
    subscribe(transmitter);
  }

  @Inject
  private void configureReceiver(
      Provider<StatusReportProcessor> provider, RequestHandlerConfigurator configurator) {
    configurator
        .newConfiguration()
        .methodName(STATUS_REPORT)
        .paramsAsDto(StatusReportParams.class)
        .noResult()
        .withConsumer(params -> provider.get().onStatusReportReceived(params));
  }

  private void subscribe(RequestTransmitter transmitter) {
    transmitter
        .newRequest()
        .endpointId(WS_AGENT_JSON_RPC_ENDPOINT_ID)
        .methodName(STATUS_REPORT_SUBSCRIBE)
        .noParams()
        .sendAndSkipResult();
  }
}
