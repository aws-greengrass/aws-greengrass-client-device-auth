## Client Devices Auth User Acceptance Tests
User Acceptance Tests for Client Devices Auth run using `aws-greengrass-testing` as a library.
They execute E2E tests which will spin up an instance of Greengrass on your device and execute different sets of tests, 
by installing the `aws.greengrass.clientdevices.Auth` component.

## Install requirements
```bash
sudo apt-get install -y maven python3-venv docker.io
```

## Build the project
```bash
mvn -ntp -U clean verify
```

## Running UATs locally

### Requirements
Some scenarios require to install and run the EMQX, Auth, IPDetector Greengrass components.

#### Docker on Linux
Because EMQX component running in a docker containter on Linux, docker should be installed on workstation where scenario is running.

#### Privileges
Also it requires privileges, for Greengrass components that means posix user which running Nucleus can execute sudo command without password requirement.
Please update your /etc/sudoers on Linux to satisfy this requirement.

### AWS credentials
Ensure credentials are available by setting them in environment variables. In unix based systems:

```bash
export AWS_REGION=<AWS_REGION>
export AWS_ACCESS_KEY_ID=<AWS_ACCESS_KEY_ID>
export AWS_SECRET_ACCESS_KEY=<AWS_SECRET_ACCESS_KEY>
```

on Windows Powershell

```bash
$Env:AWS_REGION=<AWS_REGION>
$Env:AWS_ACCESS_KEY_ID=<AWS_ACCESS_KEY_ID>
$Env:AWS_SECRET_ACCESS_KEY=<AWS_SECRET_ACCESS_KEY>
```

- Configure user credentials for Windows devices
    - create user ggc_user
    - assign ggc_user to Administrators group and remove from Users group
    - log out as your used and log in as ggc_user
    - open cmd as Administrator


- Configure user credentials for Linux devices
    - create user ggc_user
    - add ggc_user to docker group

### Build the test Jar
For UATs to run you will need to package your entire uat project into an uber jar. To do run (from the uat directory of the project)

```
mvn -U -ntp clean verify
```

Note: Everytime you make changes to the codebase you will have to rebuild the uber jar for those changes to be present on the final artifact.

### Nucleus installation package
Finally, download the zip containing the latest version of the Nucleus, which will be used to provision Greengrass for the UATs.

```bash
curl -s https://d2s8p88vqu9w66.cloudfront.net/releases/greengrass-nucleus-latest.zip > greengrass-nucleus-latest.zip
```

### Run scenarios
Execute the UATs by running the following commands from the uat directory of the project.

```bash
sudo java -Dggc.archive=<path-to-nucleus-zip> -Dtest.log.path=<path-to-test-results-folder> -Dtags=GGMQ -jar <path-to-test-jar>
```
An example to run from uat directory:
```bash
sudo java -Dggc.archive=greengrass-nucleus-latest.zip -Dtest.log.path=logs -Dtags="GGMQ" -jar testing-features/target/client-devices-auth-testing-features.jar
```

On Windows due to mosquitto-based client is not yet build on Windows use a little modified command.
```cmd
java -Dggc.archive=greengrass-nucleus-latest.zip -Dtest.log.path=logs -Dtags="@GGMQ and not @mosquitto-c" -jar testing-features/target/client-devices-auth-testing-features.jar
```

Command arguments:

Dggc.archive - path to the nucleus zip that was downloaded<br />
Dtest.log.path - path where you would like the test results to be stored<br />
Dtags can be extended, if you would like to test exact scenario, you can do as follows:<br />
```bash
java -Dggc.archive=greengrass-nucleus-latest.zip -Dtest.log.path=logs -Dtags="@GGMQ-1-T1 and @sdk-java and @mqtt3" -jar testing-features/target/client-devices-auth-testing-features.jar
```
