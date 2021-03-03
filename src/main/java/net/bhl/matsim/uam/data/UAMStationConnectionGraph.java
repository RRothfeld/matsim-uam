package net.bhl.matsim.uam.data;

import static java.lang.Math.min;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;

import com.google.inject.name.Named;

import net.bhl.matsim.uam.dispatcher.UAMManager;
import net.bhl.matsim.uam.infrastructure.UAMStation;
import net.bhl.matsim.uam.router.UAMFlightSegments;
import net.bhl.matsim.uam.run.UAMConstants;

/**
 * Class that stores information about UAMStations, such as distances, travel
 * times and utilities between stations.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMStationConnectionGraph {

	final private static Logger log = Logger.getLogger(UAMStationConnectionGraph.class);
	private Map<Id<UAMStation>, Map<Id<UAMStation>, UAMFlightLeg>> legs;

	public UAMStationConnectionGraph(UAMManager uamManager,
			@Named(UAMConstants.uam) LeastCostPathCalculator uamPathCalculator) {
		log.info("Calculating travel times and distances between all UAM stations.");

		legs = new HashMap<>();

		// TODO: For now it assumes there only being one singular UAM vehicle type,
		// enhancing this would be part of future work.
		double horizontalSpeed = uamManager.getVehicles().entrySet().iterator().next().getValue().getCruiseSpeed();
		double verticalSpeed = uamManager.getVehicles().entrySet().iterator().next().getValue().getVerticalSpeed();

		for (UAMStation uamStationOrigin : uamManager.getStations().getUAMStations().values()) {
			for (UAMStation uamStationDestination : uamManager.getStations().getUAMStations().values()) {
				if (uamStationOrigin == uamStationDestination)
					continue;

				Path path = uamPathCalculator.calcLeastCostPath(uamStationOrigin.getLocationLink().getFromNode(),
						uamStationDestination.getLocationLink().getToNode(), 0.0, null, null);

				if (path == null) {
					throw new IllegalStateException();
				}

				double distance = 0;
				double travelTime = 0;

				for (Link link : path.links) {
					distance += link.getLength();

					String flightSegment = (String) link.getAttributes().getAttribute(UAMFlightSegments.ATTRIBUTE);

					if (flightSegment == null)
						log.error(UAMConstants.uam.toUpperCase()
								+ " links within the MATSim network do not provide the \"type\" attribute, defining the flight segment.");

					if (flightSegment.equals(UAMFlightSegments.HORIZONTAL))
						travelTime += link.getLength() / min(horizontalSpeed, link.getFreespeed());

					if (flightSegment.equals(UAMFlightSegments.VERTICAL))
						travelTime += link.getLength() / min(verticalSpeed, link.getFreespeed());
				}

				if (legs.containsKey(uamStationOrigin.getId())) {
					this.legs.get(uamStationOrigin.getId()).put(uamStationDestination.getId(),
							new UAMFlightLeg(travelTime, distance, path.links));
				} else {
					Map<Id<UAMStation>, UAMFlightLeg> newEntry = new HashMap<>();
					newEntry.put(uamStationDestination.getId(), new UAMFlightLeg(travelTime, distance, path.links));
					this.legs.put(uamStationOrigin.getId(), newEntry);
				}
			}
		}
	}

	public UAMFlightLeg getFlightLeg(Id<UAMStation> originStation, Id<UAMStation> destinationStation) {
		return this.legs.get(originStation).get(destinationStation);
	}
}
