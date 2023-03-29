@GGAD
Feature: GGMQ-1

  As a developer, I can configure my client agents to connect and use MQTT

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  Scenario: GGMQ-1-T1: As a customer, I can connect, subscribe and publish using client application to MQTT topic
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                                        |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                                        |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                                        |
      | aws.greengrass.client.Mqtt5JavaSdkClient | classpath:/greengrass/components/recipes/client_java_sdk.yaml |
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    And agent "agent1" is connected to MQTT Client Control
    When I create client device "clientDeviceTest"
    And I associate "clientDeviceTest" with ggc
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.clientdevices.Auth configuration to:
    """
{
  "deviceGroups": {
    "formatVersion": "2021-03-05",
    "definitions": {
      "MyPermissiveDeviceGroup": {
        "selectionRule": "thingName: ${clientDeviceTest}",
        "policyName": "MyPermissivePolicy"
      }
    },
    "policies": {
      "MyPermissivePolicy": {
        "AllowAll": {
          "statementDescription": "Allow client devices to perform all actions.",
          "operations": [
            "*"
          ],
          "resources": [
            "*"
          ]
        }
      }
    }
  }
}
    """
    And I discover core device broker as "default_broker"
    And I connect device "clientDeviceTest" on "agent1" to "default_broker" as "connection1"
    Then connection "connection1" is successfully established within 3 seconds
    When I subscribe "connection1" to "iot_data_0" with qos 0
    Then subscription to "iot_data_0" is successfull on "connection1"
    When I publish "connection1" to "iot_data_0" with qos 0 and message "Test message"
    Then publish message "test" to "iot_data_0" is successfully on "connection1"
    And message "Test message" received on "connection1" from "iot_data_0" topic within 1 second

