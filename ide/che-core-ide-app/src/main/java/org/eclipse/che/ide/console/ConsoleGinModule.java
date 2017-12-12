/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.console;

import org.eclipse.che.ide.api.outputconsole.OutputConsole;
import org.eclipse.che.ide.api.outputconsole.OutputConsoleRendererRegistry;

import com.google.gwt.inject.client.AbstractGinModule;
import com.google.gwt.inject.client.assistedinject.GinFactoryModuleBuilder;
import com.google.inject.Singleton;
import com.google.inject.name.Names;

/**
 * GIN module for configuring command consoles.
 */
public class ConsoleGinModule extends AbstractGinModule {
    @Override
    protected void configure() {
        bind(OutputConsoleView.class).to(OutputConsoleViewImpl.class);
        install(new GinFactoryModuleBuilder()
                        .implement(CommandOutputConsole.class, Names.named("command"), CommandOutputConsolePresenter.class)
                        .implement(OutputConsole.class, Names.named("default"), DefaultOutputConsole.class)
                        .build(CommandConsoleFactory.class));
        
        bind(OutputConsoleRendererRegistry.class).to(OutputConsoleRendererRegistryImpl.class).in(Singleton.class);
    }
}
