@GGMQ
Feature: GGMQ-1

  As a developer, I can configure my client agents to connect and use MQTT

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  Scenario Outline: GGMQ-1-T1-<mqtt-v>: As a customer, I can connect, subscribe, publish and receive using client application to MQTT topic
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
    And I connect device "clientDeviceTest" on aws.greengrass.client.Mqtt5JavaSdkClient to "default_broker" using mqtt "<mqtt-v>"
    When I subscribe "clientDeviceTest" to "iot_data_0" with qos 0
    When I publish from "clientDeviceTest" to "iot_data_0" with qos 0 and message "Test message"
    And message "Test message" received on "clientDeviceTest" from "iot_data_0" topic within 5 seconds

    Examples:
      | mqtt-v |
      | v3     |
      | v5     |

  Scenario Outline: GGMQ-1-T13-<mqtt-v>: As a customer, I can connect two GGADs and send message from one GGAD to the other based on CDA configuration
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                                        |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                                        |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                                        |
      | aws.greengrass.client.Mqtt5JavaSdkClient | classpath:/greengrass/components/recipes/client_java_sdk.yaml |
    And I create client device "publisher"
    And I create client device "subscriber"
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.clientdevices.Auth configuration to:
    """
{
   "MERGE":{
      "deviceGroups":{
         "formatVersion":"2021-03-05",
         "definitions":{
            "MyPermissiveDeviceGroup":{
               "selectionRule": "thingName: ${publisher} OR thingName: ${subscriber}",
               "policyName":"MyPermissivePolicy"
            }
         },
         "policies":{
            "MyPermissivePolicy":{
               "AllowConnect": {
                "statementDescription": "Allow client devices to connect.",
                  "operations": [
                    "mqtt:connect"
                  ],
                  "resources": [
                    "*"
                  ]
                },
                "AllowPublish": {
                  "statementDescription": "Allow client devices to publish on test/topic.",
                    "operations": [
                      "mqtt:publish"
                    ],
                     "resources": [
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
    When I associate "publisher" with ggc
    When I associate "subscriber" with ggc
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    And I discover core device broker as "default_broker" from "publisher"
    And I connect device "publisher" on aws.greengrass.client.Mqtt5JavaSdkClient to "default_broker" using mqtt "<mqtt-v>"
    And I connect device "subscriber" on aws.greengrass.client.Mqtt5JavaSdkClient to "default_broker" using mqtt "<mqtt-v>"
    When I subscribe "subscriber" to "iot_data_0" with qos 0 and expect status "<subscribe-status>"
    When I subscribe "subscriber" to "iot_data_1" with qos 1 and expect status "<subscribe-status>"
    When I publish from "publisher" to "iot_data_0" with qos 0 and message "Hello world"
    When I publish from "publisher" to "iot_data_1" with qos 1 and message "Hello world" and expect status <publish-status>
    Then message "Hello world" received on "subscriber" from "iot_data_1" topic within 10 seconds is false expected
    And I disconnect device "subscriber" with reason code 0
    And I disconnect device "publisher" with reason code 0
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST |
      | aws.greengrass.clientdevices.IPDetector  | LATEST |
      | aws.greengrass.client.Mqtt5JavaSdkClient | LATEST |
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.clientdevices.Auth configuration to:
    """
{
   "MERGE":{
      "deviceGroups":{
         "formatVersion":"2021-03-05",
         "definitions":{
            "SubscriberDeviceGroup":{
               "selectionRule": "thingName: ${subscriber}",
               "policyName":"MyPermissiveSubscribePolicy"
            }
         },
         "policies":{
            "MyPermissiveSubscribePolicy":{
                "AllowSubscribe": {
                  "statementDescription": "Allow client devices to subscribe to iot_data_1.",
                  "operations": [
                  "mqtt:subscribe"
                   ],
                   "resources": [
                      "mqtt:topicfilter:iot_data_1"
                   ]
                  }
            }
         }
      }
   }
}
    """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 120 seconds
    And I discover core device broker as "default_broker" from "subscriber"
    And I connect device "publisher" on aws.greengrass.client.Mqtt5JavaSdkClient to "default_broker" using mqtt "<mqtt-v>"
    And I connect device "subscriber" on aws.greengrass.client.Mqtt5JavaSdkClient to "default_broker" using mqtt "<mqtt-v>"
    When I subscribe "subscriber" to "iot_data_0" with qos 0 and expect status "<subscribe-status>"
    When I subscribe "subscriber" to "iot_data_1" with qos 1 and expect status "<subscribe-status-auth>"
    When I publish from "publisher" to "iot_data_0" with qos 0 and message "Hello world"
    When I publish from "publisher" to "iot_data_1" with qos 1 and message "Hello world"
    Then message "Hello world" received on "subscriber" from "iot_data_0" topic within 10 seconds is false expected
    Then message "Hello world" received on "subscriber" from "iot_data_1" topic within 10 seconds

    Examples:
      | mqtt-v | subscribe-status | publish-status | subscribe-status-auth |
      | v3     | SUCCESS          | 0              | SUCCESS               |
      | v5     | NOT_AUTHORIZED   | 16             | GRANTED_QOS_1         |

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
