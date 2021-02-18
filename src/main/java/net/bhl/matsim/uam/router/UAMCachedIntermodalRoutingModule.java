package net.bhl.matsim.uam.router;

import ch.ethz.matsim.av.plcpc.ParallelLeastCostPathCalculator;
import ch.ethz.matsim.baseline_scenario.transit.routing.BaselineTransitRoutingModule;
import net.bhl.matsim.uam.config.UAMConfigGroup;
import net.bhl.matsim.uam.data.*;
import net.bhl.matsim.uam.infrastructure.UAMStations;
import net.bhl.matsim.uam.router.strategy.UAMStrategyRouter;
import net.bhl.matsim.uam.run.UAMConstants;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteFactories;
import org.matsim.core.router.*;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.Facility;
import org.matsim.pt.config.TransitConfigGroup;
import org.matsim.pt.router.TransitRouter;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides the route legs for a trip using UAM from pre-calculated UAM legs.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMCachedIntermodalRoutingModule implements RoutingModule {

	private static final Logger log = Logger.getLogger(UAMCachedIntermodalRoutingModule.class);
	private final int counterLimit = 10;
	private final Scenario scenario;
	private int counterWarningWaitingTimeSlot = 0;
	private int counterWarningWaitingTimeNull = 0;
	private int counterWarningConvertedToWalk = 0;
	private LeastCostPathCalculator plcpccar;
	private Network carNetwork;
	private BaselineTransitRoutingModule transitRouterDelegate;
	private WaitingStationData waitingData;
	private UAMStationConnectionGraph stationConnections;
	private UAMStrategyRouter strategyRouter;

	// constructor used in case of simulating PT
	public UAMCachedIntermodalRoutingModule(Scenario scenario, UAMStations landingStations,
											ParallelLeastCostPathCalculator plcpc, LeastCostPathCalculator plcpccar,
											Network carNetwork, TransitRouter transitRouter, UAMConfigGroup uamConfig,
											TransitConfigGroup transitConfigGroup,
											BaselineTransitRoutingModule transitRouterDelegate,
											WaitingStationData waitingData,
											UAMStationConnectionGraph stationConnections) {
		this(scenario, landingStations, plcpc, plcpccar, carNetwork, uamConfig, transitConfigGroup,
				waitingData, stationConnections);
		this.transitRouterDelegate = transitRouterDelegate;
		this.strategyRouter = new UAMStrategyRouter(transitRouter, scenario, uamConfig, plcpc, plcpccar,
				landingStations, carNetwork, stationConnections);

	}

	public UAMCachedIntermodalRoutingModule(Scenario scenario, UAMStations landingStations,
											ParallelLeastCostPathCalculator plcpc, LeastCostPathCalculator plcpccar, Network carNetwork,
											UAMConfigGroup uamConfig, TransitConfigGroup transitConfigGroup,
											WaitingStationData waitingData, UAMStationConnectionGraph stationConnections) {
		this.scenario = scenario;
		this.plcpccar = plcpccar;
		this.carNetwork = carNetwork;
		this.waitingData = waitingData;
		this.stationConnections = stationConnections;

		this.strategyRouter = new UAMStrategyRouter(scenario, uamConfig, plcpc, plcpccar, landingStations,
				carNetwork, stationConnections);
	}

	@Override
	public List<? extends PlanElement> calcRoute(Facility fromFacility, Facility toFacility, double departureTime,
												 Person person) {
		Network network = scenario.getNetwork();

		PopulationFactory populationFactory = scenario.getPopulation().getFactory();
		RouteFactories routeFactory = populationFactory.getRouteFactories();
		final List<PlanElement> trip = new ArrayList<>();

		UAMRoute uamRoute = UAMRoutes.getInstance().get(person.getId(), departureTime);
		if (uamRoute == null)
			uamRoute = strategyRouter.estimateUAMRoute(person, fromFacility, toFacility, departureTime);

		if (uamRoute == null) {
			if (counterWarningConvertedToWalk < counterLimit)
				log.warn("No UAM prediction for person: " + person.getId() + " at time: " + departureTime
						+ " could be calculated. Trip has been converted to walk.");

			if (counterWarningConvertedToWalk == counterLimit - 1)
				log.warn("No more UAM prediction warnings will be reported.");

			counterWarningConvertedToWalk++;
			Leg l = createTeleportationLeg(routeFactory, populationFactory,
					network.getLinks().get(fromFacility.getLinkId()),
					network.getLinks().get(toFacility.getLinkId()), TransportMode.walk, TransportMode.walk);
			trip.add(l);
			return trip;
		}

		/* access trip */
		double currentTime = departureTime;

		switch (uamRoute.accessMode) {
			case TransportMode.car:
				Link accessOriginLink = carNetwork.getLinks().get(fromFacility.getLinkId());
				if (accessOriginLink == null)
					accessOriginLink = NetworkUtils.getNearestLinkExactly(carNetwork, fromFacility.getCoord());
				Link accessDestinationLink = carNetwork.getLinks()
						.get(uamRoute.bestOriginStation.getLocationLink().getId());
				Leg carLeg = createCarLeg(UAMConstants.access + TransportMode.car, accessOriginLink,
						accessDestinationLink, departureTime, person, routeFactory, populationFactory);
				currentTime += carLeg.getTravelTime();
				trip.add(carLeg);
				break;
			case TransportMode.pt:
				// checks if pt is being simulated before adding the trip
				if (scenario.getConfig().transit().isUseTransit()) {
					@SuppressWarnings("unchecked")				
					List<PlanElement> legs = (List<PlanElement>) this.transitRouterDelegate.calcRoute(fromFacility,
							new LinkWrapperFacility(uamRoute.bestOriginStation.getLocationLink()), departureTime, person);
					for (PlanElement leg : legs) {
						if (leg instanceof Leg)
							currentTime += ((Leg) leg).getTravelTime();
					}
					trip.addAll(legs);
				} else {
					Leg uavPtAccessLeg = createTeleportationLeg(routeFactory, populationFactory,
							network.getLinks().get(fromFacility.getLinkId()), uamRoute.bestOriginStation.getLocationLink(),
							uamRoute.accessMode, uamRoute.accessMode);
					currentTime += uavPtAccessLeg.getTravelTime();
					trip.add(uavPtAccessLeg);
				}
				break;
			default:
				Leg uavAccessLeg = createTeleportationLeg(routeFactory, populationFactory,
						network.getLinks().get(fromFacility.getLinkId()), uamRoute.bestOriginStation.getLocationLink(),
						uamRoute.accessMode, UAMConstants.access + uamRoute.accessMode);
				currentTime += uavAccessLeg.getTravelTime();
				trip.add(uavAccessLeg);
		}

		/* origin station */
		Activity uav_interaction1 = populationFactory.createActivityFromLinkId(UAMConstants.interaction,
				uamRoute.bestOriginStation.getLocationLink().getId());
		uav_interaction1.setMaximumDuration(uamRoute.bestOriginStation.getPreFlightTime()); // Changes the value for the
		// duration of UAM
		// interaction after
		// arriving at the station
		// from the access leg

		trip.add(uav_interaction1);
		currentTime += uamRoute.bestOriginStation.getPreFlightTime(); // Still have to figure out why this doesn't
		// affect events

		try {
			// TODO: Rework this section and make it more efficient.
			double waitTime = this.waitingData.getWaitingData().get(uamRoute.bestOriginStation.getId())
					.getWaitingTime(departureTime);
			currentTime += waitTime;
		} catch (IndexOutOfBoundsException e) {
			if (counterWarningWaitingTimeSlot < counterLimit)
				log.warn(UAMConstants.uam.toUpperCase() + " waiting time requests for non-existent time slot. Using default value of "
						+ uamRoute.bestOriginStation.getDefaultWaitTime() + " for station "
						+ uamRoute.bestOriginStation.getId().toString());

			if (counterWarningWaitingTimeSlot == counterLimit - 1)
				log.warn("No more waiting time warnings will be reported.");

			counterWarningWaitingTimeSlot++;
			currentTime += uamRoute.bestOriginStation.getDefaultWaitTime(); // Added wait time of Origin Station
		} catch (NullPointerException e) {
			if (counterWarningWaitingTimeNull < counterLimit)
				log.warn(UAMConstants.uam.toUpperCase() + " waiting time not available. Using default value of "
						+ uamRoute.bestOriginStation.getDefaultWaitTime() + " for station "
						+ uamRoute.bestOriginStation.getId().toString());

			if (counterWarningWaitingTimeNull == counterLimit - 1)
				log.warn("No more waiting time warnings will be reported.");

			counterWarningWaitingTimeNull++;
			currentTime += uamRoute.bestOriginStation.getDefaultWaitTime(); // Added wait time of Origin Station
		}

		/* uam leg */
		// Retrieve pre-calculated UAM leg
		UAMFlightLeg cachedLeg = this.stationConnections.getFlightLeg(uamRoute.bestOriginStation.getId(),
				uamRoute.bestDestinationStation.getId());
		trip.add(createUAMLeg(uamRoute.bestOriginStation.getLocationLink(),
				uamRoute.bestDestinationStation.getLocationLink(), cachedLeg, routeFactory, populationFactory));
		currentTime += cachedLeg.travelTime;

		/* destination station */ // Add here passenger activities that only the passenger performs at destination
		// station
		Activity uav_interaction2 = populationFactory.createActivityFromLinkId(UAMConstants.interaction,
				uamRoute.bestDestinationStation.getLocationLink().getId());
		uav_interaction2.setMaximumDuration(uamRoute.bestDestinationStation.getPostFlightTime()); // Changes the value
		// for the duration
		// of UAM
		// interaction after
		// arriving at the
		// station from the
		// Flying leg

		trip.add(uav_interaction2);
		currentTime += uamRoute.bestDestinationStation.getPostFlightTime(); // Still have to figure out why this doesn't
		// affect events

		/* egress leg */
		switch (uamRoute.egressMode) {
			case TransportMode.car:
				Link egressDestinationLink = carNetwork.getLinks().get(toFacility.getLinkId());
				if (egressDestinationLink == null)
					egressDestinationLink = NetworkUtils.getNearestLinkExactly(carNetwork, toFacility.getCoord());

				Link egressOriginLink = carNetwork.getLinks()
						.get(uamRoute.bestDestinationStation.getLocationLink().getId());

				Leg carLeg = createCarLeg(UAMConstants.egress + TransportMode.car, egressOriginLink,
						egressDestinationLink, currentTime, person, routeFactory, populationFactory);
				trip.add(carLeg);
				break;
			case TransportMode.pt:
				// checks if pt is being simulated before adding the trip
				if (scenario.getConfig().transit().isUseTransit()) {
					@SuppressWarnings("unchecked")
					List<PlanElement> legs = (List<PlanElement>) this.transitRouterDelegate.calcRoute(
							new LinkWrapperFacility(uamRoute.bestDestinationStation.getLocationLink()), toFacility,
							currentTime, person);
					trip.addAll(legs);
				} else {
					Leg uavPtEgressLeg = createTeleportationLeg(routeFactory, populationFactory,
							uamRoute.bestDestinationStation.getLocationLink(),
							network.getLinks().get(toFacility.getLinkId()), uamRoute.egressMode, uamRoute.egressMode);
					trip.add(uavPtEgressLeg);
				}
				break;
			default:
				Leg uavEgressLeg = createTeleportationLeg(routeFactory, populationFactory,
						uamRoute.bestDestinationStation.getLocationLink(), network.getLinks().get(toFacility.getLinkId()),
						uamRoute.egressMode, UAMConstants.egress + uamRoute.egressMode);
				trip.add(uavEgressLeg);
		}

		return trip;
	}

	private Leg createTeleportationLeg(RouteFactories routeFactory, PopulationFactory populationFactory,
									   Link originLink, Link destinationLink, String mode, String actualMode) {

		double speed = ((PlansCalcRouteConfigGroup) scenario.getConfig().getModules().get("planscalcroute"))
				.getTeleportedModeSpeeds().get(mode);
		Route route = routeFactory.createRoute(Route.class, originLink.getId(), destinationLink.getId());
		double dist = CoordUtils.calcEuclideanDistance(originLink.getCoord(), destinationLink.getCoord());
		route.setDistance(dist);
		route.setTravelTime(dist / speed + 1.0);

		final Leg teleportationleg = populationFactory.createLeg(actualMode);
		teleportationleg.setTravelTime(dist / speed + 1.0);

		teleportationleg.setRoute(route);
		return teleportationleg;
	}

	private Leg createUAMLeg(Link originLink, Link destinationLink, UAMFlightLeg flightLeg,
							 RouteFactories routeFactory, PopulationFactory populationFactory) {
		return createNetworkLeg(UAMConstants.uam, originLink, destinationLink, flightLeg.links, routeFactory,
				populationFactory, flightLeg.travelTime, flightLeg.distance);
	}

	private Leg createCarLeg(String mode, Link originLink, Link destinationLink, double departureTime, Person person,
							 RouteFactories routeFactory, PopulationFactory populationFactory) {
		Path path = plcpccar.calcLeastCostPath(originLink.getToNode(), destinationLink.getFromNode(), departureTime,
				person, null);

		double distance = 0;
		for (Link link : path.links)
			distance += link.getLength();

		return createNetworkLeg(mode, originLink, destinationLink, path.links, routeFactory,
				populationFactory, path.travelTime, distance);
	}

	private Leg createNetworkLeg(String mode, Link originLink, Link destinationLink, List<Link> links,
								 RouteFactories routeFactory, PopulationFactory populationFactory, double travelTime,
								 double distance) {
		NetworkRoute route = routeFactory.createRoute(NetworkRoute.class, originLink.getId(), destinationLink.getId());
		route.setLinkIds(originLink.getId(), NetworkUtils.getLinkIds(links), destinationLink.getId());
		route.setTravelTime(travelTime);
		route.setDistance(distance);

		Leg leg = populationFactory.createLeg(mode);
		leg.setTravelTime(travelTime);
		leg.setRoute(route);
		return leg;
	}

	@Override
	public StageActivityTypes getStageActivityTypes() {
		final CompositeStageActivityTypes stageTypes = new CompositeStageActivityTypes();
		stageTypes.addActivityTypes(new StageActivityTypesImpl(UAMConstants.interaction));
		return stageTypes;
	}
}