# Test MQTT5 client bases on AWS IoT Device SDK for Java v2

The test controlled MQTT v5.0 client based on AWS IoT Device SDK for Java v2 is used to test Greengrass v2 MQTT v5.0 compatibility.

## How to compile

To complie this client, use the following command:

```sh
mvn clean license:check checkstyle:check pmd:check package
```

# How to run
To Run this client use the following command:
```sh
mvn exec:java
```
