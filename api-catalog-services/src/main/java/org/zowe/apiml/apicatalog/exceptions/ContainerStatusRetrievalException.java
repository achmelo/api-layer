/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.apicatalog.exceptions;

public class ContainerStatusRetrievalException extends Exception {

    private static final long serialVersionUID = 2060505088907324466L;

    public ContainerStatusRetrievalException(Throwable e) {
        super(e);
    }

}
