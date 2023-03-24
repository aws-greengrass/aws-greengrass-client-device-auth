@GGAD
Feature: GGMQ-1

  As a developer, I can configure my client agents to connect and use MQTT

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass
    And my MQTT Client Control is running

  Scenario: GGMQ-1-T1: As a customer, I can connect, subscribe and publish using client application to MQTT topic
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                                        |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                                        |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                                        |
      | aws.greengrass.client.Mqtt5JavaSdkClient | classpath:/greengrass/components/recipes/client_java_sdk.yaml |
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    And I create client device "test" on "agent1" with the following policy
      | operation | resource     |
      | connect   | "*"          |
      | subscribe | "iot_data_0" |
      | publish   | "iot_data_0" |
    And I associate "test" with ggc
    And I connect device "test" to broker
    Then device "test" is successfully connected to broker within 3 seconds
    When I subscribe device "test" to "iot_data_0" with qos 0
    Then device "test" is successfully subscribed to "iot_data_0"
    When I publish device "test" to "iot_data_0" with qos 0 and message "Test message"
    Then device "test" is successfully published message "Test message" to "iot_data_0"
    And device "test" received from "iot_data_0" message "Test message"

