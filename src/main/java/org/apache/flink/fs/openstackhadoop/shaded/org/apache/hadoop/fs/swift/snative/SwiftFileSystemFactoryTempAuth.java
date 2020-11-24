package org.apache.flink.fs.openstackhadoop.shaded.org.apache.hadoop.fs.swift.snative;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.ConfigurationUtils;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.core.fs.FileSystemFactory;
import org.apache.flink.fs.openstackhadoop.shaded.org.apache.flink.runtime.fs.hdfs.HadoopFileSystem;
import org.apache.flink.fs.openstackhadoop.shaded.org.apache.flink.runtime.util.HadoopUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple factory for the Swift file system.
 */
@SuppressWarnings("all") // Copied class
public class SwiftFileSystemFactoryTempAuth implements FileSystemFactory {

	private static final Logger LOG = LoggerFactory.getLogger(SwiftFileSystemFactoryTempAuth.class);

	/** The prefixes that Flink adds to the Hadoop config under 'fs.swift.'. */
	private static final String CONFIG_PREFIX = "swift.";

	/** Flink's configuration object. */
	private Configuration flinkConfig;

	/** Hadoop's configuration for the file systems, lazily initialized. */
	private org.apache.flink.fs.openstackhadoop.shaded.org.apache.hadoop.conf.Configuration hadoopConfig;

	@Override
	public String getScheme() {
		return "swift";
	}

	@Override
	public void configure(Configuration config) {
		flinkConfig = config;
		hadoopConfig = null;
	}

	@Override
	public FileSystem create(URI fsUri) throws IOException {
		LOG.debug("Creating swift file system (backed by a Hadoop native swift file system)");

		try {
			// -- (1) get the loaded Hadoop config (or fall back to one loaded from the classpath)

			org.apache.flink.fs.openstackhadoop.shaded.org.apache.hadoop.conf.Configuration hadoopConfig = this.hadoopConfig;
			if (hadoopConfig == null) {
				if (flinkConfig != null) {
					LOG.debug("Loading Hadoop configuration for swift native file system");
					hadoopConfig = HadoopUtils.getHadoopConfiguration(flinkConfig);

					// hadoop.tmp.dir needs to be defined because it is used as buffer directory
					if (hadoopConfig.get("hadoop.tmp.dir") == null) {
						String[] tmpDirPaths = ConfigurationUtils.parseTempDirectories(flinkConfig);
						File tmpDir = new File(tmpDirPaths[0], "hadoop-" + System.getProperty("user.name"));
						hadoopConfig.set("hadoop.tmp.dir", tmpDir.getPath());
					}

					// add additional config entries from the Flink config to the Hadoop config
					for (String key : flinkConfig.keySet()) {
						if (key.startsWith(CONFIG_PREFIX)) {
							String value = flinkConfig.getString(key, null);
							String newKey = "fs.swift." + key.substring(CONFIG_PREFIX.length());
							hadoopConfig.set(newKey, value);

							LOG.debug("Adding Flink config entry for {} as {}={} to Hadoop config for " +
									"Swift native File System", key, newKey, value);
						}
					}

					this.hadoopConfig = hadoopConfig;
				}
				else {
					LOG.warn("The factory has not been configured prior to loading the Swift native file system."
							+ " Using Hadoop configuration from the classpath.");

					hadoopConfig = new org.apache.flink.fs.openstackhadoop.shaded.org.apache.hadoop.conf.Configuration();
					this.hadoopConfig = hadoopConfig;
				}
			}

			// -- (2) Instantiate the Hadoop file system class for that scheme

			final String scheme = fsUri.getScheme();
			final String authority = fsUri.getAuthority();

			if (scheme == null && authority == null) {
				fsUri = org.apache.flink.fs.openstackhadoop.shaded.org.apache.hadoop.fs.FileSystem.getDefaultUri(hadoopConfig);
			}
			else if (scheme != null && authority == null) {
				URI defaultUri = org.apache.flink.fs.openstackhadoop.shaded.org.apache.hadoop.fs.FileSystem.getDefaultUri(hadoopConfig);
				if (scheme.equals(defaultUri.getScheme()) && defaultUri.getAuthority() != null) {
					fsUri = defaultUri;
				}
			}

			LOG.debug("Using scheme {} for swift file system backing the Swift Native File System", fsUri);

			final SwiftNativeFileSystem fs = new SwiftNativeFileSystem(new SwiftNativeFileSystemStoreTempAuth());
			fs.initialize(fsUri, hadoopConfig);

			return new HadoopFileSystem(fs);
		}
		catch (IOException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IOException(e.getMessage(), e);
		}
	}
}
