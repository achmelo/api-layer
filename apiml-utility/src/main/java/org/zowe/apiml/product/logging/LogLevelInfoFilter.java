/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.product.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/**
 * This filter's purpose is to hide or show the info level messages (and below log levels like debug etc.)
 * There are info level messages that are meant for debug mode only (originally @Slf4j)
 * There are info level messages that are meant to be displayed (like service startup messages)
 * <p>
 * Because ApimlLogger is using Slf4j in the background, there is conflict of interest.
 * Solution is that ApimlLogger is enhancing its logs with Marker instances and this filter is providing
 * adequate filtering.
 */
public class LogLevelInfoFilter extends TurboFilter {

    private static final String APIML_MARKER = "APIML-LOGGER";

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (isLevelInfoOrLower(level) && isInternalLogger(logger) &&
            (marker == null || !marker.getName().equals(APIML_MARKER))) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }

    private boolean isLevelInfoOrLower(Level messageLevel) {
        return messageLevel.levelInt <= Level.INFO.levelInt;
    }

    private boolean isInternalLogger(Logger logger) {
        return logger.getName().startsWith("org.zowe.apiml");
    }
}
