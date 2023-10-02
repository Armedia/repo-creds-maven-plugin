repocreds-maven-plugin
=====================

Decrypts server values from `.m2/settings.xml` and either writes them to files, or sets them into properties.

Status: *released to Maven Central*

[Maven site](http://davidmoten.github.io/decrypt-maven-plugin/index.html)

Usage
------------

Add this to your `pom.xml` in the `<build><plugins>` section:

```xml
<plugin>
    <groupId>com.armedia.maven</groupId>
    <artifactId>repocreds-maven-plugin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <execution>
            <id>repocreds-1</id>
            <phase>initialize</phase>
            <goals>
                <goal>decrypt</goal>
            </goals>
            <configuration>
                <!-- all these values can be set using -Dmaven.repocreds.<valueName> -->

                <!-- The ID of the server whose credentials you wish to decrypt -->
                <serverId>some-server-id</serverId>

                <!-- Whether to skip execution or not -->
                <skip>false</skip>

                <!-- Store values into files -->
                <usernameFile>username.txt</usernameFile>
                <passwordFile>password.txt</passwordFile>
                <privateKeyFile>privateKey.txt</privateKeyFile>
                <passphraseFile>passphrase.txt</passphraseFile>

                <!-- Store valuse into properties -->
                <usernameVar>var.username</usernameVar>
                <passwordVar>var.password</passwordVar>
                <privateKeyVar>var.privateKey</privateKeyVar>
                <passphraseVar>var.passphrase</passphraseVar>
            </configuration>
        </execution>
    </executions>
</plugin>
```

To call:

```bash
mvn repocreds:decrypt -Dmaven.repocreds.serverId=... -Dmaven.repocreds.usernameFile=username.txt ...
```
