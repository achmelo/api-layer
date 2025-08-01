/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.controller;

public class InvalidWebFingerConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 3570367176319366489L;

    public InvalidWebFingerConfigurationException(Throwable cause) {
        super(cause);
    }

}
