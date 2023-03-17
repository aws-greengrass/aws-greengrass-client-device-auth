# Test MQTT5 client bases on AWS IoT Device SDK for Java v2

The controlled `test MQTT v5.0 client` is based on AWS IoT Device SDK for Java v2 is used to test Greengrass v2 MQTT v5.0 compatibility.

## How to compile
To compile this client, use the following command:

```sh
mvn clean license:check checkstyle:check pmd:check package
```

# How to test
To run integrated with sources tests, use the following command:
```sh
mvn -ntp -U clean verify
```

# How to run
To run this client use the following command:
```sh
mvn exec:java
```
