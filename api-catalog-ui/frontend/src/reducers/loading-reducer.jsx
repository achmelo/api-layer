/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

const loadingReducer = (state = {}, action = {}) => {
    const { type } = action;
    const matches = /^([^_]*)_(TILES_REQUEST|TILES_SUCCESS|FAILURE|FAILED|INVALIDPASSWORD|EXPIREDPASSWORD|TILES_NEW_SUCCESS|NEW_SERVICE_SUCCESS|NEW_SERVICE_REQUEST)$/.exec(type);

    // not a *_REQUEST / *_SUCCESS /  *_FAILURE actions, so we ignore them
    if (!matches) {
        return state;
    }

    // Store whether a request is happening at the moment or not a *REQUEST action will be true
    const [, requestName, requestState] = matches;
    const isRequest = requestState.includes('REQUEST');

    return {
        ...state,
        [requestName]: isRequest,
    };
};

export default loadingReducer;
