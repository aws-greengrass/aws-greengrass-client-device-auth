@GGG
Feature: GGMQ-1

  As a developer, I can configure my client agents to connect and use MQTT

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  Scenario Outline: GGMQ-1-T15-<mqtt-v>-<name>: As a customer, I can configure local MQTT messages to be forwarded to a PubSub topic
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                                             |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                                             |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                                             |
      | aws.greengrass.clientdevices.mqtt.Bridge | LATEST                                                             |
      | <agent>                                  | classpath:/greengrass/components/recipes/<recipe>                  |
      | aws.greengrass.client.IpcClient          | classpath:/greengrass/components/recipes/client_ipc_publisher.yaml |
    And I create client device "publisher"
    When I associate "publisher" with ggc
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.clientdevices.mqtt.Bridge configuration to:
    """
{
    "MERGE":{
        "mqttTopicMapping":{
            "mapping5:":{
                "topic":"pubsub/topic/to/publish/on",
                "source":"Pubsub",
                "target":"LocalMqtt"
            },
            "mapping6:":{
                "topic":"pubsub/topic/to/publish/on",
                "source":"Pubsub",
                "target":"LocalMqtt",
                "targetTopicPrefix": "prefix/"
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
    And I update my Greengrass deployment configuration, setting the component <agent> configuration to:
    """
{
   "MERGE":{
      "controlAddresses":"${mqttControlAddresses}",
      "controlPort":"${mqttControlPort}"
   }
}
    """
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.client.IpcClient configuration to:
    """
{
   "MERGE": {
       "accessControl": {
           "aws.greengrass.ipc.pubsub": {
               "aws.greengrass.client.IpcClient:pubsub:1": {
                   "policyDescription":"access to pubsub topics",
                   "operations": [ "aws.greengrass#PublishToTopic" ],
                   "resources": [ "pubsub/topic/to/publish/on" ]
               }
           }
       },
       "topicsToPublish": "pubsub/topic/to/publish/on",
       "message": "Hello world",
       "qos": "0"
   }
}
    """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    Then I discover core device broker as "localMqttBroker" from "publisher"
    And I connect device "publisher" on <agent> to "localMqttBroker" using mqtt "<mqtt-v>"
    When I subscribe "publisher" to "pubsub/topic/to/publish/on" with qos 1
    When I subscribe "publisher" to "prefix/pubsub/topic/to/publish/on" with qos 0
    Then I wait 5 seconds
    Then message "Hello world" received on "publisher" from "pubsub/topic/to/publish/on" topic within 10 seconds

    @mqtt3 @sdk-java
    Examples:
      | mqtt-v | name     | agent                                    | recipe               |
      | v3     | sdk-java | aws.greengrass.client.Mqtt5JavaSdkClient | client_java_sdk.yaml |

    @mqtt3 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |

    @mqtt5 @sdk-java
    Examples:
      | mqtt-v | name     | agent                                    | recipe               |
      | v5     | sdk-java | aws.greengrass.client.Mqtt5JavaSdkClient | client_java_sdk.yaml |

    @mqtt5 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |
