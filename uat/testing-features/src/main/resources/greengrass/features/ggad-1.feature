@GGAD
Feature: GGAD-1

  As a developer, I can configure my GGAD devices to connect to the local GGC using cloud based discovery

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  Scenario: GGAD-1-T8: As a customer, I can configure local MQTT messages to be forwarded to a PubSub topic
    And I create client device "publisher"
    When I associate "publisher" with ggc
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                                        |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                                        |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                                        |
      | aws.greengrass.clientdevices.mqtt.Bridge | LATEST                                                        |
      | aws.greengrass.client.Mqtt5JavaSdkClient | classpath:/greengrass/components/recipes/client_java_sdk.yaml |
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.clientdevices.mqtt.Bridge configuration to:
    """
{
  "MERGE":{
    "mqttTopicMapping":{
      "mapping1:":{
        "topic":"topic/to/pubsub",
        "source":"LocalMqtt",
        "target":"Pubsub"
      },
      "mapping2:":{
        "topic":"topic/+/humidity",
        "source":"LocalMqtt",
        "target":"Pubsub"
      },
      "mapping3:":{
        "topic":"topic/device2/#",
        "source":"LocalMqtt",
        "target":"Pubsub"
      },
      "mapping4:":{
        "topic":"topic/with/prefix",
        "source":"LocalMqtt",
        "target":"Pubsub",
        "targetTopicPrefix":"prefix/"
      }
    }
  }
}
"""
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.clientdevices.Auth configuration to:
    """
{
   "MERGE":{
      "deviceGroups":{
         "formatVersion":"2021-03-05",
         "definitions":{
            "MyPermissiveDeviceGroup":{
               "selectionRule": "thingName: ${publisher}",
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
      "controlAddresses":"${mqttControlAddresses}",
      "controlPort":"${mqttControlPort}"
   }
}
    """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    When I associate "publisher" with ggc
    And I discover core device broker as "default_broker" from "publisher"
    And I connect device "publisher" on aws.greengrass.client.Mqtt5JavaSdkClient to "default_broker"
    When I publish from "publisher" to "topic/to/pubsub" with qos 0 and message "Hello world"
    When I publish from "publisher" to "topic/device1/humidity" with qos 0 and message "device1 humidity"
    When I publish from "publisher" to "topic/device2/temperature" with qos 0 and message "device2 temperature"
    When I publish from "publisher" to "topic/with/prefix" with qos 0 and message "topicPrefix message"