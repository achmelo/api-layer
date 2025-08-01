/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

import tilesReducer from './tiles-reducer';
import {
    FETCH_TILES_FAILED,
    FETCH_TILES_REQUEST,
    FETCH_TILES_STOP,
    FETCH_TILES_SUCCESS,
    FETCH_TILES_RETRY,
    FETCH_NEW_SERVICE_REQUEST, FETCH_NEW_SERVICE_SUCCESS,
} from '../constants/catalog-tile-constants';

describe('>>> Tile reducer tests', () => {
    const sampleTile = {
        version: '1.0.0',
        id: 'apicatalog',
        title: 'API Mediation Layer API',
        status: 'UP',
        description: 'lkajsdlkjaldskj',
        services: [
            {
                serviceId: 'apicatalog',
                title: 'API Catalog',
                description:
                    'API ML Microservice to locate and display API documentation for API ML discovered microservices',
                status: 'UP',
                secured: false,
                homePageUrl: '/ui/v1/apicatalog',
            },
        ],
        totalServices: 1,
        activeServices: 1,
        lastUpdatedTimestamp: '2018-08-22T08:32:03.110+0000',
        createdTimestamp: '2018-08-22T08:31:22.948+0000',
    };

    it('should handle FETCH_TILES_REQUEST', () => {
        const expectedState = {
            id: '',
            tiles: [],
            error: null,
            services: undefined,
            tilesLoading: true
        };

        // NOTE: Updates might be redundant

        expect(
            tilesReducer(
                {
                    id: '',
                    tiles: [],
                    updates: [],
                    polling: false,
                },
                {
                    type: FETCH_TILES_REQUEST,
                    payload: '',
                }
            )
        ).toEqual(expectedState);
    });

    it('should handle FETCH_TILES_STOP', () => {
        const expectedState = {
            id: 'apicatalog',
            tiles: [sampleTile],
            updates: [],
        };

        expect(
            tilesReducer(
                {
                    id: 'apicatalog',
                    tiles: [sampleTile],
                    updates: [],
                },
                {
                    type: FETCH_TILES_STOP,
                }
            )
        ).toEqual(expectedState);
    });

    it('should handle FETCH_TILES_SUCCESS', () => {
        const expectedState = {
            id: '',
            tiles: [sampleTile],
            services: [sampleTile],
            error: null,
            tilesLoading: false,
        };

        expect(
            tilesReducer(
                {
                    id: '',
                    tiles: [sampleTile],
                    services: [sampleTile],
                    error: null,
                },
                {
                    type: FETCH_TILES_SUCCESS,
                    payload: [sampleTile],
                }
            )
        ).toEqual(expectedState);
    });

    it('should handle FETCH_TILES_RETRY', () => {
        const expectedState = {
            id: 'apicatalog',
            tiles: [sampleTile],
        };

        expect(
            tilesReducer(
                {
                    id: 'apicatalog',
                    tiles: [sampleTile],
                },
                {
                    type: FETCH_TILES_RETRY,
                }
            )
        ).toEqual(expectedState);
    });

    it('should handle FETCH_TILES_FAILED', () => {
        const expectedState = {
            tiles: [sampleTile],
            id: '',
            error: 'test',
        };

        // NOTE: Updates might be redundant

        expect(
            tilesReducer(
                {
                    id: 'apicatalog',
                    tiles: [sampleTile],
                    error: null,
                },
                {
                    type: FETCH_TILES_FAILED,
                    payload: 'test',
                }
            )
        ).toEqual(expectedState);
    });

    it('should handle FETCH_NEW_SERVICE_SUCCESS', () => {
        const expectedState = {
            id: '',
            service: sampleTile.services[0],
            error: null,
            serviceLoading: false
        };

        expect(
            tilesReducer(
                {
                    id: '',
                    error: null,
                },
                {
                    type: FETCH_NEW_SERVICE_SUCCESS,
                    payload: sampleTile.services[0],
                }
            )
        ).toEqual(expectedState);
    });

    it('should handle FETCH_NEW_SERVICE_REQUEST', () => {
        const expectedState = {
            tiles: [],
            id: '',
            error: null,
            services: [],
            serviceLoading: true,
        };

        expect(
            tilesReducer(
                {
                    id: '',
                    tiles: [],
                    error: null,
                    services: [],
                },
                {
                    type: FETCH_NEW_SERVICE_REQUEST,
                    payload: '',
                }
            )
        ).toEqual(expectedState);
    });

    it('should handle DEFAULT', () => {
        const expectedState = {
            id: 'apicatalog',
            tiles: [sampleTile],
            updates: [],
        };

        expect(
            tilesReducer(
                {
                    id: 'apicatalog',
                    tiles: [sampleTile],
                    updates: [],
                },
                {
                    type: 'UNKNOWN',
                }
            )
        ).toEqual(expectedState);
    });
});
