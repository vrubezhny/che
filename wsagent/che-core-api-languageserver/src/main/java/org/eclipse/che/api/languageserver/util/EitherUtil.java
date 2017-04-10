/*******************************************************************************
 * Copyright (c) 2017 Red Hat.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat - Initial Contribution
 *******************************************************************************/
package org.eclipse.che.api.languageserver.util;

import org.eclipse.che.api.languageserver.shared.util.JsonDecision;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Code to be called by generated DTO classes to determine whether a json
 * element matches the kind of expected type in an {@link Either} field.
 * 
 * @author Thomas Mäder
 *
 */
public class EitherUtil {
	public static boolean matches(JsonElement element, JsonDecision[] decisions) {
		for (JsonDecision cls : decisions) {
			if (matches(element, cls)) {
				return true;
			}
		}
		return false;
	}

	private static boolean matches(JsonElement element, JsonDecision decision) {
		if (decision == JsonDecision.LIST) {
			return element.isJsonArray();
		}
		if (decision == JsonDecision.BOOLEAN) {
			return element.isJsonPrimitive() && ((JsonPrimitive) element).isBoolean();
		}
		if (decision == JsonDecision.NUMBER) {
			return element.isJsonPrimitive() && ((JsonPrimitive) element).isNumber();
		}
		if (decision == JsonDecision.STRING) {
			return element.isJsonPrimitive() && ((JsonPrimitive) element).isString();
		}
		return element.isJsonObject();
	}
}
