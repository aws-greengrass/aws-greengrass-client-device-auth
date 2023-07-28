## Client Devices Auth User Acceptance Tests
User Acceptance Tests for Client Devices Auth run using `aws-greengrass-testing` as a library.
They execute E2E tests which will spin up an instance of Greengrass on your device and execute different sets of tests, 
by installing the `aws.greengrass.clientdevices.Auth` component.

## Install requirements on Linux
```bash
sudo apt-get install -y maven python3 python3-venv python3-pip docker.io
```

## Installation requirements on Windows
Manually install Java, maven, python with pip, git and [psexec](https://learn.microsoft.com/en-us/sysinternals/downloads/psexec) tools.

## Build the project
```bash
mvn -ntp -U clean verify
```

## Running UATs locally

Some scenarios require to install and run the EMQX, Auth, IPDetector Greengrass components.

### Docker on Linux
Because EMQX component running in a docker containter on Linux, docker should be installed on workstation where scenario is running.

### Credentials
- Configure user credentials for Linux devices
    - create user ggc_user
    - add ggc_user to docker group

```bash
sudo adduser ggc_user
sudo usermod -a -G docker ggc_user
```

- Configure user credentials for Windows devices manually
    - create user ggc_user
    - assign ggc_user to Administrators group and remove from Users group
    - log out as your used and log in as ggc_user
    - open cmd as Administrator
    - set a user's password to never expire, run the following command.
    - store user name and password for the default user in the Credential Manager instance for the LocalSystem account (replace PASSWORD with actual password of ggc_user)

```cmd
wmic UserAccount where "Name='ggc_user'" set PasswordExpires=False
```

```cmd
psexec -s cmd /c cmdkey /generic:ggc_user /user:ggc_user /pass:PASSWORD
```

### Privileges on Linux
Also it requires privileges, for Greengrass components that means posix user which running Nucleus can execute sudo command without password requirement.
Please update your /etc/sudoers on Linux to satisfy this requirement.
See (Set up your environment)[https://docs.aws.amazon.com/greengrass/v2/developerguide/getting-started-set-up-environment.html] for more information.

### AWS credentials on Linux
Ensure credentials are available by setting them in environment variables. In unix based systems:

```bash
export AWS_REGION=<AWS_REGION>
export AWS_ACCESS_KEY_ID=<AWS_ACCESS_KEY_ID>
export AWS_SECRET_ACCESS_KEY=<AWS_SECRET_ACCESS_KEY>
```

### AWS credentials on Windows
in Windows Powershell

```powershell
$Env:AWS_REGION=<AWS_REGION>
$Env:AWS_ACCESS_KEY_ID=<AWS_ACCESS_KEY_ID>
$Env:AWS_SECRET_ACCESS_KEY=<AWS_SECRET_ACCESS_KEY>
```

in Windows cmd
```cmd
set AWS_REGION=<AWS_REGION>
set AWS_ACCESS_KEY_ID=<AWS_ACCESS_KEY_ID>
set AWS_SECRET_ACCESS_KEY=<AWS_SECRET_ACCESS_KEY>
```


### Build the test Jar
For UATs to run you will need to package your entire uat project into an uber jar. To do run (from the uat directory of the project)

```
mvn -U -ntp clean verify
```

Note: Everytime you make changes to the codebase you will have to rebuild the uber jar for those changes to be present on the final artifact.

### Nucleus installation package on Linux
Finally, download the zip containing the latest version of the Nucleus, which will be used to provision Greengrass for the UATs.

```bash
curl -s https://d2s8p88vqu9w66.cloudfront.net/releases/greengrass-nucleus-latest.zip > greengrass-nucleus-latest.zip
```

### Nucleus installation package on Windows
Download https://d2s8p88vqu9w66.cloudfront.net/releases/greengrass-nucleus-latest.zip manually and place to directory where you will run scenarios.


### Run scenarios on Linux
Execute the UATs by running the following commands from the uat directory of the project.

```bash
sudo -E java -Dggc.archive=<path-to-nucleus-zip> -Dtest.log.path=<path-to-test-results-folder> -Dtags=GGMQ -jar <path-to-test-jar>
```
An example to run from uat directory:
```bash
sudo -E java -Dggc.archive=greengrass-nucleus-latest.zip -Dtest.log.path=logs -Dtags="GGMQ" -jar testing-features/target/client-devices-auth-testing-features.jar
```

### Run scenarios on Windows
On Windows due to mosquitto-based client is not yet build on Windows use a little modified command.
```
java -Dggc.archive=greengrass-nucleus-latest.zip -Dtest.log.path=logs -Dtags="@GGMQ and not @SkipOnWindows" -jar testing-features/target/client-devices-auth-testing-features.jar
```


### Command arguments:
Dggc.archive - path to the nucleus zip that was downloaded.
Dtest.log.path - path where you would like the test results to be stored.

### Tags
-Dtags can be extended if you would like to test exact scenarios or exclude some scenarios:

@GGMQ - to start all GGMQ test scenarios run.\
Example:
```bash
sudo -E java -Dggc.archive=greengrass-nucleus-latest.zip -Dtest.log.path=logs -Dtags="@GGMQ" -jar testing-features/target/client-devices-auth-testing-features.jar
```

To choose running of specific tests using the following tags:

@sdk-java, @mosquitto-c, @paho-java, @paho-python - to choose client to run test scenarios.\
Example:
```bash
sudo -E java -Dggc.archive=greengrass-nucleus-latest.zip -Dtest.log.path=logs -Dtags="@GGMQ and @sdk-java" -jar testing-features/target/client-devices-auth-testing-features.jar
```

@mqtt3, @mqtt5 - to choose protocol version for test scenarios.\
Example:
```bash
sudo -E java -Dggc.archive=greengrass-nucleus-latest.zip -Dtest.log.path=logs -Dtags="@GGMQ and @mqtt5" -jar testing-features/target/client-devices-auth-testing-features.jar
```

@GGMQ-1-T(scenario number) - to specify scenario number that should be run.\
Example: 
```bash
sudo -E java -Dggc.archive=greengrass-nucleus-latest.zip -Dtest.log.path=logs -Dtags="@GGMQ-1-T1" -jar testing-features/target/client-devices-auth-testing-features.jar
```

@SkipOnWindows - to skip clients or tests that are not supported by Windows.\
Example:
```
java -Dggc.archive=greengrass-nucleus-latest.zip -Dtest.log.path=logs -Dtags="@GGMQ and not @SkipOnWindows" -jar testing-features/target/client-devices-auth-testing-features.jar
```

@OffTheNetwork - scenarios with port 8883 blocked for some time

To run tests matching ALL following criteria and all tags should be listed using "and" between tags
```bash
sudo -E java -Dggc.archive=greengrass-nucleus-latest.zip -Dtest.log.path=logs -Dtags="@GGMQ-1-T1 and @sdk-java and @mqtt5" -jar testing-features/target/client-devices-auth-testing-features.jar
```


### Run scenarios on CodeBuild
Due to scenario usually requires upload/download artifacts to S3, create and delete roles, policies, do Greengrass discovery and so one please ensure CodeBuild instance has enough AWS permissions to do that.
For more information please read [Create a CodeBuild service role](https://docs.aws.amazon.com/codebuild/latest/userguide/setting-up.html#setting-up-service-role)

## Generate html-documentation from JavaDoc
To generate html-documentation from JavaDoc, run the following command from the uat directory of the project:
```bash
mvn -ntp -U clean install
mvn javadoc:javadoc
```
The main html-file will be located in each module by path **target/site/apidocs/index.html**

## Limitations
MQTT clients based on IoT Device SDK for Java v2, mosquitto C, Paho Java, Paho Python do no provide API to get information from PUBREC/PUBREL/PUBCOMP packages used when messages published with QoS 2.

Not all features of MQTT v5.0 have been implemented in clients and are supported by gRPC proto and the control as was requested, these are not bugs but designed by requirement.