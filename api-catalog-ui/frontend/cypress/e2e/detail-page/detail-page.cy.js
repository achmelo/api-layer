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

describe('>>> Detail page test', () => {
    it('Detail page test', () => {
        cy.login(Cypress.env('username'), Cypress.env('password'));

        cy.get('#grid-container').contains('API Catalog').click();

        cy.url().should('contain', '/service/apicatalog');

        cy.get('#go-back-button').should('exist');

        cy.get('.api-description-container').should('exist');

        cy.contains(
            'The API Mediation Layer for z/OS internal API services. The API Mediation Layer provides a single point of access to mainframe REST APIs and offers enterprise cloud-like features such as high-availability, scalability, dynamic API discovery, and documentation.'
        );

        cy.contains('Version: ');
    });

    it('Should display the API Catalog service title, URL and description in Swagger', () => {
        cy.login(Cypress.env('username'), Cypress.env('password'));

        cy.contains('Version: ');
        cy.get('#grid-container').contains('API Catalog').click();

        cy.visit(`${Cypress.env('catalogHomePage')}/index.html#/service/apicatalog`);

        const baseUrl = `${Cypress.env('catalogHomePage')}`;

        cy.get(
            '#swaggerContainer > div > div:nth-child(2) > div.scheme-container > section > div:nth-child(1) > div > div > label > select > option'
        )
            .should('exist')
            .should('contain', `${baseUrl.match(/^https?:\/\/([^/?#]+)(?:[/?#]|$)/i)[1]}/apicatalog/api/v1`);

        cy.get('.tabs-container').should('not.exist');
        cy.get('.serviceTab').should('exist').and('contain', 'API Catalog');

        cy.contains('Service Homepage').should('exist');

        cy.get(
            '#root > div > div.content > div.main > div.main-content2.detail-content > div.content-description-container > div > div.tabs-swagger > div.serviceTab > div.header > a'
        )
            .should('have.attr', 'href')
            .should('contain', `${baseUrl.match(/^https?:\/\/([^/?#]+)(?:[/?#]|$)/i)[1]}/apicatalog/ui/v1`); // TODO This originally /ui/v1 but now /ui is selected for service homepage URL, see https://github.com/zowe/api-layer/issues/3652 to verify if it needs to be restored

        cy.get('pre.version').should('contain', '1.0.0');

        cy.contains('Swagger/OpenAPI JSON Document').should('exist');

        cy.get('.opblock-tag-section').should('have.length.gte', 1);
    });

    it('Should display the Gateway information in the detail page', () => {
        cy.login(Cypress.env('username'), Cypress.env('password'));

        cy.contains('Version: ');
        cy.contains('API Gateway').click();

        cy.visit(`${Cypress.env('catalogHomePage')}/index.html#/service/gateway`);

        const baseUrl = `${Cypress.env('catalogHomePage')}`;

        cy.get(
            '#swaggerContainer > div > div:nth-child(2) > div.scheme-container > section > div:nth-child(1) > div > div > label > select > option'
        )
            .should('exist')
            .should('contain', `${baseUrl.match(/^https?:\/\/([^/?#]+)(?:[/?#]|$)/i)[1]}/`);

        cy.get('.tabs-container').should('not.exist');
        cy.get('.serviceTab').should('exist').and('contain', 'API Gateway');

        cy.contains('Service Homepage').should('exist');

        cy.get('pre.version').should('contain', 'OAS');

        cy.contains('Swagger/OpenAPI JSON Document').should('exist');

        cy.get('.opblock-tag-section').should('have.length.gte', 1);

        cy.get(
            '#root > div > div.content > div.main > div.main-content2.detail-content > div.content-description-container > div > div.tabs-swagger > div.serviceTab > div.header > h6:nth-child(4)'
        )
            .should('exist')
            .should(
                'contain',
                'API Gateway service to route requests to services registered in the API Mediation Layer and provides an API for mainframe security.'
            );
    });

    it('Should go to the detail page, go back to the dashboard page and check if the search bar works', () => {
        cy.login(Cypress.env('username'), Cypress.env('password'));

        cy.contains('Version: ');
        cy.contains('API Gateway').click();

        cy.url().should('contain', '/service/gateway');

        cy.get('#go-back-button').should('exist').click();

        cy.get('#search > div > div > input').should('exist');
        cy.should('not.contain', 'Available API Services'); // Removed in last version

        cy.get('#search > div > div > input').as('search').type('API Gateway');

        let expectedGatewaysCount = 2;
        if (isModulith) {
            expectedGatewaysCount = 1;
        }

        cy.get('.grid-tile').should('have.length', expectedGatewaysCount).should('contain', 'API Gateway'); // FIXME in modulith multi tenancy is not working
    });
});
