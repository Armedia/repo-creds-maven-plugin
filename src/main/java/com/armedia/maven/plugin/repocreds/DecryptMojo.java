package com.armedia.maven.plugin.repocreds;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;

@Mojo(name = "decrypt", defaultPhase = LifecyclePhase.INITIALIZE)
public final class DecryptMojo extends AbstractMojo {

	/** The prefix to use when setting values using -D.... */
	private static final String PROP_PFX = "maven.repocreds.";

	/**
	 * <p>
	 * A bit of overengineering never hurt anyone :D
	 * </p>
	 *
	 * <p>
	 * This class helps make logging more agile by checking if each logging level is enabled before
	 * executing the string formatting and logging call.
	 * </p>
	 *
	 * <p>
	 * It's just a simple wrapper around Maven's Log interface
	 * </p>
	 *
	 * @author diego
	 */
	protected class Logger {
		private final Log log = getLog();

		public void debug(String message, Object... args) {
			if (this.log.isDebugEnabled()) {
				this.log.debug(String.format(message, args));
			}
		}

		public void debug(Throwable thrown, String message, Object... args) {
			if (this.log.isDebugEnabled()) {
				this.log.debug(String.format(message, args), thrown);
			}
		}

		public void info(String message, Object... args) {
			if (this.log.isInfoEnabled()) {
				this.log.info(String.format(message, args));
			}
		}

		public void info(Throwable thrown, String message, Object... args) {
			if (this.log.isInfoEnabled()) {
				this.log.info(String.format(message, args), thrown);
			}
		}

		public void warn(String message, Object... args) {
			if (this.log.isWarnEnabled()) {
				this.log.warn(String.format(message, args));
			}
		}

		public void warn(Throwable thrown, String message, Object... args) {
			if (this.log.isWarnEnabled()) {
				this.log.warn(String.format(message, args), thrown);
			}
		}

		public void error(String message, Object... args) {
			if (this.log.isErrorEnabled()) {
				this.log.error(String.format(message, args));
			}
		}

		public void error(Throwable thrown, String message, Object... args) {
			if (this.log.isErrorEnabled()) {
				this.log.error(String.format(message, args), thrown);
			}
		}
	}

	/**
	 * <p>
	 * This class does the actual work of either writing out the value to a file, or setting the
	 * variable value
	 * </p>
	 *
	 * @author diego
	 */
	protected class ValueExporter {
		private final String fieldName;
		private final Function<Server, String> getter;
		private final Supplier<File> file;
		private final Supplier<String> var;

		private ValueExporter(String fieldName, Function<Server, String> getter, Supplier<File> file,
			Supplier<String> var) {
			this.fieldName = fieldName;
			this.getter = getter;
			this.file = file;
			this.var = var;
		}

		private void exportVariable(Logger log, Server server, String value) throws MojoExecutionException {
			final String target = this.var.get();

			// Do nothing if no value set
			if ((target == null) || (target.length() < 1)) {
				log.debug("No variable name given to store the %s value for server [%s]", this.fieldName,
					server.getId());
				return;
			}

			// Create the variable
			log.info("Storing the %s for server [%s] in the variable [%s]", this.fieldName, server.getId(), target);

			// Not entirely sure this is necessary, but we do it anyway :D
			DecryptMojo.this.session.getTopLevelProject().getProperties().setProperty(target, value);
			for (MavenProject project : DecryptMojo.this.session.getProjectDependencyGraph().getSortedProjects()) {
				getLog().debug("Storing timestamp property in project " + project.getId());
				project.getProperties().setProperty(target, value);
			}
		}

		private void exportFile(Logger log, Server server, String value) throws MojoExecutionException {
			final File target = this.file.get();

			// Do nothing if no value set
			if (target == null) {
				log.debug("No file to store the %s value for server [%s]", this.fieldName, server.getId());
				return;
			}

			final File parentDir = target.getParentFile();
			if (!parentDir.exists()) {
				log.debug("Creating the parent directory [%s]", parentDir);
				parentDir.mkdirs();
			}

			// Write the file
			log.info("Writing out the %s for server [%s] to the file [%s]", this.fieldName, server.getId(), target);
			try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(target)))) {
				w.write(value);
			} catch (IOException e) {
				throw new MojoExecutionException(e.getMessage(), e);
			}
		}

		public void export(Logger log, Server server) throws MojoExecutionException {

			final String value = this.getter.apply(server);
			if (value == null) {
				log.debug("No %s value for server [%s]", this.fieldName, server.getId());
				return;
			}

			exportVariable(log, server, value);
			exportFile(log, server, value);
		}
	}

	/**
	 * <p>
	 * This collection just makes it easy to iterate over all fields and do the deed
	 * </p>
	 */
	private final Collection<ValueExporter> valueExporters = Collections
		.unmodifiableCollection(new LinkedList<ValueExporter>() {
			private static final long serialVersionUID = 1L;
			{
				add(new ValueExporter( //
					"username", //
					Server::getUsername, //
					() -> DecryptMojo.this.usernameFile, //
					() -> DecryptMojo.this.usernameVar //
				));
				add(new ValueExporter( //
					"password", //
					Server::getPassword, //
					() -> DecryptMojo.this.passwordFile, //
					() -> DecryptMojo.this.passwordVar //
				));
				add(new ValueExporter( //
					"private key passphrase", //
					Server::getPassphrase, //
					() -> DecryptMojo.this.passphraseFile, //
					() -> DecryptMojo.this.passphraseVar //
				));
				add(new ValueExporter( //
					"private key", //
					Server::getPrivateKey, //
					() -> DecryptMojo.this.privateKeyFile, //
					() -> DecryptMojo.this.privateKeyVar //
				));
			}
		});

	@Parameter(property = DecryptMojo.PROP_PFX + "skip", required = false, defaultValue = "false")
	private boolean skip;

	@Parameter(property = DecryptMojo.PROP_PFX + "serverId", required = false)
	private String serverId;

	@Parameter(property = DecryptMojo.PROP_PFX + "usernameFile", required = false)
	private File usernameFile;

	@Parameter(property = DecryptMojo.PROP_PFX + "passwordFile", required = false)
	private File passwordFile;

	@Parameter(property = DecryptMojo.PROP_PFX + "passphraseFile", required = false)
	private File passphraseFile;

	@Parameter(property = DecryptMojo.PROP_PFX + "privateKeyFile", required = false)
	private File privateKeyFile;

	@Parameter(property = DecryptMojo.PROP_PFX + "usernameVar", required = false)
	private String usernameVar;

	@Parameter(property = DecryptMojo.PROP_PFX + "passwordVar", required = false)
	private String passwordVar;

	@Parameter(property = DecryptMojo.PROP_PFX + "passphraseVar", required = false)
	private String passphraseVar;

	@Parameter(property = DecryptMojo.PROP_PFX + "privateKeyVar", required = false)
	private String privateKeyVar;

	@Parameter(defaultValue = "${settings}", readonly = true)
	private Settings settings;

	@Component
	private SettingsDecrypter decrypter;

	@Parameter(defaultValue = "${session}", required = true, readonly = true)
	private MavenSession session;

	public boolean isSkip() {
		return this.skip;
	}

	public void setSkip(boolean skip) {
		this.skip = skip;
	}

	public File getUsernameFile() {
		return this.usernameFile;
	}

	public void setUsernameFile(File usernameFile) {
		this.usernameFile = usernameFile;
	}

	public File getPasswordFile() {
		return this.passwordFile;
	}

	public void setPasswordFile(File passwordFile) {
		this.passwordFile = passwordFile;
	}

	public File getPassphraseFile() {
		return this.passphraseFile;
	}

	public void setPassphraseFile(File passphraseFile) {
		this.passphraseFile = passphraseFile;
	}

	public File getPrivateKeyFile() {
		return this.privateKeyFile;
	}

	public void setPrivateKeyFile(File privateKeyFile) {
		this.privateKeyFile = privateKeyFile;
	}

	public String getUsernameVar() {
		return this.usernameVar;
	}

	public void setUsernameVar(String usernameVar) {
		this.usernameVar = usernameVar;
	}

	public String getPasswordVar() {
		return this.passwordVar;
	}

	public void setPasswordVar(String passwordVar) {
		this.passwordVar = passwordVar;
	}

	public String getPassphraseVar() {
		return this.passphraseVar;
	}

	public void setPassphraseVar(String passphraseVar) {
		this.passphraseVar = passphraseVar;
	}

	public String getPrivateKeyVar() {
		return this.privateKeyVar;
	}

	public void setPrivateKeyVar(String privateKeyVar) {
		this.privateKeyVar = privateKeyVar;
	}

	@Override
	public final void execute() throws MojoExecutionException, MojoFailureException {
		final Logger log = new Logger();

		if (this.skip) {
			log.info("Skipping execution.");
			return;
		}

		if (this.serverId == null) {
			log.info("No server ID given, nothing to decrypt");
			return;
		}

		final Server server = this.settings.getServer(this.serverId);
		if (server == null) {
			throw new MojoExecutionException(String.format("No server found with ID [%s]", this.serverId));
		}

		log.debug("Requesting decryption of the settings for server [%s]", this.serverId);
		SettingsDecryptionRequest decryptReq = new DefaultSettingsDecryptionRequest(server);
		SettingsDecryptionResult decryptRes = this.decrypter.decrypt(decryptReq);

		// Get the decrypted server information
		final Server decrypted = decryptRes.getServer();
		for (ValueExporter e : this.valueExporters) {
			e.export(log, decrypted);
		}
	}
}