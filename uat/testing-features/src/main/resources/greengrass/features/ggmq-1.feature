@GGAD
Feature: GGMQ-1

  As a developer, I can configure my client agents to connect and use MQTT

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass
    And my MQTT Client Control is running

  Scenario: GGMQ-1-T1: As a customer, I can connect client application to MQTT topic
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                                        |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                                        |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                                        |
      | aws.greengrass.client.Mqtt5JavaSdkClient | classpath:/greengrass/components/recipes/client_java_sdk.yaml |
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    And I create client device "test" on "agent1" with the following policy
      | operation | resource |
      | connect   | "*"      |
    And I associate "test" with ggc
    And I connect "test" to "iot_data_0"
    Then device "test" is successfully connected to "iot_data_0" within 3 seconds

