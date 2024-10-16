module.exports = {
    globalExtends: ["config:recommended"], // using this instead of "extends" solves the problem with order of the configuration
    repositories: ['achmelo/api-layer'],
    baseBranches: ["master"],
    dependencyDashboard: true,
    packageRules: [
        {
            //for v3.x.x branch find all packages which are minor and patches,
            // slug them and make PR with name "all non-major dependencies"
            "matchBaseBranches": ["master"],
            "groupName": "all non-major dependencies",
            "groupSlug": "all-minor-patch",
            "matchPackageNames": ["*"],
            "matchUpdateTypes": ["minor", "patch"]
        }
    ],
    printConfig: true,
    labels: ['dependencies'],
    dependencyDashboardLabels: ['dependencies'],
    ignoreDeps: ['history','jsdom','react-router-dom','@mui/icons-material','@mui/material','@material-ui/core','@material-ui/icons'],
    commitMessagePrefix: 'chore: ',
    prHourlyLimit: 0, // removes rate limit for PR creation per hour
    npmrc: 'legacy-peer-deps=true\nregistry=https://zowe.jfrog.io/artifactory/api/npm/npm-org/', //for updating lock-files
    npmrcMerge: true //be combined with a "global" npmrc
};
