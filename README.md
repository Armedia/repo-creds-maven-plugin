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
    <configuration>
        <!-- all these values can be set using -Dmaven.repocreds.<valueName> -->

        <!-- The ID of the server whose credentials you wish to decrypt -->
        <serverId>some-server-ID</serverId>

        <!-- Whether to skip execution or not -->
        <skip>true</skip>

        <!-- The file into which to store the username -->
        <usernameFile>...</usernameFile>

        <!-- The file into which to store the password -->
        <passwordFile>...</passwordFile>

        <!-- The file into which to store the private key -->
        <privateKeyFile>...</privateKeyFile>

        <!-- The file into which to store the private key's passphrase -->
        <passphraseFile>...</passphraseFile>

        <!-- The file into which to store the username -->
        <usernameFile>...</usernameFile>

        <!-- The file into which to store the password -->
        <passwordFile>...</passwordFile>

        <!-- The file into which to store the private key -->
        <privateKeyFile>...</privateKeyFile>

        <!-- The file into which to store the private key's passphrase -->
        <passphraseFile>...</passphraseFile>

        <!-- The property into which to store the username -->
        <usernameVar>...</usernameVar>

        <!-- The property into which to store the password -->
        <passwordVar>...</passwordVar>

        <!-- The property into which to store the private key -->
        <privateKeyVar>...</privateKeyVar>

        <!-- The property into which to store the private key's passphrase -->
        <passphraseVar>...</passphraseVar>
    </configuration>
</plugin>
```

To call:

```bash
mvn repocreds:decrypt -Dmaven.repocreds.serverId=... -Dmaven.repocreds.usernameFile=username.txt ...
```
