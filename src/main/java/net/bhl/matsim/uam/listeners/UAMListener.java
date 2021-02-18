package net.bhl.matsim.uam.listeners;

import com.google.inject.Inject;
import net.bhl.matsim.uam.events.UAMData;
import net.bhl.matsim.uam.events.UAMDemand;
import net.bhl.matsim.uam.run.UAMConstants;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/**
 * This class provides output for the uam usage.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMListener implements IterationEndsListener {

	@Inject
	UAMDemand uamDemand;

	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		// UAMDemand
		Map<Id<Person>, ArrayList<UAMData>> data = this.uamDemand.getDemand();
		BufferedWriter writer = IOUtils.getBufferedWriter(event.getServices().getControlerIO().getIterationFilename(event.getIteration(),
				UAMConstants.uam + "demand.csv"));

		try {
			writer.write("peronId,originCoordX,originCoordY,originStationCoordX,originStationCoordY,destinationStationCoordX,"
					+ "destinationStationCoordY,destinationCoordX,destinationCoordY,startTime,arrivalAtStationTime,takeOffTime,"
					+ "landingTime,departureFromStationTime,endTime,vehicleId,originStationId,destinationStationId,accessMode,"
					+ "egressMode,uamTrip");
			writer.newLine();
			for (Id<Person> personId : data.keySet()) {
				for (UAMData d : data.get(personId)) {
					writer.write(personId.toString() + ",");
					writer.write(d.toString());
					writer.newLine();
				}
			}
			writer.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
