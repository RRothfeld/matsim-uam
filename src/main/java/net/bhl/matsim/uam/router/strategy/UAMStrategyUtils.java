package net.bhl.matsim.uam.router.strategy;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.Facility;
import org.matsim.pt.router.TransitRouter;

import net.bhl.matsim.uam.config.UAMConfigGroup;
import net.bhl.matsim.uam.data.UAMAccessLeg;
import net.bhl.matsim.uam.data.UAMAccessOptions;
import net.bhl.matsim.uam.data.UAMStationConnectionGraph;
import net.bhl.matsim.uam.infrastructure.UAMStation;
import net.bhl.matsim.uam.infrastructure.UAMStations;

/**
 * This class provides the methods used for different UAMStrategies.
 *
 * @author Aitanm (Aitan Militao), RRothfeld (Raoul Rothfeld)
 */
public class UAMStrategyUtils {
	private static final Logger log = Logger.getLogger(UAMStrategyUtils.class);
	private final Scenario scenario;
	private UAMStations landingStations;
	private UAMConfigGroup uamConfig;
	private UAMStationConnectionGraph stationConnections;
	private Network carNetwork;
	private TransitRouter transitRouter;
	private LeastCostPathCalculator plcpccar;

	public UAMStrategyUtils(UAMStations landingStations, UAMConfigGroup uamConfig, Scenario scenario,
							UAMStationConnectionGraph stationConnections, Network carNetwork, TransitRouter transitRouter,
							LeastCostPathCalculator plcpccar) {
		this.landingStations = landingStations;
		this.uamConfig = uamConfig;
		this.scenario = scenario;
		this.stationConnections = stationConnections;
		this.carNetwork = carNetwork;
		this.transitRouter = transitRouter;
		this.plcpccar = plcpccar;
	}

	/**
	 * @return the list of possible UAMStations accessible from/to a specific
	 * location (facility) based in the searchRadius parameter defined in
	 * the config file.
	 */
	Collection<UAMStation> getPossibleStations(Facility fromFacility) {
		if (uamConfig.getUseDynamicSearchRadius())
			log.error("Cannot get possible stations only with fromFacility when using non-static search radius.");

		return getPossibleStations(fromFacility, null);
	}

	/**
	 * @return the list of possible UAMStations accessible from/to a specific
	 * location (facility) based in the searchRadius parameter defined in
	 * the config file and the beeline distance.
	 */
	Collection<UAMStation> getPossibleStations(Facility fromFacility, Facility toFacility) {
		double radius = uamConfig.getSearchRadius();

		if (uamConfig.getUseDynamicSearchRadius())
			radius *= CoordUtils.calcEuclideanDistance(fromFacility.getCoord(), toFacility.getCoord());

		return landingStations.getUAMStationsInRadius(fromFacility.getCoord(), radius);
	}

	/**
	 * @param access   true if access leg, false for egress legs.
	 * @param stations list of possible stations to be checked
	 * @param facility the origin of the trip (if access leg); the trip final
	 *                 destination (if egress leg).
	 * @return a map with station Id's as keys and UAMAccessRouteData as values.
	 */
	Map<Id<UAMStation>, UAMAccessOptions> getAccessOptions(boolean access, Collection<UAMStation> stations,
														   Facility facility, double departureTime) {
		Set<String> modes = new HashSet<>(uamConfig.getAccessEgressModes());
		Map<Id<UAMStation>, UAMAccessOptions> accessRouteData = new HashMap<>();

		for (UAMStation station : stations) {
			String bestModeDistance = null;
			String bestModeTime = null;

			double minDistance = Double.POSITIVE_INFINITY;
			double minAccessTime = Double.POSITIVE_INFINITY;

			for (String mode : modes) {
				UAMAccessLeg uamAccessLeg = estimateAccessLeg(access, facility, departureTime, station, mode);
				double distance = uamAccessLeg.distance;
				double travelTime = uamAccessLeg.travelTime;

				// TODO: Does this make sense?
				if (distance <= uamConfig.getWalkDistance() && modes.contains(TransportMode.walk))
					continue;

				if (distance < minDistance) {
					minDistance = distance;
					bestModeDistance = mode;
				}

				if (travelTime < minAccessTime) {
					minAccessTime = travelTime;
					bestModeTime = mode;
				}
			}

			if (bestModeDistance != null)
				accessRouteData.put(station.getId(), new UAMAccessOptions(minDistance, minAccessTime,
						bestModeDistance, bestModeTime, station));
		}
		return accessRouteData;
	}

	UAMAccessLeg estimateAccessLeg(boolean access, Facility facility, double time, UAMStation station, String mode) {
		Network network = scenario.getNetwork();
		Link from, to;
		if (access) {
			from = network.getLinks().get(facility.getLinkId());
			to = station.getLocationLink();
		} else {
			from = station.getLocationLink();
			to = network.getLinks().get(facility.getLinkId());
		}

		switch (mode) {
			case TransportMode.car:
				if (carNetwork.getLinks().get(from.getId()) != null)
					from = carNetwork.getLinks().get(from.getId());
				else
					from = NetworkUtils.getNearestLinkExactly(carNetwork, from.getCoord());

				if (carNetwork.getLinks().get(to.getId()) != null)
					to = carNetwork.getLinks().get(to.getId());
				else
					to = NetworkUtils.getNearestLinkExactly(carNetwork, to.getCoord());

				Path path = plcpccar.calcLeastCostPath(from.getFromNode(), to.getToNode(), time, null, null);

				double distanceByCar = 0.0;
				for (Link l : path.links) {
					distanceByCar += l.getLength();
				}
				return new UAMAccessLeg(path.travelTime, distanceByCar, path.links);
			case TransportMode.pt:
				if (transitRouter != null) {
					List<Leg> legs = transitRouter.calcRoute(new LinkWrapperFacility(from), new LinkWrapperFacility(to),
							time, null);
					double distanceByPt = 0.0;
					double timeByPt = 0.0;
					for (Leg leg : legs) {
						timeByPt += leg.getTravelTime().seconds();
						distanceByPt += leg.getRoute().getDistance();
					}
					return new UAMAccessLeg(timeByPt, distanceByPt, null);
				}
			default:
				return getBeelineAccessLeg(facility, station, mode);
		}
	}

	private UAMAccessLeg getBeelineAccessLeg(Facility facility, UAMStation station, String mode) {
		double distanceFactor = ((PlansCalcRouteConfigGroup) scenario.getConfig().getModules().get("planscalcroute"))
				.getBeelineDistanceFactors().get(mode);
		double distance = CoordUtils.calcEuclideanDistance(
				scenario.getNetwork().getLinks().get(facility.getLinkId()).getCoord(),
				station.getLocationLink().getCoord());
		double speed = ((PlansCalcRouteConfigGroup) scenario.getConfig().getModules().get("planscalcroute"))
				.getTeleportedModeSpeeds().get(mode);

		return new UAMAccessLeg(distance * distanceFactor / speed,
				distance * distanceFactor, null);
	}

	Network getNetwork() {
		return scenario.getNetwork();
	}

	Set<String> getModes() {
		return uamConfig.getAccessEgressModes();
	}

	UAMStationConnectionGraph getStationConnections() {
		return stationConnections;
	}

	public double getAccessTime(Facility fromFacility, Double time, UAMStation departureStation, String mode) {
		return estimateAccessLeg(true, fromFacility, time, departureStation, mode).travelTime;
	}

	public double getFlightTime(UAMStation originStation, UAMStation destinationStation) {
		return stationConnections.getFlightLeg(originStation.getId(), destinationStation.getId()).travelTime;
	}

	public double getFlightDistance(UAMStation originStation, UAMStation destinationStation) {
		return stationConnections.getFlightLeg(originStation.getId(), destinationStation.getId()).distance;
	}

	public double getEgressTime(Facility toFacility, Double time, UAMStation destinationStation, String mode) {
		return estimateAccessLeg(false, toFacility, time, destinationStation, mode).travelTime;
	}
}
