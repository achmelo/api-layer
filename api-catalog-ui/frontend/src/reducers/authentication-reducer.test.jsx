/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

import userConstants from '../constants/user-constants';
import authenticationReducer from './authentication-reducer';

describe('>>> Authentication reducer tests', () => {

    let initialState = {
        sessionOn: false,
        user: null,
        error: null,
        showHeader: false,
        loginSuccess: false,
        authenticationFailed: false,
        showUpdatePassSuccess: false,
        expired: false,
        expiredWarning: false,
        matches: false,
    };
    it('should return default state in the default action', () => {
        expect(authenticationReducer()).toEqual(initialState);
    });

    it('should handle USERS_LOGIN_REQUEST', () => {
        const action = {
            type: userConstants.USERS_LOGIN_REQUEST,
            user: 'user',
        };
        expect(authenticationReducer({}, action)).toEqual({  authenticationFailed: false, user: 'user' });
    });

    it('should handle USERS_LOGIN_SUCCESS', () => {
        const action = {
            type: userConstants.USERS_LOGIN_SUCCESS,
            user: 'user',
        };

        expect(authenticationReducer({}, action)).toEqual({ authenticationFailed: false, error: null,loginSuccess: true, user: 'user', showHeader: true,showUpdatePassSuccess: undefined });
        expect(authenticationReducer().sessionOn).toEqual(true);

    });

    it('should handle USERS_LOGIN_FAILURE', () => {
        const action = {
            type: userConstants.USERS_LOGIN_FAILURE,
            error: 'error',
        };
        expect(authenticationReducer({}, action)).toEqual({ error: 'error' });
        expect(authenticationReducer().sessionOn).toEqual(true);
    });

    it('should handle AUTHENTICATION_FAILURE', () => {
        const action = {
            type: userConstants.AUTHENTICATION_FAILURE,
            error: 'error',
        };

        const initialState = {
            sessionOn: true,
            user: null,
            error: null,
            showHeader: false,
            loginSuccess: false,
            authenticationFailed: false,
            showUpdatePassSuccess: false,
            expired: false,
            expiredWarning: false,
            matches: false,
        };
        const result = authenticationReducer({}, action);
        expect(result.error).toEqual('error');
        expect(result.sessionOn).toEqual(true);
        expect(authenticationReducer()).toEqual(initialState);
        result.onCompleteHandling();
        expect(authenticationReducer().sessionOn).toEqual(false );
    });

    it('should handle USERS_LOGOUT_REQUEST', () => {
        // Login again to recover from previous test case
        authenticationReducer({}, { type: userConstants.USERS_LOGIN_SUCCESS });

        const result = authenticationReducer(undefined, { type: userConstants.USERS_LOGOUT_REQUEST });
        expect(result.error).toEqual(null);
        expect(result.showHeader).toEqual(false);
        expect(authenticationReducer().sessionOn).toEqual(true);
    });

    it('should handle USERS_LOGOUT_SUCCESS', () => {
        // Login again to recover from previous test case
        authenticationReducer({}, { type: userConstants.USERS_LOGIN_SUCCESS });

        const initialState = {
            sessionOn: true,
            user: null,
            error: null,
            showHeader: false,
            loginSuccess: false,
            authenticationFailed: false,
            showUpdatePassSuccess: false,
            expired: false,
            expiredWarning: false,
            matches: false,
        };
        const result = authenticationReducer({}, { type: userConstants.USERS_LOGOUT_SUCCESS });
        expect(result.error).toEqual(null);
        expect(result.showHeader).toEqual(false);
        expect(authenticationReducer()).toEqual(initialState);
        result.onCompleteHandling();
        expect(authenticationReducer().sessionOn).toEqual(false);
    });

    it('should handle USERS_LOGOUT_FAILURE', () => {
        const action = {
            type: userConstants.USERS_LOGOUT_FAILURE,
            error: 'error',
        };
        const initialState = {
            sessionOn: false,
            user: null,
            error: null,
            showHeader: false,
            loginSuccess: false,
            authenticationFailed: false,
            showUpdatePassSuccess: false,
            expired: false,
            expiredWarning: false,
            matches: false,
        };
        expect(authenticationReducer({}, action)).toEqual({ error: 'error', showHeader: false });
        expect(authenticationReducer()).toEqual(initialState);
        expect(authenticationReducer().sessionOn).toEqual(false);
    });

    it('should handle USERS_LOGIN_INVALIDPASSWORD', () => {
        const action = {
            type: userConstants.USERS_LOGIN_INVALIDPASSWORD,
            error: 'error',
        };
        expect(authenticationReducer({}, action)).toEqual({ error: 'error', expiredWarning: false });
    });

    it('should validate new password', () => {
        const action = {
            type: userConstants.USERS_LOGIN_VALIDATE,
            credentials: {
                newPassword: 'newPass',
                repeatNewPassword: 'typo',
            },
        };
        expect(authenticationReducer({}, action)).toEqual({
            matches: false,
        });
    });

    it('should return empty error object if re-entered new password matches', () => {
        const action = {
            type: userConstants.USERS_LOGIN_VALIDATE,
            credentials: {
                newPassword: 'newPass',
                repeatNewPassword: 'newPass',
            },
        };
        expect(authenticationReducer({}, action)).toEqual({
            matches: true,
        });
    });

    it('should init with empty state', () => {
        const action = {
            type: userConstants.USERS_LOGIN_INIT,
        };
        expect(authenticationReducer({}, action)).toEqual({});
    });
});
