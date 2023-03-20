@Auth
Feature: Client Devices Auth

    Scenario: Auth-1-T1-a: Registration and Run
        Given my device is registered as a Thing
        And my device is running Greengrass
        When I create a Greengrass deployment with components
            | aws.greengrass.clientdevices.Auth | LATEST |
        And I deploy the Greengrass deployment configuration
        Then the Greengrass deployment is COMPLETED on the device after 300 seconds
