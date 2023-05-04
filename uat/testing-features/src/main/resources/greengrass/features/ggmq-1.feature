@GGMQ
Feature: GGMQ-1

  As a developer, I can configure my client agents to connect and use MQTT

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  Scenario: GGMQ-1-T1: As a customer, I can connect, subscribe, publish and receive using client application to MQTT topic
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                                        |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                                        |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                                        |
      | aws.greengrass.client.Mqtt5JavaSdkClient | classpath:/greengrass/components/recipes/client_java_sdk.yaml |
    And I create client device "clientDeviceTest"
    When I associate "clientDeviceTest" with ggc
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.clientdevices.Auth configuration to:
    """
{
   "MERGE":{
      "deviceGroups":{
         "formatVersion":"2021-03-05",
         "definitions":{
            "MyPermissiveDeviceGroup":{
               "selectionRule": "thingName: ${clientDeviceTest}",
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
    And I discover core device broker as "default_broker" from "clientDeviceTest"
    And I connect device "clientDeviceTest" on aws.greengrass.client.Mqtt5JavaSdkClient to "default_broker"
    When I subscribe "clientDeviceTest" to "iot_data_0" with qos 0
    When I publish from "clientDeviceTest" to "iot_data_0" with qos 0 and message "Test message"
    And message "Test message" received on "clientDeviceTest" from "iot_data_0" topic within 5 seconds

  Scenario: GGMQ-1-T8: As a customer, I can configure local MQTT messages to be forwarded to a PubSub topic
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                                        |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                                        |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                                        |
      | aws.greengrass.clientdevices.mqtt.Bridge | LATEST                                                        |
      | aws.greengrass.client.Mqtt5JavaSdkClient | classpath:/greengrass/components/recipes/client_java_sdk.yaml |
      | aws.greengrass.client.IpcClient            | classpath:/greengrass/components/recipes/client_ipc.yaml      |
    And I create client device "publisher"
    When I associate "publisher" with ggc
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
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.client.IpcClient configuration to:
    """
{
   "MERGE": {
       "accessControl": {
           "aws.greengrass.ipc.pubsub": {
               "aws.greengrass.client.IpcClient:pubsub:1": {
                   "policyDescription":"access to pubsub topics",
                   "operations": [ "aws.greengrass#SubscribeToTopic" ],
                   "resources": [ "topic/to/pubsub", "topic/device1/humidity", "topic/device2/temperature", "prefix/topic/with/prefix" ]
               }
           }
       },
       "topicsToSubscribe": "topic/to/pubsub,topic/device1/humidity,topic/device2/temperature,prefix/topic/with/prefix"
   }
}
    """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    Then I discover core device broker as "localMqttBroker" from "publisher"
    And I connect device "publisher" on aws.greengrass.client.Mqtt5JavaSdkClient to "localMqttBroker"
    And I wait 10 seconds
    When I publish from "publisher" to "topic/to/pubsub" with qos 1 and message "Hello world"
    Then the aws.greengrass.client.IpcClient log on the device contains the line "RECEIVED TOPIC=topic/to/pubsub, MESSAGE: Hello world" within 30 seconds
    When I publish from "publisher" to "topic/device1/humidity" with qos 1 and message "device1 humidity"
    Then the aws.greengrass.client.IpcClient log on the device contains the line "RECEIVED TOPIC=topic/device1/humidity, MESSAGE: device1 humidity" within 30 seconds
    When I publish from "publisher" to "topic/device2/temperature" with qos 1 and message "device2 temperature"
    Then the aws.greengrass.client.IpcClient log on the device contains the line "RECEIVED TOPIC=topic/device2/temperature, MESSAGE: device2 temperature" within 30 seconds
    When I publish from "publisher" to "topic/with/prefix" with qos 1 and message "topicPrefix message"
    Then the aws.greengrass.client.IpcClient log on the device contains the line "RECEIVED TOPIC=prefix/topic/with/prefix, MESSAGE: topicPrefix message" within 30 seconds

  Scenario: GGMQ-1-T14: As a customer, I can configure IoT Core messages to be forwarded to local MQTT topic
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                                        |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                                        |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                                        |
      | aws.greengrass.clientdevices.mqtt.Bridge | LATEST                                                        |
      | aws.greengrass.client.Mqtt5JavaSdkClient | classpath:/greengrass/components/recipes/client_java_sdk.yaml |
    And I create client device "localMqttSubscriber"
    And I create client device "iotCorePublisher"
    When I associate "localMqttSubscriber" with ggc
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.clientdevices.Auth configuration to:
    """
{
   "MERGE":{
      "deviceGroups":{
         "formatVersion":"2021-03-05",
         "definitions":{
            "MyPermissiveDeviceGroup":{
               "selectionRule": "thingName: ${localMqttSubscriber}",
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
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.clientdevices.mqtt.Bridge configuration to:
    """
{
  "MERGE": {
      "mqttTopicMapping": {
          "mapping1:": {
              "topic": "${localMqttSubscriber}topic/to/localmqtt",
              "source": "IotCore",
              "target": "LocalMqtt"
          },
          "mapping2:": {
              "topic": "${localMqttSubscriber}topic/+/humidity",
              "source": "IotCore",
              "target": "LocalMqtt"
          },
          "mapping3:": {
              "topic": "${localMqttSubscriber}topic/device2/#",
              "source": "IotCore",
              "target": "LocalMqtt"
          },
          "mapping4:": {
              "topic": "${localMqttSubscriber}topic/with/prefix",
              "source": "IotCore",
              "target": "LocalMqtt",
              "targetTopicPrefix": "prefix/"
          }
      }
  }
}
    """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    When I discover core device broker as "localBroker" from "localMqttSubscriber"
    And I label IoT core broker as "iotCoreBroker"
    And I connect device "localMqttSubscriber" on aws.greengrass.client.Mqtt5JavaSdkClient to "localBroker"
    And I connect device "iotCorePublisher" on aws.greengrass.client.Mqtt5JavaSdkClient to "iotCoreBroker"
    And I subscribe "localMqttSubscriber" to "${localMqttSubscriber}topic/to/localmqtt" with qos 1
    And I subscribe "localMqttSubscriber" to "${localMqttSubscriber}topic/device2/#" with qos 1
    And I subscribe "localMqttSubscriber" to "${localMqttSubscriber}topic/+/humidity" with qos 1
    And I subscribe "localMqttSubscriber" to "prefix/${localMqttSubscriber}topic/with/prefix" with qos 1
    When I publish from "iotCorePublisher" to "${localMqttSubscriber}topic/to/localmqtt" with qos 1 and message "Hello world"
    Then message "Hello world" received on "localMqttSubscriber" from "${localMqttSubscriber}topic/to/localmqtt" topic within 10 seconds
    When I publish from "iotCorePublisher" to "${localMqttSubscriber}topic/device1/humidity" with qos 1 and message "H=10%"
    Then message "H=10%" received on "localMqttSubscriber" from "${localMqttSubscriber}topic/device1/humidity" topic within 10 seconds
    When I publish from "iotCorePublisher" to "${localMqttSubscriber}topic/device2/temperature" with qos 1 and message "T=100C"
    Then message "T=100C" received on "localMqttSubscriber" from "${localMqttSubscriber}topic/device2/temperature" topic within 10 seconds
    When I publish from "iotCorePublisher" to "${localMqttSubscriber}topic/with/prefix" with qos 1 and message "Hello world"
    Then message "Hello world" received on "localMqttSubscriber" from "prefix/${localMqttSubscriber}topic/with/prefix" topic within 10 seconds
