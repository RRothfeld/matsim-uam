package net.bhl.matsim.uam.router.strategy;

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
 * minimum travel distance of access to UAM Station and egress from UAM Station.
 *
 * @author Aitanm (Aitan Militao), RRothfeld (Raoul Rothfeld)
 */
public class UAMMinAccessDistanceStrategy implements UAMStrategy {
	private UAMStrategyUtils strategyUtils;

	public UAMMinAccessDistanceStrategy(UAMStrategyUtils strategyUtils) {
		this.strategyUtils = strategyUtils;
	}

	@Override
	public UAMStrategyType getUAMStrategyType() {
		return UAMStrategyType.MINACCESSDISTANCE;
	}

	@Override
	public Optional<UAMRoute> getRoute(Person person, Facility fromFacility, Facility toFacility, double departureTime) {
		UAMStation bestStationOrigin = null, bestStationDestination = null;
		Collection<UAMStation> stationsOrigin = strategyUtils.getPossibleStations(fromFacility, toFacility);
		Collection<UAMStation> stationsDestination = strategyUtils.getPossibleStations(toFacility, fromFacility);
		Map<Id<UAMStation>, UAMAccessOptions> accessRoutesData = strategyUtils.getAccessOptions(true,
				stationsOrigin, fromFacility, departureTime);
		//access trips
		double minAccessDistance = Double.POSITIVE_INFINITY;
		for (UAMStation stationOrigin : stationsOrigin) {
			if (accessRoutesData.get(stationOrigin.getId()).getShortestAccessDistance() < minAccessDistance) {
				bestStationOrigin = stationOrigin;
				minAccessDistance = accessRoutesData.get(stationOrigin.getId()).getShortestAccessDistance();
			}
		}

		//egress trips
		String bestModeEgress = TransportMode.walk;
		Set<String> modes = strategyUtils.getModes();
		double minEgressDistance = Double.POSITIVE_INFINITY;
		for (UAMStation stationDestination : stationsDestination) {
			if (bestStationOrigin == stationDestination)
				continue;

			//fly time between stations
			double flyTime = strategyUtils.getFlightTime(bestStationOrigin, stationDestination);
			//updates departureTime 
			double currentDepartureTime = departureTime + accessRoutesData.get(bestStationOrigin.getId()).getFastestAccessTime() + flyTime;
			for (String mode : modes) {
				//Calculates the distance for the egress routes using updated departureTime
				double egressDistance = strategyUtils.estimateAccessLeg(false, toFacility, currentDepartureTime,
						stationDestination, mode).distance;

				if (egressDistance < minEgressDistance) {
					bestStationDestination = stationDestination;
					minEgressDistance = egressDistance;
					bestModeEgress = mode;
				}
			}
		}
		
		// TODO: What if non is found? Should return Optional.empty();

		return Optional.of(new UAMRoute(accessRoutesData.get(bestStationOrigin.getId()).getShortestDistanceMode(), bestStationOrigin,
				bestStationDestination, bestModeEgress));
	}
}
