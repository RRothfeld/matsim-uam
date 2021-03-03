package net.bhl.matsim.uam.analysis.uamdemand.run;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.MainModeIdentifierImpl;

import net.bhl.matsim.uam.analysis.uamdemand.CSVUAMDemandWriter;
import net.bhl.matsim.uam.analysis.uamdemand.UAMDemandItem;
import net.bhl.matsim.uam.analysis.uamdemand.listeners.UAMListener;
import net.bhl.matsim.uam.analysis.uamdemand.readers.EventsUAMReader;
import net.bhl.matsim.uam.router.UAMMainModeIdentifier;
import net.bhl.matsim.uam.run.UAMConstants;

/**
 * This script generates a UAMDemand csv file containing UAMDemand data.
 * Necessary inputs are in the following order: -Network file; -Events file;
 * -UAMConfig file; -output file;
 *
 * @author Aitanm (Aitan Militao), RRothfeld (Raoul Rothfeld)
 */

public class ConvertUAMDemandFromEvents {
	static public void main(String[] args) throws IOException {
		// PROVIDE: NETWORK EVENTS UAMVEHICLES OUTFILE-NAME
		extract(args[0], args[1], args[2], args[3]);
		System.out.println("done.");
	}

	static public void extract(String network, String events, String uamVehicles, String outfile) throws IOException {
		Network netw = NetworkUtils.createNetwork();
		new MatsimNetworkReader(netw).readFile(network);

		MainModeIdentifier mainModeIdentifier = new UAMMainModeIdentifier(new MainModeIdentifierImpl());
		Collection<String> networkRouteModes = Arrays.asList(TransportMode.car, UAMConstants.uam,
				UAMConstants.access + TransportMode.car, UAMConstants.egress + TransportMode.car);

		UAMListener uamListener = new UAMListener(netw, uamVehicles, mainModeIdentifier,
				networkRouteModes);
		Collection<UAMDemandItem> uamData = new EventsUAMReader(uamListener).readUAMData(events);

		new CSVUAMDemandWriter(uamData).write(outfile);
	}
}