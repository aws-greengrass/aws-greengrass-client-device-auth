@Mqtt5
Feature: MQTTv5-1

  As a developer, I can configure a client to connect to MQTT v5 broker

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  Scenario: MQTTv5-1-T1-a: As a developer, I can configure and run control and clients as components
    When I create a Greengrass deployment with components
      | aws.greengrass.testing.MqttControl       | classpath:/greengrass/components/recipes/mqtt_control.yaml    |
      | aws.greengrass.client.Mqtt5JavaSdkClient | classpath:/greengrass/components/recipes/client_java_sdk.yaml |
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    And the aws.greengrass.client.Mqtt5JavaSdkClient log on the device contains the line "GRPCControlServer created and listed" within 30 seconds

  Scenario: MQTTv5-1-T1-b: As a developer, I can send and receive MQTT messages
    When I create a Greengrass deployment with components
      | aws.greengrass.testing.MqttControl       | classpath:/greengrass/components/recipes/mqtt_control.yaml    |
      | aws.greengrass.client.Mqtt5JavaSdkClient | classpath:/greengrass/components/recipes/client_java_sdk.yaml |
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    And the aws.greengrass.client.Mqtt5JavaSdkClient log on the device contains the line "GRPCControlServer created and listed" within 30 seconds
    Then I configure Control:
    """
    {
        "broker":{
            "address" : "127.0.0.1",
            "port": 8883
        }
    }
    """
    And I configure client scenario:
    """
    {
        "agent1" : {
            [
                {
                    "action": "subscribe",
                    "topic": "/test/agent1"
                },
                {
                    "action": "publish",
                    "topic": "/test/agent1",
                    "message": "Test message from agent1",
                    "delay": 1000
                },
                {
                    "action": "unsubscribe",
                    "topic": "/test/agent1"
                }
            ]
        }
    }
    """
    Then the aws.greengrass.client.Mqtt5JavaSdkClient log on the device contains the line "Received: Test message from agent1" within 10 seconds