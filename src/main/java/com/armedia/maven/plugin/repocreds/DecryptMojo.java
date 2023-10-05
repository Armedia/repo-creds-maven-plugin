package com.armedia.maven.plugin.repocreds;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Base64;
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
	protected class ValueHandler {
		private final String fieldName;
		private final Function<Server, String> getter;
		private final Supplier<File> file;
		private final Supplier<String> var;

		private ValueHandler(String fieldName, Function<Server, String> getter, Supplier<File> file,
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

	private String renderAuthCombo(Server server) {
		if (server == null) { return null; }
		String username = server.getUsername();
		if (username == null) {
			username = "";
		}
		String password = server.getPassword();
		if (password == null) {
			password = "";
		}

		Charset charset = Charset.defaultCharset();
		String encoding = this.authComboEncoding;
		if ((encoding == null) || (encoding.trim().length() == 0)) {
			charset = Charset.forName(encoding);
		}
		String auth = String.format("%s:%s", username, password);
		return Base64.getEncoder().encodeToString(auth.getBytes(charset));
	}

	/**
	 * <p>
	 * This collection just makes it easy to iterate over all fields and do the deed
	 * </p>
	 */
	private final Collection<ValueHandler> valueHandlers = Collections
		.unmodifiableCollection(new LinkedList<ValueHandler>() {
			private static final long serialVersionUID = 1L;
			{
				add(new ValueHandler( //
					"username", //
					Server::getUsername, //
					() -> DecryptMojo.this.usernameFile, //
					() -> DecryptMojo.this.usernameVar //
				));
				add(new ValueHandler( //
					"password", //
					Server::getPassword, //
					() -> DecryptMojo.this.passwordFile, //
					() -> DecryptMojo.this.passwordVar //
				));
				add(new ValueHandler( //
					"private key passphrase", //
					Server::getPassphrase, //
					() -> DecryptMojo.this.passphraseFile, //
					() -> DecryptMojo.this.passphraseVar //
				));
				add(new ValueHandler( //
					"private key", //
					Server::getPrivateKey, //
					() -> DecryptMojo.this.privateKeyFile, //
					() -> DecryptMojo.this.privateKeyVar //
				));
				add(new ValueHandler( //
					"user-pass auth combo", //
					DecryptMojo.this::renderAuthCombo, //
					() -> DecryptMojo.this.authComboFile, //
					() -> DecryptMojo.this.authComboVar //
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

	@Parameter(property = DecryptMojo.PROP_PFX + "authComboFile", required = false)
	private File authComboFile;

	@Parameter(property = DecryptMojo.PROP_PFX + "usernameVar", required = false)
	private String usernameVar;

	@Parameter(property = DecryptMojo.PROP_PFX + "passwordVar", required = false)
	private String passwordVar;

	@Parameter(property = DecryptMojo.PROP_PFX + "passphraseVar", required = false)
	private String passphraseVar;

	@Parameter(property = DecryptMojo.PROP_PFX + "privateKeyVar", required = false)
	private String privateKeyVar;

	@Parameter(property = DecryptMojo.PROP_PFX + "authComboEncoding", required = false)
	private String authComboEncoding;

	@Parameter(property = DecryptMojo.PROP_PFX + "authComboVar", required = false)
	private String authComboVar;

	@Parameter(defaultValue = "${settings}", readonly = true)
	private Settings settings;

	@Component
	private SettingsDecrypter decrypter;

	@Parameter(defaultValue = "${session}", required = true, readonly = true)
	private MavenSession session;

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
		for (ValueHandler h : this.valueHandlers) {
			h.export(log, decrypted);
		}
	}
}