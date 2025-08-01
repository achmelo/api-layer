/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

import * as constants from '../constants/catalog-tile-constants';
import * as actions from './catalog-tile-actions';

describe('>>> Catalog tiles actions tests', () => {
    it('should create when fetching tiles has failed', () => {
        const expectedAction = {
            type: constants.FETCH_TILES_FAILED,
        };

        expect(actions.fetchTilesFailed()).toEqual(expectedAction);
    });

    it('should create when fetching tiles is successful', () => {
        const expectedAction = {
            type: constants.FETCH_TILES_SUCCESS,
            payload: [],
        };

        expect(actions.fetchTilesSuccess([])).toEqual(expectedAction);
    });

    it('should create when start fetching one tile', () => {
        const expectedAction = {
            type: constants.FETCH_TILES_REQUEST,
            payload: '',
        };

        expect(actions.fetchTilesStart('')).toEqual(expectedAction);
    });

    it('should create when stop fetching tiles', () => {
        const expectedAction = {
            type: constants.FETCH_TILES_STOP,
        };

        expect(actions.fetchTilesStop()).toEqual(expectedAction);
    });

    it('should create when retry fetching of tiles', () => {
        const expectedAction = {
            type: constants.FETCH_TILES_RETRY,
        };

        expect(actions.fetchTilesRetry()).toEqual(expectedAction);
    });

    it('should create when start fetching new tiles', () => {
        const expectedAction = {
            type: constants.FETCH_NEW_SERVICE_REQUEST,
            payload: '',
        };

        expect(actions.fetchNewService('')).toEqual(expectedAction);
    });

    it('should create when fetching new tiles is successful', () => {
        const expectedAction = {
            type: constants.FETCH_NEW_SERVICE_SUCCESS,
            payload: [],
        };

        expect(actions.fetchNewServiceSuccess([])).toEqual(expectedAction);
    });

});
