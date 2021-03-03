package net.bhl.matsim.uam.analysis.uamroutes.run;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelDisutilityUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;

import net.bhl.matsim.uam.data.UAMFlightLeg;
import net.bhl.matsim.uam.data.UAMStationConnectionGraph;
import net.bhl.matsim.uam.dispatcher.UAMManager;
import net.bhl.matsim.uam.infrastructure.UAMStation;
import net.bhl.matsim.uam.infrastructure.UAMStations;
import net.bhl.matsim.uam.infrastructure.readers.UAMXMLReader;
import net.bhl.matsim.uam.run.UAMConstants;

/**
 * This script generates a CSV file containing the distance, travel time and
 * utility between UAM stations. Necessary inputs are in the following order:
 * -Network file; -UAMVehicles file; -output file;
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class RunCalculateUAMRoutes {

	static final private String delimiter = ",";
	static private Map<Id<UAMStation>, UAMStation> stations;
	static private UAMStationConnectionGraph uamSCG;

	static public void main(String[] args) throws IOException {
		// PROVIDE: NETWORK UAMVEHICLES OUTFILE-NAME
		extract(args[0], args[1], args[2]);
		System.out.println("done.");
	}

	static public void extract(String networkString, String uamVehicles, String outfile) throws IOException {
		Config config = ConfigUtils.createConfig();
		config.network().setInputFile(networkString);
		Network network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(networkString);

		TransportModeNetworkFilter filter = new TransportModeNetworkFilter(network);
		Set<String> modes = new HashSet<>();
		modes.add(UAMConstants.uam);
		Network networkUAM = NetworkUtils.createNetwork();
		filter.filter(networkUAM, modes);

		UAMXMLReader uamReader = new UAMXMLReader(networkUAM);
		uamReader.readFile(uamVehicles);

		stations = uamReader.getStations();
		uamSCG = calculateRoutes(uamReader);
		write(outfile);
	}

	static public UAMStationConnectionGraph calculateRoutes(UAMXMLReader uamReader) {
		TravelTime tt = new FreeSpeedTravelTime();
		TravelDisutility td = TravelDisutilityUtils
				.createFreespeedTravelTimeAndDisutility(ConfigUtils.createConfig().planCalcScore());

		UAMStations uamStations = new UAMStations(uamReader.getStations(), uamReader.network);
		UAMManager uamManager = new UAMManager(uamReader.network, uamStations, uamReader.getVehicles());

		LeastCostPathCalculator pllcp = new DijkstraFactory().createPathCalculator(uamReader.network, td, tt);

		UAMStationConnectionGraph uamSCG = new UAMStationConnectionGraph(uamManager, pllcp);
		return uamSCG;
	}

	private static void write(String outputPath) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath)));
		writer.write(formatHeader() + "\n");

		for (UAMStation stationFrom : stations.values()) {
			for (UAMStation stationTo : stations.values()) {
				if (stationFrom.equals(stationTo))
					continue;

				UAMFlightLeg leg = uamSCG.getFlightLeg(stationFrom.getId(), stationTo.getId());

				writer.write(String.join(delimiter, new String[] { String.valueOf(stationFrom.getId()),
						String.valueOf(stationTo.getId()), String.valueOf(leg.travelTime), leg.distance + "\n" }));
			}
		}

		writer.flush();
		writer.close();
	}

	private static String formatHeader() {
		return String.join(delimiter,
				new String[] { "station_from", "station_to", "travel_time_s", "travel_distance_m" });
	}
}
