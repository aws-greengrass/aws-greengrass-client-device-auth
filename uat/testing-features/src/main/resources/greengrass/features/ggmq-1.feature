@GGMQ
Feature: GGMQ-1

  As a developer, I can configure my client agents to connect and use MQTT

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  Scenario: GGMQ-1-T1: As a customer, I can connect, subscribe, publish and receive using client application to MQTT topic
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                                        |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                                        |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                                        |
      | aws.greengrass.client.Mqtt5JavaSdkClient | classpath:/greengrass/components/recipes/client_java_sdk.yaml |
    And I create client device "clientDeviceTest"
    When I associate "clientDeviceTest" with ggc
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.clientdevices.Auth configuration to:
    """
{
   "MERGE":{
      "deviceGroups":{
         "formatVersion":"2021-03-05",
         "definitions":{
            "MyPermissiveDeviceGroup":{
               "selectionRule": "thingName: ${clientDeviceTest}",
               "policyName":"MyPermissivePolicy"
            }
         },
         "policies":{
            "MyPermissivePolicy":{
               "AllowAll":{
                  "statementDescription":"Allow client devices to perform all actions.",
                  "operations":[
                     "*"
                  ],
                  "resources":[
                     "*"
                  ]
               }
            }
         }
      }
   }
}
    """
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.client.Mqtt5JavaSdkClient configuration to:
    """
{
   "MERGE":{
      "agentId":"aws.greengrass.client.Mqtt5JavaSdkClient",
      "controlAddress":"127.0.0.1",
      "controlPort":"47619"
   }
}
    """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    And I discover core device broker as "default_broker" from "clientDeviceTest"
    And I connect device "clientDeviceTest" on aws.greengrass.client.Mqtt5JavaSdkClient to "default_broker"
    When I subscribe "clientDeviceTest" to "iot_data_0" with qos 0
    When I publish from "clientDeviceTest" to "iot_data_0" with qos 0 and message "Test message"
    And message "Test message" received on "clientDeviceTest" from "iot_data_0" topic within 5 seconds
