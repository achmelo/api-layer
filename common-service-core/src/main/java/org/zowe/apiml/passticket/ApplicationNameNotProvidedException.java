/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.passticket;

/**
 * Exception thrown when applicationName parameter was not provided
 */
public class ApplicationNameNotProvidedException extends PassTicketException {

    public ApplicationNameNotProvidedException() { super("ApplicationName not provided"); }

    public ApplicationNameNotProvidedException(String message) {
        super(message);
    }
}
