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

import lombok.Getter;

public class SafAccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 7742132986283292513L;

    @Getter
    private final Object principal;

    public SafAccessDeniedException(String message, Object principal) {
        super(message);
        this.principal = principal;
    }

}
