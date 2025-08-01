/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

import {useEffect} from 'react';
import {Tab, Tabs, Tooltip, Typography, withStyles} from '@material-ui/core';
import {Link as RouterLink, useLocation} from 'react-router';
import Shield from '../ErrorBoundary/Shield/Shield';
import SearchCriteria from '../Search/SearchCriteria';
import {sortServices} from '../../selectors/selectors';

function ServicesNavigationBar({services, searchCriteria, clear, filterText, fetchNewService}) {


    useEffect(() => {
        return function cleanup() {
            clear();
        };
    }, []); // Dependencies

    const handleTabClick = (id) => {
        fetchNewService(id)
    };


    const styles = () => ({
        truncatedTabLabel: {
            maxWidth: '100%',
            width: 'max-content',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
        },
    });

    const hasTiles = services && services.length > 0;
    const hasSearchCriteria = searchCriteria !== undefined && searchCriteria !== null && searchCriteria.length > 0;
    const url = window.location.href;
    let location = useLocation();
    const parts = url.split('/');

    const serviceId = parts[parts.length - 1];
    let servicesUrl = url.split('/');
    servicesUrl.pop();
    const basePath = location.pathname.replace(serviceId, '');
    let selectedTab = Number(0);
    let allServices;
    if (hasTiles) {
        allServices = sortServices(services);
        const index = allServices.findIndex((item) => item.serviceId === serviceId);
        selectedTab = Number(index);
    }

    const TruncatedTabLabel = withStyles(styles)(({classes, label}) => (
        <Tooltip title={label} placement="bottom">
            <div className={classes.truncatedTabLabel}>{label}</div>
        </Tooltip>
    ));
    return (
        <div>
            <div id="search2">
                <Shield title="Search Bar is broken !">
                    <SearchCriteria data-testid="search-bar" placeholder="Search..." doSearch={filterText}/>
                </Shield>
            </div>
            <Typography id="serviceIdTabs" variant="h5">
                Product APIs
            </Typography>
            {!hasTiles && hasSearchCriteria && (
                <Typography id="search_no_results" variant="subtitle2" className="no-content">
                    No services found matching search criteria
                </Typography>
            )}
            {hasTiles && (
                <Tabs
                    value={selectedTab}
                    variant="scrollable"
                    orientation="vertical"
                    scrollButtons="auto"
                    className="custom-tabs"
                >
                    {allServices.map((service, serviceIndex) => (
                        <Tab
                            onClick={() => handleTabClick(service.serviceId)}
                            key={service.serviceId}
                            className="tabs"
                            component={RouterLink}

                            to={`${basePath}${service.serviceId}`}
                            value={serviceIndex}
                            label={<TruncatedTabLabel label={service.title}/>}
                            wrapped
                        />
                    ))}
                </Tabs>
            )}
        </div>
    );

}

export default ServicesNavigationBar;
