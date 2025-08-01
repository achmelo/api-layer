/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.apicatalog;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.zowe.apiml.product.service.ServiceStartupEventHandler;

/**
 * This class fires on ApplicationReadyEvent event during Spring context initialization
 */
@Component
@RequiredArgsConstructor
public class AppReadyListener {

    private final ServiceStartupEventHandler handler;

    /**
     * Fires on ApplicationReadyEvent
     * triggers ServiceStartupEventHandler
     *
     * @param event Spring event
     */
    @EventListener
    public void onApplicationEvent(ApplicationReadyEvent event) {
        handler.onServiceStartup("API Catalog Service",
            ServiceStartupEventHandler.DEFAULT_DELAY_FACTOR);
    }

}
