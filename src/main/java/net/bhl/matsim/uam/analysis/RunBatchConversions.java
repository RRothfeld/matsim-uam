package net.bhl.matsim.uam.analysis;

import java.io.IOException;

import net.bhl.matsim.uam.analysis.traffic.run.BatchConvertLinkStatsFromEvents;
import net.bhl.matsim.uam.analysis.trips.run.BatchConvertTripsFromEvents;
import net.bhl.matsim.uam.analysis.trips.run.BatchConvertTripsFromPopulation;
import net.bhl.matsim.uam.analysis.uamdemand.run.BatchConvertUAMDemandFromEvents;
import net.bhl.matsim.uam.analysis.uamstations.run.BatchConvertUAMStationsFromUAMVehicles;

/**
 * This script takes a specific folder path and runs
 * {@link BatchConvertUAMStationsFromUAMVehicles},
 * {@link BatchConvertUAMDemandFromEvents}, {@link BatchConvertTripsFromEvents},
 * {@link BatchConvertTransitTripsFromEvents},
 * {@link BatchConvertTripsFromPopulation} and
 * {@link BatchConvertLinkStatsFromEvents} consecutively for all MATSim output
 * folders within the provided base folder.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class RunBatchConversions {

	public static void main(String[] args) throws IOException {
		// PROVIDE PARENT FOLDER OF OUTPUT FOLDERS
		BatchConvertUAMStationsFromUAMVehicles.main(args);
		BatchConvertUAMDemandFromEvents.main(args);
		BatchConvertTripsFromEvents.main(args);
		BatchConvertTripsFromPopulation.main(args);
		BatchConvertLinkStatsFromEvents.main(args);
	}

}
