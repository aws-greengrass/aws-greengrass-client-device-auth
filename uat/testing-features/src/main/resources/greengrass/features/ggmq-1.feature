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
    And agent "agent1" is connected to MQTT Client Control
    When I create client device "test" with the following policy
      | operation | resource     |
      | connect   | "*"          |
      | subscribe | "iot_data_0" |
      | publish   | "iot_data_0" |
    And I associate "test" with ggc
    And I connect device "test" on "agent1" to "default_broker" as "agent1-test-default_broker"
    Then connection "agent1-test-default_broker" is successfully established within 3 seconds
    When I subscribe "agent1-test-default_broker" to "iot_data_0" with qos 0
    Then subscription to "iot_data_0" is successfull on "agent1-test-default_broker"
    When I publish "agent1-test-default_broker" to "iot_data_0" with qos 0 and message "Test message"
    Then publish message "test" to "iot_data_0" is successfully on "agent1-test-default_broker"
    And message "Test message" received on "agent1-test-default_broker"

