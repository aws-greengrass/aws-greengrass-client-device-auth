## AWS Greengrass Device Certificate Manager

This module implements the Device Certificate Manager (DCM) for Greengrass.

As part of the effort to secure communications between GGC and devices, and also for devices to discover GGCs
DCM does the following things:
* Generate the certificate vended by the local MQTT server in the GGC to devices.
* React to the connectivity information changes
* Regenerate the corresponding server cert when this information changes
* Manage server cert expiry, and renew it when needed
* React to the rotation of the group CA

#### License

This library is licensed under the Apache 2.0 License. 
