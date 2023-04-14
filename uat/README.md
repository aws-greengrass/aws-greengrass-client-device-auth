## Client Devices Auth User Acceptance Tests
User Acceptance Tests for Client Devices Auth run using `aws-greengrass-testing-standalone` as a library. They 
execute E2E
tests which will spin up an instance of Greengrass on your device and execute different sets of tests, by installing
the `aws.greengrass.clientdevices.Auth` component.

## Running UATs locally

### Requirements
Some scenarios require to install and run the EMQX, Auth, IPDetector Greengrass components.

#### Docker
Because EMQX compoment running in a docker containter docker should be installed on workstation where scenario is running.

#### Rrivileges
Also it requires privileges, for Greengrass components that means posix user which running Nucleus can execute sudo command without password requirement.
Please update your /etc/sudoers on Linux to satisfy this requirement.

### AWS credentials
Ensure credentials are available by setting them in environment variables. In unix based systems:

```bash
export AWS_ACCESS_KEY_ID=<AWS_ACCESS_KEY_ID>
export AWS_SECRET_ACCESS_KEY=<AWS_SECRET_ACCESS_KEY>
```

on Windows Powershell

```bash
$Env:AWS_ACCESS_KEY_ID=<AWS_ACCESS_KEY_ID>
$Env:AWS_SECRET_ACCESS_KEY=<AWS_SECRET_ACCESS_KEY>
```

### Build the test Jar
For UATs to run you will need to package your entire application along with `aws-greengrass-testing-standalone` into
an uber jar. To do run (from the root of the project)

```
mvn -U -ntp clean verify -f uat/pom.xml
```

Note: Everytime you make changes to the codebase you will have to rebuild the uber jar for those changes to be present on the final artifact.

### Nucleus installation package
Finally, download the zip containing the latest version of the Nucleus, which will be used to provision Greengrass for the UATs.

```bash
curl -s https://d2s8p88vqu9w66.cloudfront.net/releases/greengrass-nucleus-latest.zip > greengrass-nucleus-latest.zip
```

### Run scenarios
Execute the UATs by running the following commands from the root of the project.

```
java -Dggc.archive=<path-to-nucleus-zip> -Dtest.log.path=<path-to-test-results-folder> -Dtags=GGMQ -jar <path-to-test-jar>
java -Dggc.archive=./greengrass-nucleus-latest.zip -Dtest.log.path=./logs -Dtags=GGMQ -jar uat/testing-features/target/client-devices-auth-testing-features.jar
```

Command arguments:

Dggc.archive - path to the nucleus zip that was downloaded
Dtest.log.path - path where you would like the test results to be stored

