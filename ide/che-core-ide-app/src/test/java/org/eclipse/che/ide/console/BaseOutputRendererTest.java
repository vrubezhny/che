/*******************************************************************************
 * Copyright (c) 2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.console;

import java.util.stream.Stream;

import org.eclipse.che.ide.api.outputconsole.OutputConsoleRenderer;
import org.junit.Assert;

/**
 * Base class for JUnit tests of stacktrace line detection.
 * 
 * @author Victor Rubezhny
 */
public class BaseOutputRendererTest {

    private OutputConsoleRenderer outputRenderer;
    private OutputConsoleRenderer[] renderers;

    /**
     * Sets the renderer to be tested and array of all the renderers.
     * Normally is to be called from setUp() method, before any test case run.
     *  
     * @param renderer 
     * @param renderers 
     */
    protected void setupTestRenderers(OutputConsoleRenderer renderer, OutputConsoleRenderer... renderers) {
        Assert.assertNotNull("At least one test renderer is to be specified", renderer);
        Assert.assertNotNull("At least one renderer is to be specified", renderers);
        Assert.assertTrue("At least one renderer is to be specified", renderers.length > 0);

        this.outputRenderer = renderer;
        this.renderers = renderers;
    }

    /**
     * Tests the lines that cannot be treated as stacktrace
     * lines and, as such, shouldn't be rendered
     * 
     * @param line
     * @throws Exception
     */
    protected void testStackTraceLine(String line) throws Exception {
        testStackTraceLine(null, line, null);
    }

    /**
     * Tests customizable and non-customizable lines
     * 
     * @param expectedRendererClass
     * @param line
     * @param expectedCustomization
     * @throws Exception
     */
    protected void testStackTraceLine(Class expectedRendererClass, String line, String expectedCustomization)
            throws Exception {
        boolean shouldBeCustomizable = expectedRendererClass != null;
        Assert.assertEquals(
                "Line [" + line + "] is " + (shouldBeCustomizable ? "" : "not ") + "customizable while it should"
                        + (shouldBeCustomizable ? "n\'t " : " ") + "be: ",
                Boolean.valueOf(shouldBeCustomizable), Boolean.valueOf(outputRenderer.canRender(line)));
        if (shouldBeCustomizable) {
            testRendererValidity(expectedRendererClass, renderers, line);
            Assert.assertEquals("Wrong customization result:", expectedCustomization, outputRenderer.render(line));
        }
    }

    /*
     * Tests that a line can be processed only by expected renderer and not by the
     * other ones
     */
    private void testRendererValidity(Class expectedRendererClass, OutputConsoleRenderer[] allRenderers, String text) {
        Assert.assertTrue(
                "Expected renderer of type [" + expectedRendererClass + "] cannot render line [" + text + "]",
                Stream.of(allRenderers).filter(child -> child.getClass().equals(expectedRendererClass))
                        .allMatch(expected -> expected.canRender(text)));

        Assert.assertTrue(
                "Unexpected renderer of type [" + expectedRendererClass + "] can render line [" + text + "]",
                Stream.of(allRenderers).filter(child -> !child.getClass().equals(expectedRendererClass))
                        .noneMatch(expected -> expected.canRender(text)));
    }
}
