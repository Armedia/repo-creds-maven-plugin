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
				<serverId>some-server-id</serverId>

				<!-- Whether to skip execution or not -->
				<skip>false</skip>

				<!-- The file into which to store the username -->
				<usernameFile>username.txt</usernameFile>

				<!-- The file into which to store the password -->
				<passwordFile>password.txt</passwordFile>

				<!-- The file into which to store the private key -->
				<privateKeyFile>privateKey.txt</privateKeyFile>

				<!-- The file into which to store the private key's passphrase -->
				<passphraseFile>passphrase.txt</passphraseFile>

				<!-- The property into which to store the username -->
				<usernameVar>var.username</usernameVar>

				<!-- The property into which to store the password -->
				<passwordVar>var.password</passwordVar>

				<!-- The property into which to store the private key -->
				<privateKeyVar>var.privateKey</privateKeyVar>

				<!-- The property into which to store the private key's passphrase -->
				<passphraseVar>var.passphrase</passphraseVar>
			</configuration>
</plugin>
```

To call:

```bash
mvn repocreds:decrypt -Dmaven.repocreds.serverId=... -Dmaven.repocreds.usernameFile=username.txt ...
```
