package net.bhl.matsim.uam.router.strategy;

import net.bhl.matsim.uam.data.UAMAccessLeg;
import net.bhl.matsim.uam.data.UAMAccessOptions;
import net.bhl.matsim.uam.data.UAMRoute;
import net.bhl.matsim.uam.infrastructure.UAMStation;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.facilities.Facility;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * This strategy is used to assign to the passenger a UAMRoute based on the
 * minimum travel distance of the route.
 *
 * @author Aitanm (Aitan Militao), RRothfeld (Raoul Rothfeld)
 */
public class UAMMinDistanceStrategy implements UAMStrategy {
	private UAMStrategyUtils strategyUtils;

	public UAMMinDistanceStrategy(UAMStrategyUtils strategyUtils) {
		this.strategyUtils = strategyUtils;
	}

	@Override
	public UAMStrategyType getUAMStrategyType() {
		return UAMStrategyType.MINDISTANCE;
	}

	@Override
	public Optional<UAMRoute> getRoute(Person person, Facility fromFacility, Facility toFacility, double departureTime) {
		UAMStation bestStationOrigin = null, bestStationDestination = null;
		Collection<UAMStation> stationsOrigin = strategyUtils.getPossibleStations(fromFacility, toFacility);
		Collection<UAMStation> stationsDestination = strategyUtils.getPossibleStations(toFacility, fromFacility);
		Map<Id<UAMStation>, UAMAccessOptions> accessRoutesData = strategyUtils.getAccessOptions(true,
				stationsOrigin, fromFacility, departureTime);
		String bestModeEgress = TransportMode.walk;

		Set<String> modes = strategyUtils.getModes();
		//UAM Flight Distance + access and egress distance
		double minTotalDistance = Double.POSITIVE_INFINITY;
		for (UAMStation stationOrigin : stationsOrigin) {
			for (UAMStation stationDestination : stationsDestination) {
				if (stationOrigin == stationDestination)
					continue;
				//collects the distance between stations and stores it
				double flyDistance = strategyUtils.getFlightDistance(stationOrigin, stationDestination);

				//fly time between stations
				double flyTime = strategyUtils.getFlightTime(stationOrigin, stationDestination);
				double accessTime = strategyUtils.estimateAccessLeg(true, fromFacility, departureTime,
						stationOrigin, accessRoutesData.get(stationOrigin.getId()).getShortestDistanceMode()).travelTime;
				//updates departureTime 
				double currentDepartureTime = departureTime + accessTime + flyTime;
				//Calculates the shortest path
				for (String mode : modes) {
					//Calculates the distance for the egress routes using updated departureTime
					UAMAccessLeg egressLeg = strategyUtils.estimateAccessLeg(false, toFacility, currentDepartureTime,
							stationDestination, mode);
					if (egressLeg == null)
						continue;
					double egressDistance = egressLeg.distance;
					double totalDistance = accessRoutesData.get(stationOrigin.getId()).getShortestAccessDistance() + flyDistance + egressDistance;
					if (totalDistance < minTotalDistance) {
						minTotalDistance = totalDistance;
						bestStationOrigin = stationOrigin;
						bestStationDestination = stationDestination;
						bestModeEgress = mode;
					}
				}
			}
		}
		
		// TODO: What if non is found? Should return Optional.empty();

		return Optional.of(new UAMRoute(accessRoutesData.get(bestStationOrigin.getId()).getShortestDistanceMode(), bestStationOrigin,
				bestStationDestination, bestModeEgress));
	}
}
