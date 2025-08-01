/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
/* eslint-disable no-undef */
/// <reference types="Cypress" />

const isModulith = Cypress.env('modulith');

describe('>>> Dashboard test', () => {
    it('dashboard test', () => {
        cy.login(Cypress.env('username'), Cypress.env('password'));

        cy.contains('Version: ');

        cy.get('.header').should('exist');

        cy.url().should('contain', '/dashboard');
        cy.get('.grid-tile').should('have.length.gte', 1);

        cy.get('.grid-tile-status').should('have.length.gte', 1);

        cy.contains('The service is running').should('exist');

        cy.get('.header').should('exist');

        cy.get('#search > div > div > input').should('exist');
        cy.get('#refresh-api-button').should('exist').click();
        cy.get('.Toastify').should('have.length.gte', 1);
        cy.get('.Toastify > div> div')
            .should('have.length', 1)
            .should('contain', 'The refresh of static APIs was successful!');

        cy.get('#search > div > div > input')
            .as('search')
            .type('Oh freddled gruntbuggly, Thy micturations are to me, (with big yawning)');

        cy.get('.grid-tile').should('have.length', 0);

        cy.get('#search_no_results').should('exist').should('have.text', 'No services found matching search criteria');

        cy.get('@search').clear();

        cy.get('#search > div > div > input').as('search').type('API Gateway');

        let expectedGatewaysCount = 2;
        if (isModulith) {
            expectedGatewaysCount = 1;
        }

        cy.get('.grid-tile').should('have.length', expectedGatewaysCount); // FIXME modulith does not support multitenancy yet

        cy.get('.clear-text-search').click();

        cy.get('.grid-tile').should('have.length.gte', 2);
        cy.get('@search').should('have.text', '');

        cy.contains('API Catalog').click();

        cy.get('#root > div > div.content > div.header > div.right-icons > div').should('exist').click();
        cy.get('#user-info-text').should('have.length', 1);
        cy.get('#logout-button').should('have.length', 1).should('contain', 'Log out');
    });

    it('should keep session persistent by navigating to dashboard if valid token is provided', () => {
        const requestBody = {
            username: Cypress.env('username'),
            password: Cypress.env('password'),
        };

        cy.request({
            method: 'POST',
            url: `${Cypress.env('loginUrl')}`,
            body: requestBody,
        }).then((resp) => {
            expect(resp.status).to.eq(204);
            expect(resp.headers).to.have.property('set-cookie');

            const rawCookie = resp.headers['set-cookie'].find((cookie) =>
                cookie.startsWith('apimlAuthenticationToken=')
            );
            // eslint-disable-next-line no-unused-expressions
            expect(rawCookie).to.not.be.empty;

            const cookieValue = rawCookie.split(';')[0].split('=')[1];

            // Set the cookie in the Cypress browser
            cy.setCookie('apimlAuthenticationToken', cookieValue);

            cy.visit(`${Cypress.env('catalogHomePage')}/index.html#/dashboard`);

            cy.get('.header').should('exist');
            cy.url().should('contain', '/dashboard');
            cy.contains('The service is running').should('exist');
        });
    });
});
