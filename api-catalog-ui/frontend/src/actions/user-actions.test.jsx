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
import { userActions } from './user-actions';
import { userService } from '../services';

describe('>>> User actions tests', () => {
    const credentials = { username: 'user', password: 'password' };
    const mockPostMessage = jest.fn();
    const mockClose = jest.fn();

    global.BroadcastChannel = class {
        constructor() {
            this.postMessage = mockPostMessage;
            this.close = mockClose;
        }
    };

    it('should close alert', () => {
        const expectedAction = {
            type: userConstants.USERS_CLOSE_ALERT,
        };

        const response = userActions.closeAlert();
        expect(response).toEqual(expectedAction);
    });

    it('should validate input', () => {
        const expectedAction = {
            credentials: {
                password: 'password',
                username: 'user',
            },
            type: userConstants.USERS_LOGIN_VALIDATE,
        };

        const response = userActions.validateInput(credentials);
        expect(response).toEqual(expectedAction);
    });

    it('should login', async () => {
        const dispatch = jest.fn();
        const expectedAction = { type: 'USERS_LOGIN_REQUEST', user: { password: 'password', username: 'user' } };

        await userActions.login(credentials)(dispatch);
        expect(dispatch.mock.calls[0][0]).toStrictEqual(expectedAction);
    });

    it('should use normal routing', async () => {
        const dispatch = jest.fn();
        const expectedAction = { type: 'USERS_LOGIN_REQUEST', user: { password: 'password', username: 'user' } };

        userService.login = jest.fn().mockResolvedValue('token');


        await userActions.login(credentials)(dispatch);

        expect(dispatch.mock.calls[0][0]).toStrictEqual(expectedAction);

    });

    it('should logout', async () => {
        const dispatch = jest.fn();
        const expectedAction = { type: 'USERS_LOGOUT_REQUEST' };

        await userActions.logout(credentials)(dispatch);
        expect(dispatch.mock.calls[0][0]).toStrictEqual(expectedAction);
    });

    it('should dispatch authentication failure', async () => {
        const dispatch = jest.fn();
        const error = {
            xhr: {
                getResponseHeader: jest.fn(),
            },
        };
        await userActions.authenticationFailure(error)(dispatch);
        expect(dispatch.mock.calls[0][0].type).toBe('AUTHENTICATION_FAILURE');
    });

    it('should return to login', async () => {
        const dispatch = jest.fn();
        await userActions.returnToLogin()(dispatch);
        expect(dispatch.mock.calls[0][0].type).toBe('USERS_LOGIN_INIT');
    });

    it('should validate token', async () => {
        const action = userActions.query('user');
        console.log(action);
        expect(action.type).toBe('USERS_LOGIN_SUCCESS');
        expect(action.user).toBe('user');
    });

    it('should dispatch USERS_LOGOUT_SUCCESS in forceLogout', () => {
        const dispatch = jest.fn();

        userActions.forceLogout()(dispatch);

        expect(dispatch).toHaveBeenCalledWith({
            type: userConstants.USERS_LOGOUT_SUCCESS
        });
    });

    it('should dispatch logout success and broadcast logout on success', async () => {
        const dispatch = jest.fn();

        userService.logout = jest.fn().mockResolvedValue();

        await userActions.logout()(dispatch);

        expect(dispatch).toHaveBeenCalledWith({ type: userConstants.USERS_LOGOUT_REQUEST });
        expect(dispatch).toHaveBeenCalledWith({ type: userConstants.USERS_LOGOUT_SUCCESS });

        const channelCall = new BroadcastChannel('auth_channel');
        expect(channelCall.postMessage).toHaveBeenCalledWith('logout');
        expect(channelCall.close).toHaveBeenCalled();
    });

    it('should dispatch logout failure on error', async () => {
        const dispatch = jest.fn();
        const error = new Error('Logout failed');

        userService.logout = jest.fn().mockRejectedValue(error);

        await userActions.logout()(dispatch);

        expect(dispatch).toHaveBeenCalledWith({ type: userConstants.USERS_LOGOUT_REQUEST });
        expect(dispatch).toHaveBeenCalledWith({ type: userConstants.USERS_LOGOUT_FAILURE, error });
    });
});
