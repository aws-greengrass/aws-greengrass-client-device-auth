@GGMQ
Feature: GGMQ-1

  As a developer, I can configure my client agents to connect and use MQTT

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  @GGMQ-1-T1
  Scenario Outline: GGMQ-1-T1-<mqtt-v>-<name>: As a customer, I can connect, subscribe/publish at QoS 0 and 1 and receive using client application to MQTT topic
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                            |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                            |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                            |
      | <agent>                                  | classpath:/greengrass/components/recipes/<recipe> |
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
               "AllowConnect": {
                "statementDescription": "Allow client devices to connect.",
                  "operations": [
                    "mqtt:connect"
                  ],
                  "resources": [
                    "*"
                  ]
                },
                "AllowSubscribe": {
                  "statementDescription": "Allow client devices to subscribe to iot_data_1.",
                  "operations": [
                      "mqtt:subscribe"
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
    And I update my Greengrass deployment configuration, setting the component <agent> configuration to:
    """
{
   "MERGE":{
      "controlAddresses":"${mqttControlAddresses}",
      "controlPort":"${mqttControlPort}"
   }
}
    """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    And I discover core device broker as "default_broker" from "clientDeviceTest"
    And I connect device "clientDeviceTest" on <agent> to "default_broker" using mqtt "<mqtt-v>"
    When I subscribe "clientDeviceTest" to "iot_data_0" with qos 0 and expect status "<subscribe-status-q0>"
    When I subscribe "clientDeviceTest" to "iot_data _1" with qos 1 and expect status "<subscribe-status-q1>"
    When I publish from "clientDeviceTest" to "iot_data_0" with qos 0 and message "Hello world"
    When I publish from "clientDeviceTest" to "iot_data_1" with qos 1 and message "Hello world" and expect status <publish-statusq1>
    Then message "Hello world" received on "clientDeviceTest" from "iot_data_1" topic within 10 seconds is false expected
    And I disconnect device "clientDeviceTest" with reason code 0
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST |
      | aws.greengrass.clientdevices.IPDetector  | LATEST |
      | <agent>                                  | LATEST |
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.clientdevices.Auth configuration to:
    """
{
   "MERGE":{
      "deviceGroups":{
         "formatVersion":"2021-03-05",
         "definitions":{
            "PublisherDeviceGroup":{
               "selectionRule": "thingName: ${clientDeviceTest}",
               "policyName":"MyPermissivePublishPolicy"
            }
         },
         "policies":{
            "MyPermissivePublishPolicy":{
                "AllowPublish": {
                  "statementDescription": "Allow client devices to publish on test/topic.",
                  "operations": [
                  "mqtt:publish"
                   ],
                   "resources": [
                      "mqtt:topic:iot_data_1"
                   ]
                  }
            }
         }
      }
   }
}
    """
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.clientdevices.IPDetector configuration to:
    """
{
   "MERGE":{
      "includeIPv4LoopbackAddrs":"true"
   }
}
    """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 120 seconds
    And I connect device "clientDeviceTest" on <agent> to "default_broker" using mqtt "<mqtt-v>"
    When I subscribe "clientDeviceTest" to "iot_data_0" with qos 0 and expect status "<subscribe-status-q0>"
    When I subscribe "clientDeviceTest" to "iot_data_1" with qos 1 and expect status "<subscribe-status-q1>"
    When I publish from "clientDeviceTest" to "iot_data_0" with qos 0 and message "Hello world" and expect status <publish-statusq10>
    When I publish from "clientDeviceTest" to "iot_data_1" with qos 1 and message "Hello world"
    Then message "Hello world" received on "clientDeviceTest" from "iot_data_0" topic within 10 seconds is false expected
    Then message "Hello world" received on "clientDeviceTest" from "iot_data_1" topic within 10 seconds

    @mqtt3 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-q0 | subscribe-status-q1| publish-statusq1 | publish-statusq10 |
      | v3     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    | SUCCESS             | SUCCESS            | 0                | 0                 |

    @mqtt3 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-q0 | subscribe-status-q1| publish-statusq1 | publish-statusq10 |
      | v3     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml | SUCCESS             | SUCCESS            | 0                | 0                 |

    @mqtt5 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-q0 | subscribe-status-q1| publish-statusq1 | publish-statusq10 |
      | v5     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    | SUCCESS             | GRANTED_QOS_1      | 135              | 0                 |

    @mqtt5 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-q0 | subscribe-status-q1| publish-statusq1 | publish-statusq10 |
      | v5     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml | SUCCESS             | SUCCESS            | 0                | 0                 |


  @GGMQ-1-T8
  Scenario Outline: GGMQ-1-T8-<mqtt-v>-<name>: As a customer, I can configure local MQTT messages to be forwarded to a PubSub topic
    When I start an assertion server
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                                   |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                                   |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                                   |
      | aws.greengrass.clientdevices.mqtt.Bridge | LATEST                                                   |
      | <agent>                                  | classpath:/greengrass/components/recipes/<recipe>        |
      | aws.greengrass.client.IpcClient          | classpath:/greengrass/components/recipes/client_ipc.yaml |
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
                   "operations": [ "aws.greengrass#SubscribeToTopic" ],
                   "resources": [ "topic/to/pubsub", "topic/device1/humidity", "topic/device2/temperature", "prefix/topic/with/prefix" ]
               }
           }
       },
       "topicsToSubscribe": "topic/to/pubsub,topic/device1/humidity,topic/device2/temperature,prefix/topic/with/prefix",
       "assertionServerUrl": "${assertionServerUrl}"
   }
}
    """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    Then I discover core device broker as "localMqttBroker" from "publisher"
    And I connect device "publisher" on <agent> to "localMqttBroker" using mqtt "<mqtt-v>"
    And I wait 5 seconds
    When I publish from "publisher" to "topic/to/pubsub" with qos 1 and message "Hello world"
    When I publish from "publisher" to "topic/device1/humidity" with qos 1 and message "device1 humidity"
    When I publish from "publisher" to "topic/device2/temperature" with qos 1 and message "device2 temperature"
    When I publish from "publisher" to "topic/with/prefix" with qos 1 and message "topicPrefix message"
    Then I wait 5 seconds
    And I get 1 assertions with context "ReceivedPubsubMessage" and message "Hello world"
    And I get 1 assertions with context "ReceivedPubsubMessage" and message "device1 humidity"
    And I get 1 assertions with context "ReceivedPubsubMessage" and message "device2 temperature"
    And I get 1 assertions with context "ReceivedPubsubMessage" and message "topicPrefix message"

    @mqtt3 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt3 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |

    @mqtt5 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt5 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |


  @GGMQ-1-T13
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

    @mqtt3 @sdk-java
    Examples:
      | mqtt-v | subscribe-status | publish-status | subscribe-status-auth |
      | v3     | SUCCESS          | 0              | SUCCESS               |

    @mqtt5 @sdk-java
    Examples:
      | mqtt-v | subscribe-status | publish-status | subscribe-status-auth |
      | v5     | NOT_AUTHORIZED   | 16             | GRANTED_QOS_1         |


  @GGMQ-1-T14
  Scenario Outline: GGMQ-1-T14-<mqtt-v>-<name>: As a customer, I can configure IoT Core messages to be forwarded to local MQTT topic
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                            |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                            |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                            |
      | aws.greengrass.clientdevices.mqtt.Bridge | LATEST                                            |
      | <agent>                                  | classpath:/greengrass/components/recipes/<recipe> |
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
    And I update my Greengrass deployment configuration, setting the component <agent> configuration to:
    """
{
   "MERGE":{
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
    And I connect device "localMqttSubscriber" on <agent> to "localBroker" using mqtt "<mqtt-v>"
    And I connect device "iotCorePublisher" on <agent> to "iotCoreBroker" using mqtt "<mqtt-v>"
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

    @mqtt3 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt3 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |

    @mqtt5 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt5 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |


  @GGMQ-1-T101
  Scenario Outline: GGMQ-1-T101-<mqtt-v>-<name>: As a customer, I can configure retain flag and retain handling
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth       | LATEST                                            |
      | aws.greengrass.clientdevices.mqtt.EMQX  | LATEST                                            |
      | aws.greengrass.clientdevices.IPDetector | LATEST                                            |
      | <agent>                                 | classpath:/greengrass/components/recipes/<recipe> |
    And I create client device "publisher"
    And I create client device "subscriber"
    When I associate "subscriber" with ggc
    When I associate "publisher" with ggc
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
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 300 seconds
    Then I discover core device broker as "localMqttBroker1" from "publisher"
    Then I discover core device broker as "localMqttBroker2" from "subscriber"
    And I connect device "publisher" on <agent> to "localMqttBroker1" using mqtt "<mqtt-v>"
    And I connect device "subscriber" on <agent> to "localMqttBroker2" using mqtt "<mqtt-v>"

    And I set MQTT publish retain flag to true

    When I publish from "publisher" to "iot_data_0" with qos 0 and message "Hello world"
    And I set MQTT subscribe retain handling property to "MQTT5_RETAIN_SEND_AT_SUBSCRIPTION"
    When I subscribe "subscriber" to "iot_data_0" with qos 0
    And message "Hello world" received on "subscriber" from "iot_data_0" topic within 5 seconds

    When I publish from "publisher" to "iot_data_1" with qos 0 and message "Hello world"
    And I set MQTT subscribe retain handling property to "MQTT5_RETAIN_SEND_AT_NEW_SUBSCRIPTION"
    When I subscribe "subscriber" to "iot_data_1" with qos 0
    And message "Hello world" received on "subscriber" from "iot_data_1" topic within 5 seconds

    When I publish from "publisher" to "iot_data_2" with qos 0 and message "Hello world"
    And I set MQTT subscribe retain handling property to "MQTT5_RETAIN_DO_NOT_SEND_AT_SUBSCRIPTION"
    When I subscribe "subscriber" to "iot_data_2" with qos 0
    And message "Hello world" received on "subscriber" from "iot_data_2" topic within 5 seconds is <retainHandling-2> expected

    And I set MQTT publish retain flag to false

    When I publish from "publisher" to "iot_data_3" with qos 0 and message "Hello world"
    And I set MQTT subscribe retain handling property to "MQTT5_RETAIN_SEND_AT_SUBSCRIPTION"
    When I subscribe "subscriber" to "iot_data_3" with qos 0
    And message "Hello world" received on "subscriber" from "iot_data_3" topic within 5 seconds is false expected

    When I publish from "publisher" to "iot_data_4" with qos 0 and message "Hello world"
    And I set MQTT subscribe retain handling property to "MQTT5_RETAIN_SEND_AT_NEW_SUBSCRIPTION"
    When I subscribe "subscriber" to "iot_data_4" with qos 0
    And message "Hello world" received on "subscriber" from "iot_data_4" topic within 5 seconds is false expected

    When I publish from "publisher" to "iot_data_5" with qos 0 and message "Hello world"
    And I set MQTT subscribe retain handling property to "MQTT5_RETAIN_DO_NOT_SEND_AT_SUBSCRIPTION"
    When I subscribe "subscriber" to "iot_data_5" with qos 0
    And message "Hello world" received on "subscriber" from "iot_data_5" topic within 5 seconds is false expected

    @mqtt3 @sdk-java
    Examples:
      | mqtt-v | name     | agent                                      | recipe               | retainHandling-2  |
      | v3     | sdk-java | aws.greengrass.client.Mqtt5JavaSdkClient   | client_java_sdk.yaml | true              |

    @mqtt3 @paho-java
    Examples:
      | mqtt-v | name      | agent                                     | recipe                | retainHandling-2 |
      | v3     | paho-java | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml | true             |

    @mqtt5 @sdk-java
    Examples:
      | mqtt-v | name     | agent                                      | recipe               | retainHandling-2  |
      | v5     | sdk-java | aws.greengrass.client.Mqtt5JavaSdkClient   | client_java_sdk.yaml | false             |

    @mqtt5 @paho-java
    Examples:
      | mqtt-v | name      | agent                                     | recipe                | retainHandling-2 |
      | v5     | paho-java | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml | false            |
