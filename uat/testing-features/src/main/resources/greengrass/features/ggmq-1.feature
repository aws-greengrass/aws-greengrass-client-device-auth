@GGMQ
Feature: GGMQ-1

  As a developer, I can configure my client agents to connect and use MQTT

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  @GGMQ-1-T1
  Scenario Outline: GGMQ-1-T1-<mqtt-v>-<name>: As a customer, I can connect, subscribe/publish at QoS 0 and 1 and receive using client application to MQTT topic
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                  |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                  |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                  |
      | <agent>                                  | classpath:/local-store/recipes/<recipe> |
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
                    "selectionRule":"thingName: ${clientDeviceTest}",
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
    Then the Greengrass deployment is COMPLETED on the device after 5 minutes
    And the aws.greengrass.clientdevices.mqtt.EMQX log on the device contains the line "is running now!." within 1 minutes

    And I discover core device broker as "default_broker" from "clientDeviceTest" in OTF
    And I connect device "clientDeviceTest" on <agent> to "default_broker" using mqtt "<mqtt-v>"

    When I subscribe "clientDeviceTest" to "iot_data_0" with qos 0 and expect status "GRANTED_QOS_0"
    When I subscribe "clientDeviceTest" to "iot_data_1" with qos 1 and expect status "<subscribe-status-q1>"

    When I publish from "clientDeviceTest" to "iot_data_0" with qos 0 and message "Test message0"
    And message "Test message0" received on "clientDeviceTest" from "iot_data_0" topic within 10 seconds

    When I publish from "clientDeviceTest" to "iot_data_1" with qos 1 and message "Test message1"
    And message "Test message1" received on "clientDeviceTest" from "iot_data_1" topic within 10 seconds

    And I disconnect device "clientDeviceTest" with reason code 0

    @mqtt3 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-q1 |
      | v3     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    | GRANTED_QOS_0       |

    @mqtt3 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-q1 |
      | v3     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml | GRANTED_QOS_1       |

    @mqtt3 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-q1 |
      | v3     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   | GRANTED_QOS_0       |

    @mqtt5 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-q1 |
      | v5     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    | GRANTED_QOS_1       |

    @mqtt5 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-q1 |
      | v5     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml | GRANTED_QOS_1       |

    @mqtt5 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-q1 |
      | v5     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   | GRANTED_QOS_1       |

  @GGMQ-1-T8
  Scenario Outline: GGMQ-1-T8-<mqtt-v>-<name>: As a customer, I can configure local MQTT messages to be forwarded to a PubSub topic
    When I start an assertion server
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                                 |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                                 |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                                 |
      | aws.greengrass.clientdevices.mqtt.Bridge | LATEST                                                 |
      | <agent>                                  | classpath:/local-store/recipes/<recipe>                |
      | aws.greengrass.client.LocalIpcSubscriber | classpath:/local-store/recipes/LocalIpcSubscriber.yaml |
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
                    "selectionRule":"thingName: ${publisher}",
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
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.client.LocalIpcSubscriber configuration to:
    """
{
    "MERGE":{
        "accessControl":{
            "aws.greengrass.ipc.pubsub":{
                "aws.greengrass.client.IpcClient:pubsub:1":{
                    "policyDescription":"access to pubsub topics",
                    "operations":[
                        "aws.greengrass#SubscribeToTopic"
                    ],
                    "resources":[
                        "topic/to/pubsub",
                        "topic/device1/humidity",
                        "topic/device2/temperature",
                        "prefix/topic/with/prefix"
                    ]
                }
            }
        },
        "topicsToSubscribe":"topic/to/pubsub,topic/device1/humidity,topic/device2/temperature,prefix/topic/with/prefix",
        "assertionServerUrl":"${assertionServerUrl}"
    }
}
    """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 5 minutes
    And the greengrass log on the device contains the line "com.aws.greengrass.mqtt.bridge.clients.MQTTClient: Connected to broker" within 1 minutes

    Then I discover core device broker as "default_broker" from "publisher" in OTF
    And I connect device "publisher" on <agent> to "default_broker" using mqtt "<mqtt-v>"

    When I publish from "publisher" to "topic/to/pubsub" with qos 1 and message "Hello world"
    Then I wait 5 seconds
    And I get 1 assertions with context "ReceivedPubsubMessage" and message "Hello world"

    When I publish from "publisher" to "topic/device1/humidity" with qos 1 and message "device1 humidity"
    Then I wait 5 seconds
    And I get 1 assertions with context "ReceivedPubsubMessage" and message "device1 humidity"

    When I publish from "publisher" to "topic/device2/temperature" with qos 1 and message "device2 temperature"
    Then I wait 5 seconds
    And I get 1 assertions with context "ReceivedPubsubMessage" and message "device2 temperature"

    When I publish from "publisher" to "topic/with/prefix" with qos 1 and message "topicPrefix message"
    Then I wait 5 seconds
    And I get 1 assertions with context "ReceivedPubsubMessage" and message "topicPrefix message"

    And I disconnect device "publisher" with reason code 0

    @mqtt3 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt3 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |

    @mqtt3 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   |

    @mqtt5 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt5 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |

    @mqtt5 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   |

  @GGMQ-1-T9
  Scenario Outline: GGMQ-1-T9-<mqtt-v>-<name>: As a customer,I can configure local MQTT messages to be forwarded to an IoT Core MQTT topic
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                  |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                  |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                  |
      | aws.greengrass.clientdevices.mqtt.Bridge | LATEST                                  |
      | <agent>                                  | classpath:/local-store/recipes/<recipe> |
    And I create client device "localMqttPublisher"
    And I create client device "iotCoreSubscriber"
    When I associate "localMqttPublisher" with ggc
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.clientdevices.Auth configuration to:
    """
{
    "MERGE":{
        "deviceGroups":{
            "formatVersion":"2021-03-05",
            "definitions":{
                "MyPermissiveDeviceGroup":{
                    "selectionRule":"thingName: ${localMqttPublisher}",
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
    "MERGE":{
        "mqttTopicMapping":{
            "mapping1:":{
                "topic":"${iotCoreSubscriber}topic/to/iotcore",
                "source":"LocalMqtt",
                "target":"IotCore"
            },
            "mapping2:":{
                "topic":"${iotCoreSubscriber}topic/+/humidity",
                "source":"LocalMqtt",
                "target":"IotCore"
            },
            "mapping3:":{
                "topic":"${iotCoreSubscriber}topic/device2/#",
                "source":"LocalMqtt",
                "target":"IotCore"
            },
            "mapping4:":{
                "topic":"${iotCoreSubscriber}topic/with/prefix",
                "source":"LocalMqtt",
                "target":"IotCore",
                "targetTopicPrefix":"prefix/"
            }
        }
    }
}
    """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 5 minutes
    And the greengrass log on the device contains the line "com.aws.greengrass.mqtt.bridge.clients.MQTTClient: Connected to broker" within 1 minutes

    When I discover core device broker as "localBroker" from "localMqttPublisher" in OTF
    And I label IoT Core broker as "iotCoreBroker"
    And I connect device "localMqttPublisher" on <agent> to "localBroker" using mqtt "<mqtt-v>"
    And I connect device "iotCoreSubscriber" on <agent> to "iotCoreBroker" using mqtt "<mqtt-v>"

    And I subscribe "iotCoreSubscriber" to "${iotCoreSubscriber}topic/to/iotcore" with qos 1
    And I subscribe "iotCoreSubscriber" to "${iotCoreSubscriber}topic/device2/#" with qos 1
    And I subscribe "iotCoreSubscriber" to "${iotCoreSubscriber}topic/+/humidity" with qos 1
    And I subscribe "iotCoreSubscriber" to "prefix/${iotCoreSubscriber}topic/with/prefix" with qos 1

    When I publish from "localMqttPublisher" to "${iotCoreSubscriber}topic/to/iotcore" with qos 1 and message "Hello world1"
    Then message "Hello world1" received on "iotCoreSubscriber" from "${iotCoreSubscriber}topic/to/iotcore" topic within 10 seconds

    When I publish from "localMqttPublisher" to "${iotCoreSubscriber}topic/device1/humidity" with qos 1 and message "H=10%"
    Then message "H=10%" received on "iotCoreSubscriber" from "${iotCoreSubscriber}topic/device1/humidity" topic within 10 seconds

    When I publish from "localMqttPublisher" to "${iotCoreSubscriber}topic/device2/temperature" with qos 1 and message "T=100C"
    Then message "T=100C" received on "iotCoreSubscriber" from "${iotCoreSubscriber}topic/device2/temperature" topic within 10 seconds

    When I publish from "localMqttPublisher" to "${iotCoreSubscriber}topic/with/prefix" with qos 1 and message "Hello world2"
    Then message "Hello world2" received on "iotCoreSubscriber" from "prefix/${iotCoreSubscriber}topic/with/prefix" topic within 10 seconds

    And I disconnect device "iotCoreSubscriber" with reason code 0
    And I disconnect device "localMqttPublisher" with reason code 0

    @mqtt3 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt3 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |

    @mqtt3 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   |

    @mqtt5 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt5 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |

    @mqtt5 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   |


  @GGMQ-1-T13
  Scenario Outline: GGMQ-1-T13-<mqtt-v>-<name>: As a customer, I can connect two GGADs and send message from one GGAD to the other based on CDA configuration
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                  |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                  |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                  |
      | <agent>                                  | classpath:/local-store/recipes/<recipe> |

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
                    "selectionRule":"thingName: ${publisher} OR thingName: ${subscriber}",
                    "policyName":"Policy1"
                }
            },
            "policies":{
                "Policy1":{
                    "AllowConnect":{
                        "statementDescription":"Allow client devices to connect.",
                        "operations":[
                            "mqtt:connect"
                        ],
                        "resources":[
                            "*"
                        ]
                    },
                    "AllowOnlyPublish":{
                        "statementDescription":"Allow client devices to only publish to all topics.",
                        "operations":[
                            "mqtt:publish"
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
    When I associate "publisher" with ggc
    When I associate "subscriber" with ggc

    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 5 minutes
    And the aws.greengrass.clientdevices.mqtt.EMQX log on the device contains the line "is running now!." within 1 minutes

    And I discover core device broker as "default_broker" from "publisher" in OTF
    And I connect device "publisher" on <agent> to "default_broker" using mqtt "<mqtt-v>"
    And I connect device "subscriber" on <agent> to "default_broker" using mqtt "<mqtt-v>"

    When I subscribe "subscriber" to "iot_data_0" with qos 0 and expect status "<subscribe-status-na>"
    When I subscribe "subscriber" to "iot_data_1" with qos 1 and expect status "<subscribe-status-na>"

    When I publish from "publisher" to "iot_data_0" with qos 0 and message "Hello world 0"
    Then message "Hello world 0" is not received on "subscriber" from "iot_data_0" topic within 10 seconds

    When I publish from "publisher" to "iot_data_1" with qos 1 and message "Hello world 1" and expect status <publish-status-nms>
    Then message "Hello world 1" is not received on "subscriber" from "iot_data_1" topic within 10 seconds

    And I disconnect device "subscriber" with reason code 0
    And I disconnect device "publisher" with reason code 0

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
                "SubscriberDeviceGroup":{
                    "selectionRule":"thingName: ${publisher} OR thingName: ${subscriber}",
                    "policyName":"Policy2"
                }
            },
            "policies":{
                "Policy2":{
                    "AllowConnect":{
                        "statementDescription":"Allow client devices to connect.",
                        "operations":[
                            "mqtt:connect"
                        ],
                        "resources":[
                            "*"
                        ]
                    },
                    "AllowOnlyPublish":{
                        "statementDescription":"Allow client devices to only publish to all topics.",
                        "operations":[
                            "mqtt:publish"
                        ],
                        "resources":[
                            "*"
                        ]
                    },
                    "AllowOneSubscribe":{
                        "statementDescription":"Allow client devices to subscribe to iot_data_1.",
                        "operations":[
                            "mqtt:subscribe"
                        ],
                        "resources":[
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
    Then the Greengrass deployment is COMPLETED on the device after 299 seconds

    And I discover core device broker as "default_broker" from "subscriber" in OTF
    And I connect device "publisher" on <agent> to "default_broker" using mqtt "<mqtt-v>"
    And I connect device "subscriber" on <agent> to "default_broker" using mqtt "<mqtt-v>"

    When I subscribe "subscriber" to "iot_data_0" with qos 0 and expect status "<subscribe-status-na>"
    When I subscribe "subscriber" to "iot_data_1" with qos 1 and expect status "<subscribe-status-good>"

    When I publish from "publisher" to "iot_data_0" with qos 0 and message "Hello world 2"
    Then message "Hello world 2" is not received on "subscriber" from "iot_data_0" topic within 10 seconds

    When I publish from "publisher" to "iot_data_1" with qos 1 and message "Hello world 3"
    Then message "Hello world 3" received on "subscriber" from "iot_data_1" topic within 10 seconds

    And I disconnect device "subscriber" with reason code 0
    And I disconnect device "publisher" with reason code 0

    # WARNING: AWS IoT device SDK Java v2 MQTT v3 client in software.amazon.awssdk.crt.mqtt.MqttClientConnection
    #  missing API to getting actual reason code of SUBACK/PUBACK/UNSUBACK, client always return reason code 0 on publish and subscribe.
    #  It makes sdk-java client useless for T13
    @mqtt3 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-na | subscribe-status-good | publish-status-nms |
      | v3     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    | GRANTED_QOS_0       | GRANTED_QOS_0         | 0                  |

    @mqtt3 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-na | subscribe-status-good | publish-status-nms |
      | v3     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml | UNSPECIFIED_ERROR   | GRANTED_QOS_1         | 0                  |

    # WARNING: Paho Java MQTT v3 client in org.eclipse.paho.client.mqttv3.IMqttAsyncClient
    #  missing API to getting actual reason code of SUBACK/PUBACK/UNSUBACK, client always return reason code 0 on publish and subscribe.
    #  It makes sdk-java client useless for T13
    @mqtt3 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-na | subscribe-status-good | publish-status-nms |
      | v3     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   | GRANTED_QOS_0       | GRANTED_QOS_0         | 0                  |

    @mqtt5 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-na | subscribe-status-good | publish-status-nms |
      | v5     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    | NOT_AUTHORIZED      | GRANTED_QOS_1         | 16                 |

    @mqtt5 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-na | subscribe-status-good | publish-status-nms |
      | v5     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml | NOT_AUTHORIZED      | GRANTED_QOS_1         | 16                 |

    @mqtt5 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  | subscribe-status-na | subscribe-status-good | publish-status-nms |
      | v5     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   | NOT_AUTHORIZED      | GRANTED_QOS_1         | 16                 |


  @GGMQ-1-T14
  Scenario Outline: GGMQ-1-T14-<mqtt-v>-<name>: As a customer, I can configure IoT Core messages to be forwarded to local MQTT topic
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                  |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                  |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                  |
      | aws.greengrass.clientdevices.mqtt.Bridge | LATEST                                  |
      | <agent>                                  | classpath:/local-store/recipes/<recipe> |
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
                    "selectionRule":"thingName: ${localMqttSubscriber}",
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
    "MERGE":{
        "mqttTopicMapping":{
            "mapping1:":{
                "topic":"${localMqttSubscriber}topic/to/localmqtt",
                "source":"IotCore",
                "target":"LocalMqtt"
            },
            "mapping2:":{
                "topic":"${localMqttSubscriber}topic/+/humidity",
                "source":"IotCore",
                "target":"LocalMqtt"
            },
            "mapping3:":{
                "topic":"${localMqttSubscriber}topic/device2/#",
                "source":"IotCore",
                "target":"LocalMqtt"
            },
            "mapping4:":{
                "topic":"${localMqttSubscriber}topic/with/prefix",
                "source":"IotCore",
                "target":"LocalMqtt",
                "targetTopicPrefix":"prefix/"
            }
        }
    }
}
    """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 5 minutes
    And the greengrass log on the device contains the line "com.aws.greengrass.mqtt.bridge.clients.MQTTClient: Connected to broker" within 1 minutes

    When I discover core device broker as "localBroker" from "localMqttSubscriber" in OTF
    And I label IoT Core broker as "iotCoreBroker"
    And I connect device "localMqttSubscriber" on <agent> to "localBroker" using mqtt "<mqtt-v>"
    And I connect device "iotCorePublisher" on <agent> to "iotCoreBroker" using mqtt "<mqtt-v>"

    And I subscribe "localMqttSubscriber" to "${localMqttSubscriber}topic/to/localmqtt" with qos 1
    And I subscribe "localMqttSubscriber" to "${localMqttSubscriber}topic/device2/#" with qos 1
    And I subscribe "localMqttSubscriber" to "${localMqttSubscriber}topic/+/humidity" with qos 1
    And I subscribe "localMqttSubscriber" to "prefix/${localMqttSubscriber}topic/with/prefix" with qos 1

    When I publish from "iotCorePublisher" to "${localMqttSubscriber}topic/to/localmqtt" with qos 1 and message "Hello world1"
    Then message "Hello world1" received on "localMqttSubscriber" from "${localMqttSubscriber}topic/to/localmqtt" topic within 10 seconds

    When I publish from "iotCorePublisher" to "${localMqttSubscriber}topic/device1/humidity" with qos 1 and message "H=10%"
    Then message "H=10%" received on "localMqttSubscriber" from "${localMqttSubscriber}topic/device1/humidity" topic within 10 seconds

    When I publish from "iotCorePublisher" to "${localMqttSubscriber}topic/device2/temperature" with qos 1 and message "T=100C"
    Then message "T=100C" received on "localMqttSubscriber" from "${localMqttSubscriber}topic/device2/temperature" topic within 10 seconds

    When I publish from "iotCorePublisher" to "${localMqttSubscriber}topic/with/prefix" with qos 1 and message "Hello world2"
    Then message "Hello world2" received on "localMqttSubscriber" from "prefix/${localMqttSubscriber}topic/with/prefix" topic within 10 seconds

    And I disconnect device "iotCorePublisher" with reason code 0
    And I disconnect device "localMqttSubscriber" with reason code 0

    @mqtt3 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt3 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |

    @mqtt3 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   |

    @mqtt5 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt5 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |

    @mqtt5 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   |


  @GGMQ-1-T15
  Scenario Outline: GGMQ-1-T15-<mqtt-v>-<name>: As a customer, I can configure Pubsub messages to be forwarded to local MQTT topic
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth        | LATEST                                  |
      | aws.greengrass.clientdevices.mqtt.EMQX   | LATEST                                  |
      | aws.greengrass.clientdevices.IPDetector  | LATEST                                  |
      | aws.greengrass.clientdevices.mqtt.Bridge | LATEST                                  |
      | aws.greengrass.Cli                       | LATEST                                  |
      | <agent>                                  | classpath:/local-store/recipes/<recipe> |
    And I create client device "subscriber"
    When I associate "subscriber" with ggc
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.clientdevices.mqtt.Bridge configuration to:
    """
{
    "MERGE":{
        "mqttTopicMapping":{
            "mapping1:":{
                "topic":"pubsub/topic/to/publish/on",
                "source":"Pubsub",
                "target":"LocalMqtt"
            },
            "mapping2:":{
                "topic":"pubsub/topic/to/publish/on",
                "source":"Pubsub",
                "target":"LocalMqtt",
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
               "selectionRule": "thingName: ${subscriber}",
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
    Then the Greengrass deployment is COMPLETED on the device after 5 minutes
    And the greengrass log on the device contains the line "com.aws.greengrass.mqtt.bridge.clients.MQTTClient: Connected to broker" within 1 minutes

    Then I discover core device broker as "default_broker" from "subscriber" in OTF
    And I connect device "subscriber" on <agent> to "default_broker" using mqtt "<mqtt-v>"

    When I subscribe "subscriber" to "pubsub/topic/to/publish/on" with qos 1
    When I subscribe "subscriber" to "prefix/pubsub/topic/to/publish/on" with qos 1

    When I install the component LocalIpcPublisher from local store with configuration
    """
{
    "MERGE":{
        "accessControl":{
            "aws.greengrass.ipc.pubsub":{
                "aws.greengrass.client.IpcClient:pubsub:1":{
                    "policyDescription":"access to pubsub topics",
                    "operations":[
                        "aws.greengrass#PublishToTopic"
                    ],
                    "resources":[
                        "pubsub/topic/to/publish/on"
                    ]
                }
            }
        },
        "topicsToPublish":"pubsub/topic/to/publish/on",
        "message":"Hello world"
    }
}
    """
    Then the local Greengrass deployment is SUCCEEDED on the device after 120 seconds

    Then I wait 5 seconds
    Then message "Hello world" received on "subscriber" from "pubsub/topic/to/publish/on" topic within 10 seconds
    Then message "Hello world" received on "subscriber" from "prefix/pubsub/topic/to/publish/on" topic within 10 seconds

    And I disconnect device "subscriber" with reason code 0

    @mqtt3 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt3 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |

    @mqtt3 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   |

    @mqtt5 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt5 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |

    @mqtt5 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   |


  @GGMQ-1-T101
  Scenario Outline: GGMQ-1-T101-<mqtt-v>-<name>: As a customer, I can use publish retain flag
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth       | LATEST                                  |
      | aws.greengrass.clientdevices.mqtt.EMQX  | LATEST                                  |
      | aws.greengrass.clientdevices.IPDetector | LATEST                                  |
      | <agent>                                 | classpath:/local-store/recipes/<recipe> |
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
                    "selectionRule":"thingName: ${publisher} OR thingName: ${subscriber}",
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
    Then the Greengrass deployment is COMPLETED on the device after 5 minutes
    And the aws.greengrass.clientdevices.mqtt.EMQX log on the device contains the line "is running now!." within 1 minutes

    Then I discover core device broker as "localMqttBroker1" from "publisher" in OTF
    Then I discover core device broker as "localMqttBroker2" from "subscriber" in OTF
    And I connect device "publisher" on <agent> to "localMqttBroker1" using mqtt "<mqtt-v>"
    And I connect device "subscriber" on <agent> to "localMqttBroker2" using mqtt "<mqtt-v>"

    And I set MQTT publish 'retain' flag to true

    When I publish from "publisher" to "iot_data_0" with qos 0 and message "Hello world0"
    When I subscribe "subscriber" to "iot_data_0" with qos 0
    And message "Hello world0" received on "subscriber" from "iot_data_0" topic within 5 seconds

    And I set MQTT publish 'retain' flag to false

    When I publish from "publisher" to "iot_data_1" with qos 0 and message "Hello world1"
    When I subscribe "subscriber" to "iot_data_1" with qos 0
    And message "Hello world1" is not received on "subscriber" from "iot_data_1" topic within 5 seconds

    And I disconnect device "subscriber" with reason code 0
    And I disconnect device "publisher" with reason code 0

    @mqtt3 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt3 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |

    @mqtt3 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v3     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   |


  @GGMQ-1-T102
  Scenario Outline: GGMQ-1-T102-<mqtt-v>-<name>: As a customer, I can use publish retain flag and subscribe retain handling as expected
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth       | LATEST                                  |
      | aws.greengrass.clientdevices.mqtt.EMQX  | LATEST                                  |
      | aws.greengrass.clientdevices.IPDetector | LATEST                                  |
      | <agent>                                 | classpath:/local-store/recipes/<recipe> |
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
                    "selectionRule":"thingName: ${publisher} OR thingName: ${subscriber}",
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
    Then the Greengrass deployment is COMPLETED on the device after 5 minutes
    And the aws.greengrass.clientdevices.mqtt.EMQX log on the device contains the line "is running now!." within 1 minutes

    Then I discover core device broker as "localMqttBroker1" from "publisher" in OTF
    Then I discover core device broker as "localMqttBroker2" from "subscriber" in OTF
    And I connect device "publisher" on <agent> to "localMqttBroker1" using mqtt "<mqtt-v>"
    And I connect device "subscriber" on <agent> to "localMqttBroker2" using mqtt "<mqtt-v>"

    And I set MQTT publish 'retain' flag to true
    When I publish from "publisher" to "iot_data_001" with qos 0 and message "Old retained message"
    When I publish from "publisher" to "iot_data_001" with qos 0 and message "New retained message"
    And I set MQTT subscribe 'retain handling' property to "MQTT5_RETAIN_SEND_AT_SUBSCRIPTION"
    When I subscribe "subscriber" to "iot_data_001" with qos 0
    And message "Old retained message" is not received on "subscriber" from "iot_data_001" topic within 5 seconds
    And message "New retained message" received on "subscriber" from "iot_data_001" topic within 5 seconds

    And I set MQTT publish 'retain' flag to true
    When I publish from "publisher" to "iot_data_002" with qos 0 and message "Old retained message 002"
    And I set MQTT publish 'retain' flag to false
    When I publish from "publisher" to "iot_data_002" with qos 0 and message "New retained message 002"
    When I subscribe "subscriber" to "iot_data_002" with qos 0
    And message "New retained message 002" is not received on "subscriber" from "iot_data_002" topic within 5 seconds
    And message "Old retained message 002" received on "subscriber" from "iot_data_002" topic within 5 seconds

    And I set MQTT publish 'retain' flag to true

    When I publish from "publisher" to "iot_data_0" with qos 0 and message "Hello world0"
    And I set MQTT subscribe 'retain handling' property to "MQTT5_RETAIN_SEND_AT_SUBSCRIPTION"
    When I subscribe "subscriber" to "iot_data_0" with qos 0
    And message "Hello world0" received on "subscriber" from "iot_data_0" topic within 5 seconds
    And I clear message storage
    When I subscribe "subscriber" to "iot_data_0" with qos 0
    And message "Hello world0" received on "subscriber" from "iot_data_0" topic within 5 seconds

    When I publish from "publisher" to "retained/iot_data_1" with qos 1 and message "Hello world1" and expect status 16
    And I set MQTT subscribe 'retain handling' property to "MQTT5_RETAIN_SEND_AT_NEW_SUBSCRIPTION"
    When I subscribe "subscriber" to "retained/iot_data_1" with qos 0
    And message "Hello world1" received on "subscriber" from "retained/iot_data_1" topic within 5 seconds
    And I clear message storage
    When I subscribe "subscriber" to "retained/iot_data_1" with qos 0
    And message "Hello world1" is not received on "subscriber" from "retained/iot_data_1" topic within 5 seconds

    When I publish from "publisher" to "iot_data_2" with qos 0 and message "Hello world2"
    And I set MQTT subscribe 'retain handling' property to "MQTT5_RETAIN_DO_NOT_SEND_AT_SUBSCRIPTION"
    When I subscribe "subscriber" to "iot_data_2" with qos 0
    And message "Hello world2" is not received on "subscriber" from "iot_data_2" topic within 5 seconds

    And I set MQTT publish 'retain' flag to false

    When I publish from "publisher" to "iot_data_3" with qos 0 and message "Hello world3"
    And I set MQTT subscribe 'retain handling' property to "MQTT5_RETAIN_SEND_AT_SUBSCRIPTION"
    When I subscribe "subscriber" to "iot_data_3" with qos 0
    And message "Hello world3" is not received on "subscriber" from "iot_data_3" topic within 5 seconds

    When I publish from "publisher" to "iot_data_4" with qos 0 and message "Hello world4"
    And I set MQTT subscribe 'retain handling' property to "MQTT5_RETAIN_SEND_AT_NEW_SUBSCRIPTION"
    When I subscribe "subscriber" to "iot_data_4" with qos 0
    And message "Hello world4" is not received on "subscriber" from "iot_data_4" topic within 5 seconds

    When I publish from "publisher" to "iot_data_5" with qos 0 and message "Hello world5"
    And I set MQTT subscribe 'retain handling' property to "MQTT5_RETAIN_DO_NOT_SEND_AT_SUBSCRIPTION"
    When I subscribe "subscriber" to "iot_data_5" with qos 0
    And message "Hello world5" is not received on "subscriber" from "iot_data_5" topic within 5 seconds

    And I disconnect device "subscriber" with reason code 0
    And I disconnect device "publisher" with reason code 0

    @mqtt5 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt5 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |

    @mqtt5 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   |


  @GGMQ-1-T103
  Scenario Outline: GGMQ-1-T103-<mqtt-v>-<name>: As a customer, I can use retain as published flag
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth       | LATEST                                  |
      | aws.greengrass.clientdevices.mqtt.EMQX  | LATEST                                  |
      | aws.greengrass.clientdevices.IPDetector | LATEST                                  |
      | <agent>                                 | classpath:/local-store/recipes/<recipe> |
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
                    "selectionRule":"thingName: ${publisher} OR thingName: ${subscriber}",
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
    Then the Greengrass deployment is COMPLETED on the device after 5 minutes
    And the aws.greengrass.clientdevices.mqtt.EMQX log on the device contains the line "is running now!." within 1 minutes

    Then I discover core device broker as "localMqttBroker1" from "publisher" in OTF
    Then I discover core device broker as "localMqttBroker2" from "subscriber" in OTF
    And I connect device "publisher" on <agent> to "localMqttBroker1" using mqtt "<mqtt-v>"
    And I connect device "subscriber" on <agent> to "localMqttBroker2" using mqtt "<mqtt-v>"

    # 1. test case when subscribe 'retain as published' is false.
    #  In that case 'retain' flag on receive should be false regardless 'retain' value on publish.

    And I set MQTT subscribe 'retain as published' flag to false
    When I subscribe "subscriber" to "iot_data_0" with qos 0

    And I set MQTT publish 'retain' flag to false
    And I set the 'retain' flag in expected received messages to false

    When I publish from "publisher" to "iot_data_0" with qos 0 and message "Hello world1"
    And message "Hello world1" received on "subscriber" from "iot_data_0" topic within 5 seconds

    And I set MQTT publish 'retain' flag to true
    When I publish from "publisher" to "iot_data_0" with qos 0 and message "Hello world2"
    And message "Hello world2" received on "subscriber" from "iot_data_0" topic within 5 seconds


    # 2. test case when subscribe 'retain as published' is true.
    #  In that case 'retain' flag on receive should be equal to 'retain' value on publish.

    And I set MQTT subscribe 'retain as published' flag to true
    When I subscribe "subscriber" to "iot_data_1" with qos 0

    And I set MQTT publish 'retain' flag to false
    And I set the 'retain' flag in expected received messages to false

    When I publish from "publisher" to "iot_data_1" with qos 0 and message "Hello world3"
    And message "Hello world3" received on "subscriber" from "iot_data_1" topic within 5 seconds

    And I set MQTT publish 'retain' flag to true
    And I set the 'retain' flag in expected received messages to true
    When I publish from "publisher" to "iot_data_1" with qos 0 and message "Hello world4"
    And message "Hello world4" received on "subscriber" from "iot_data_1" topic within 5 seconds

    And I disconnect device "subscriber" with reason code 0
    And I disconnect device "publisher" with reason code 0

    @mqtt5 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt5 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |

    @mqtt5 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   |


  @GGMQ-1-T104
  Scenario Outline: GGMQ-1-T104-<mqtt-v>-<name>: As a customer, I can configure user properties for MQTT 5.0
    When I create a Greengrass deployment with components
      | aws.greengrass.clientdevices.Auth       | LATEST                                  |
      | aws.greengrass.clientdevices.mqtt.EMQX  | LATEST                                  |
      | aws.greengrass.clientdevices.IPDetector | LATEST                                  |
      | <agent>                                 | classpath:/local-store/recipes/<recipe> |
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
                    "selectionRule":"thingName: ${publisher} OR thingName: ${subscriber}",
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
    Then the Greengrass deployment is COMPLETED on the device after 5 minutes
    And the aws.greengrass.clientdevices.mqtt.EMQX log on the device contains the line "is running now!." within 1 minutes

    Then I discover core device broker as "localMqttBroker1" from "publisher" in OTF
    Then I discover core device broker as "localMqttBroker2" from "subscriber" in OTF
    And I connect device "publisher" on <agent> to "localMqttBroker1" using mqtt "<mqtt-v>"
    And I connect device "subscriber" on <agent> to "localMqttBroker2" using mqtt "<mqtt-v>"

    And I set MQTT user property with key "type" and value "json" to transmit
    And I set MQTT user property with key "type" and value "json" to receive

    When I subscribe "subscriber" to "iot_data_1" with qos 0
    When I publish from "publisher" to "iot_data_1" with qos 0 and message "Hello world"
    And message "Hello world" received on "subscriber" from "iot_data_1" topic within 5 seconds

    And I clear message storage
    And I clear MQTT user properties to transmit
    And I clear MQTT user properties to receive
    And I set MQTT user property with key "agent-control" and value "control" to transmit
    When I subscribe "subscriber" to "iot_data_2" with qos 0
    When I publish from "publisher" to "iot_data_2" with qos 0 and message "Hello world1"
    And message "Hello world1" is not received on "subscriber" from "iot_data_2" topic within 5 seconds

    And I clear message storage
    And I clear MQTT user properties to transmit
    And I clear MQTT user properties to receive
    When I subscribe "subscriber" to "iot_data_3" with qos 0
    When I publish from "publisher" to "iot_data_3" with qos 0 and message "Hello world2"
    And message "Hello world2" received on "subscriber" from "iot_data_3" topic within 5 seconds

    @mqtt5 @sdk-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | sdk-java    | aws.greengrass.client.Mqtt5JavaSdkClient  | client_java_sdk.yaml    |

    @mqtt5 @mosquitto-c
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | mosquitto-c | aws.greengrass.client.MqttMosquittoClient | client_mosquitto_c.yaml |

    @mqtt5 @paho-java
    Examples:
      | mqtt-v | name        | agent                                     | recipe                  |
      | v5     | paho-java   | aws.greengrass.client.Mqtt5JavaPahoClient | client_java_paho.yaml   |