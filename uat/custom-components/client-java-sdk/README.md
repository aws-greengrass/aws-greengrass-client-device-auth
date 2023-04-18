# Test MQTT5 client bases on AWS IoT Device SDK for Java v2

The controlled `test MQTT v5.0 client` is based on AWS IoT Device SDK for Java v2 is used to test Greengrass v2 MQTT v5.0 compatibility.

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
