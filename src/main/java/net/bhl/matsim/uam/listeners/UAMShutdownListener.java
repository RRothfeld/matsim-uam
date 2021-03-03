package net.bhl.matsim.uam.listeners;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.utils.io.IOUtils;

import com.google.inject.Inject;

import net.bhl.matsim.uam.config.UAMConfigGroup;

/**
 * This listener copies the input UAM Vehicle file into the output folder after
 * the simulation ends
 *
 * @author Aitanm (Aitan Militao), RRothfeld (Raoul Rothfeld)
 */

public class UAMShutdownListener implements ShutdownListener {
	private static final Logger log = Logger.getLogger(UAMListener.class);

	@Inject
	private OutputDirectoryHierarchy controlerIO;

	@Inject
	private Config config;

	@Inject
	private UAMConfigGroup uamConfig;

	@Override
	public void notifyShutdown(ShutdownEvent event) {
		writeUAMVehiclesFile(event);
	}

	private void writeUAMVehiclesFile(ShutdownEvent event) {
		try {
			InputStream fromStream = IOUtils
					.getInputStream(ConfigGroup.getInputFileURL(config.getContext(), uamConfig.getInputFile()));
			OutputStream toStream = IOUtils.getOutputStream(
					new File(controlerIO.getOutputFilename("output_uam_vehicles.xml.gz")).toURI().toURL(), false);
			IOUtils.copyStream(fromStream, toStream);

			fromStream.close();
			toStream.close();
		} catch (IOException e) {
			log.warn("writing output UAM Vehicles did not work; probably parameters were such that no events were "
					+ "generated in the final iteration");
		}
	}
}
