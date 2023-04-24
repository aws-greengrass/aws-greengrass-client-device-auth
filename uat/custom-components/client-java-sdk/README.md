# Test MQTT5/311 client bases on AWS IoT Device SDK for Java v2

The controlled `test MQTT v5.0/v3.1.1 client` is based on AWS IoT Device SDK for Java v2 is used to test Greengrass v2 MQTT v5.0 compatibility.

## How to compile
To compile this client, use the following command:

```sh
mvn clean license:check checkstyle:check pmd:check package
```

## Arguments
Currently that client support three arguments and only first is mandatory.

### Agent Id
Arbitrary string which will identify that instance of client in Control.

### IP of control
The IP address where gRPC server of Control is listening.
Value 127.0.0.1 will be used by default.

### Port of control
The TCP port where gRPC server of Control is listening.
Value 47619 will be used by default.

# How to test
To run integrated with sources tests, use the following command:
```sh
mvn -ntp -U clean verify
```

# How to run from maven
To run this client with default settings use the following command:
```sh
mvn exec:java
```
# How to run manually with custom arguments
```sh
java -jar target/client-devices-auth-uat-client-java-sdk.jar agent1 127.0.0.1 47619
```

# Limitations
That client support both MQTT v5.0 and MQTT v3.1.1 protocols.
But because both clients are based on separated clients implemented in IoT device SDK and CRT libraries it differs in protocol-level information provided to control.

Will message not yet supported.

## MQTT v5.0 client
Currenly information from packets related to QoS2 like PUBREC PUBREL PUBCOMP is missing.

## MQTT v3.1.1 client
String result code is not available in MQTT 3.1.1, corresponding fields of gRPC messages will not be set.

SDK-based client does not provide OS specific error code or string, corresponding fields of gRPC messages will be not set.

SDK-based client provides only session present flag of CONNACK packet. The Connect Return code of CONNACK is missing.

Raal SUBACK information is not available from that client. Instead hard-coded Result Code 0 is used to create a response on gRPC request.

Real PUBACK information is not available from that client. Instead hard-coded Result Code 0 is used to create a response on gRPC request.
