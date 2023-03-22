@Mqtt5
Feature: MQTTv5-1

  As a developer, I can configure a client to connect to MQTT v5 broker

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  Scenario: MQTTv5-1-T1-a: As a developer, I can configure and run clients as a components
    When I create a Greengrass deployment with components
      | aws.greengrass.testing.MqttControl       | classpath:/greengrass/components/recipes/mqtt_control.yaml    |
      | aws.greengrass.client.Mqtt5JavaSdkClient | classpath:/greengrass/components/recipes/client_java_sdk.yaml |
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    And the aws.greengrass.client.Mqtt5JavaSdkClient log on the device contains the line "GRPCControlServer created and listed" within 30 seconds
