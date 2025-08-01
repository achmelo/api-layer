/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.util.categories;
import org.junit.jupiter.api.Tag;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

/**
 * Test related to the API Rate limiting.
 */
@Tag("RateLimitTest")
@Target({ TYPE, METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimitTest {
}
