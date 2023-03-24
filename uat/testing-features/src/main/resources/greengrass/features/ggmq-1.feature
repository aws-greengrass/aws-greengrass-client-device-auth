@GGAD
Feature: GGMQ-1

  As a developer, I can configure my GGAD devices to connect to the local GGC using cloud based discovery

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass
    And the control component configured:
    """
    {
        "port": 47619
    }
    """

  Scenario: GGMQ-1-T1: As a customer, I can associate and connect a single GGAD with a single GGC based on cda configuration
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                                        |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                                        |
      | aws.greengrass.client.Mqtt5JavaSdkClient | classpath:/greengrass/components/recipes/client_java_sdk.yaml |
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    When I add "agent1" ggad with the following policy to the CDA configuration
      | operation | resource |
      | publish   | "*"      |
    And I associate agent1 with ggc
    And run cloud discovery on agent1
    Then the aws.greengrass.client.Mqtt5JavaSdkClient log on the device contains the line "cloud_discover: SUCCESS" within 3 seconds
    And the aws.greengrass.client.Mqtt5JavaSdkClient log on the device contains the line "connect_command: NOT AUTHORIZED" within 3 seconds
    When I add "agent1" ggad with the following policy to the CDA configuration
      | operation | resource |
      | connect   | "*"      |
    Then the aws.greengrass.client.Mqtt5JavaSdkClient log on the device contains the line "connect_command: SUCCESS" within 3 seconds
    When Client agent1 publish to "iot_data_0" qos 0 message:
    """
    Test message from agent1
    """
    Then the aws.greengrass.client.Mqtt5JavaSdkClient log on the device contains the line "publish_message: SUCCESS" within 3 seconds

