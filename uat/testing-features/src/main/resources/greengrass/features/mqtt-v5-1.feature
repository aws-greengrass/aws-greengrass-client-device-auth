@Mqtt5
Feature: MQTTv5-1

  As a developer, I can configure a client to connect to MQTT v5 broker

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass
    And the control component configured:
    """
    {
        "port": 47619
    }
    """

  Scenario: MQTTv5-1-T1-a: As a developer, I can configure and run clients as components
    When I create a Greengrass deployment with components
      | aws.greengrass.client.Mqtt5JavaSdkClient | classpath:/greengrass/components/recipes/client_java_sdk.yaml |
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    And the aws.greengrass.client.Mqtt5JavaSdkClient log on the device contains the line "GRPCControlServer created and listed" within 30 seconds

  Scenario: MQTTv5-1-T1-b: As a developer, I can send and receive MQTT messages
    When I create a Greengrass deployment with components
      | aws.greengrass.client.Mqtt5JavaSdkClient | classpath:/greengrass/components/recipes/client_java_sdk.yaml |
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    And the aws.greengrass.client.Mqtt5JavaSdkClient log on the device contains the line "GRPCControlServer created and listed" within 30 seconds
    When Client agent1 subscribe to "/test/agent1"
    Then the aws.greengrass.client.Mqtt5JavaSdkClient log on the device contains the line "Listen /test/agent1" within 10 seconds
    When Client agent1 publish to "/test/agent1" message:
    """
    Test message from agent1
    """
    Then the aws.greengrass.client.Mqtt5JavaSdkClient log on the device contains the line "Test message from agent1" within 10 seconds