package net.bhl.matsim.uam.router.strategy;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.facilities.Facility;

import net.bhl.matsim.uam.data.UAMAccessLeg;
import net.bhl.matsim.uam.data.UAMAccessOptions;
import net.bhl.matsim.uam.data.UAMRoute;
import net.bhl.matsim.uam.infrastructure.UAMStation;

/**
 * This strategy is used to assign to the passenger the UAMRoute based on the
 * minimum travel time to access to UAM Station and egress travel time from UAM
 * Station.
 *
 * @author Aitanm (Aitan Militao), RRothfeld (Raoul Rothfeld)
 */
public class UAMMinAccessTravelTimeStrategy implements UAMStrategy {
	private UAMStrategyUtils strategyUtils;

	public UAMMinAccessTravelTimeStrategy(UAMStrategyUtils strategyUtils) {
		this.strategyUtils = strategyUtils;
	}

	@Override
	public UAMStrategyType getUAMStrategyType() {
		return UAMStrategyType.MINACCESSTRAVELTIME;
	}

	@Override
	public Optional<UAMRoute> getRoute(Person person, Facility fromFacility, Facility toFacility,
			double departureTime) {
		UAMStation bestStationOrigin = null, bestStationDestination = null;
		Collection<UAMStation> stationsOrigin = strategyUtils.getPossibleStations(fromFacility, toFacility);
		Collection<UAMStation> stationsDestination = strategyUtils.getPossibleStations(toFacility, fromFacility);
		Map<Id<UAMStation>, UAMAccessOptions> accessRoutesData = strategyUtils.getAccessOptions(true, stationsOrigin,
				fromFacility, departureTime);
		double minAccessTime = Double.POSITIVE_INFINITY;

		for (UAMStation stationOrigin : stationsOrigin) {
			if (accessRoutesData.get(stationOrigin.getId()).getFastestAccessTime() < minAccessTime) {
				bestStationOrigin = stationOrigin;
				minAccessTime = accessRoutesData.get(stationOrigin.getId()).getFastestAccessTime();
			}
		}
		
		if (bestStationOrigin == null) {
			return Optional.empty();
		}
		
		// egress trips
		Set<String> modes = strategyUtils.getModes();
		double minEgressTime = Double.POSITIVE_INFINITY;
		String bestModeEgress = TransportMode.walk;
		for (UAMStation stationDestination : stationsDestination) {
			if (bestStationOrigin == stationDestination)
				continue;
			// fly time between stations
			double flyTime = strategyUtils.getFlightTime(bestStationOrigin, stationDestination);
			// updates departureTime
			double currentDepartureTime = departureTime
					+ accessRoutesData.get(bestStationOrigin.getId()).getFastestAccessTime() + flyTime;
			for (String mode : modes) {
				UAMAccessLeg egressLeg = strategyUtils.estimateAccessLeg(false, toFacility, currentDepartureTime,
						stationDestination, mode);
				if (egressLeg == null)
					continue;
				double egressTravelTime = egressLeg.travelTime;
				if (egressTravelTime < minEgressTime) {
					bestStationDestination = stationDestination;
					minEgressTime = egressTravelTime;
					bestModeEgress = mode;
				}
			}
		}

		if (bestStationDestination == null) {
			return Optional.empty();
		}

		return Optional.of(new UAMRoute(accessRoutesData.get(bestStationOrigin.getId()).getFastestTimeMode(),
				bestStationOrigin, bestStationDestination, bestModeEgress));
	}
}
