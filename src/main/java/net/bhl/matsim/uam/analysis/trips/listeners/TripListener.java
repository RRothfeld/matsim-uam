package net.bhl.matsim.uam.analysis.trips.listeners;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonStuckEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.api.experimental.events.handler.TeleportationArrivalEventHandler;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.vehicles.Vehicle;

import net.bhl.matsim.uam.analysis.trips.TripItem;
import net.bhl.matsim.uam.analysis.trips.utils.HomeActivityTypes;

/**
 * A listener that retrieves information from trip events.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class TripListener implements ActivityStartEventHandler, ActivityEndEventHandler, PersonDepartureEventHandler,
		PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, LinkEnterEventHandler,
		PersonStuckEventHandler, TeleportationArrivalEventHandler {
	final private HomeActivityTypes homeActivityTypes;
	final private MainModeIdentifier mainModeIdentifier;
	final private Network network;
	final private PopulationFactory factory;
	final private Collection<String> networkRouteModes;

	final private Collection<TripItem> trips = new LinkedList<>();
	final private Map<Id<Person>, TripListenerItem> ongoing = new HashMap<>();
	final private Map<Id<Vehicle>, Collection<Id<Person>>> passengers = new HashMap<>();
	final private Map<Id<Person>, Integer> tripIndex = new HashMap<>();

	public TripListener(Network network, HomeActivityTypes homeActivityTypes,
						MainModeIdentifier mainModeIdentifier, Collection<String> networkRouteModes) {
		this.network = network;
		this.homeActivityTypes = homeActivityTypes;
		this.mainModeIdentifier = mainModeIdentifier;
		this.networkRouteModes = networkRouteModes;
		this.factory = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation().getFactory();
	}

	public Collection<TripItem> getTripItems() {
		return trips;
	}

	@Override
	public void reset(int iteration) {
		trips.clear();
		ongoing.clear();
		passengers.clear();
		tripIndex.clear();
	}

	@Override
	public void handleEvent(ActivityEndEvent event) {
		if (!TripStructureUtils.isStageActivityType(event.getActType())) {
			Integer personTripIndex = tripIndex.get(event.getPersonId());
			network.getLinks().get(event.getLinkId()).getCoord();

			if (personTripIndex == null) {
				personTripIndex = 0;
			} else {
				personTripIndex = personTripIndex + 1;
			}

			ongoing.put(event.getPersonId(), new TripListenerItem(event.getPersonId(), personTripIndex,
					network.getLinks().get(event.getLinkId()).getCoord(), event.getTime(), event.getActType()));

			tripIndex.put(event.getPersonId(), personTripIndex);
		}
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {

		if (ongoing.get(event.getPersonId()) == null) {
			Integer personTripIndex = tripIndex.get(event.getPersonId());
			network.getLinks().get(event.getLinkId()).getCoord();

			if (personTripIndex == null) {
				personTripIndex = 0;
			} else {
				personTripIndex = personTripIndex + 1;
			}

			ongoing.put(event.getPersonId(), new TripListenerItem(event.getPersonId(), personTripIndex,
					network.getLinks().get(event.getLinkId()).getCoord(), event.getTime(), event.getEventType()));

			tripIndex.put(event.getPersonId(), personTripIndex);
		} else {
			ongoing.get(event.getPersonId()).elements.add(factory.createLeg(event.getLegMode()));
		}
	}

	@Override
	public void handleEvent(ActivityStartEvent event) {
		if (TripStructureUtils.isStageActivityType(event.getActType())) {
			ongoing.get(event.getPersonId()).elements
					.add(factory.createActivityFromLinkId(event.getActType(), event.getLinkId()));
		} else {
			TripListenerItem trip = ongoing.remove(event.getPersonId());

			if (trip != null && !trip.elements.isEmpty()) {
				trip.returning = homeActivityTypes.isHomeActivity(event.getActType());
				trip.followingPurpose = event.getActType();
				trip.travelTime = event.getTime() - trip.startTime;
				trip.mode = mainModeIdentifier.identifyMainMode(trip.elements);
				trip.destination = network.getLinks().get(event.getLinkId()).getCoord();
				trip.networkDistance = getNetworkDistance(trip);
				trip.crowflyDistance = CoordUtils.calcEuclideanDistance(trip.origin, trip.destination);

				trips.add(new TripItem(trip.personId, trip.personTripId, trip.origin, trip.destination, trip.startTime,
						trip.travelTime, trip.networkDistance, trip.mode, trip.preceedingPurpose, trip.followingPurpose,
						trip.returning, trip.crowflyDistance));
			}
		}
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if (!passengers.containsKey(event.getVehicleId())) {
			passengers.put(event.getVehicleId(), new HashSet<>());
		}

		passengers.get(event.getVehicleId()).add(event.getPersonId());
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		if (passengers.containsKey(event.getVehicleId())) {
			passengers.get(event.getVehicleId()).remove(event.getPersonId());

			if (passengers.get(event.getVehicleId()).size() == 0) {
				passengers.remove(event.getVehicleId());
			}
		}
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		Collection<Id<Person>> personIds = passengers.get(event.getVehicleId());

		if (personIds != null) {
			personIds.forEach(id -> ongoing.get(id).route.add(event.getLinkId()));
		}
	}

	private double getNetworkDistance(TripListenerItem trip) {
		if (networkRouteModes.contains(mainModeIdentifier.identifyMainMode(trip.elements))) {
			double distance = 0.0;

			if (trip.route.size() > 0) {
				for (Id<Link> linkId : trip.route.subList(0, trip.route.size() - 1)) {
					distance += network.getLinks().get(linkId).getLength();
				}
			}

			return distance;
		}

		return trip.networkDistance;
	}

	@Override
	public void handleEvent(TeleportationArrivalEvent event) {
		TripListenerItem item = ongoing.get(event.getPersonId());

		if (Double.isNaN(item.networkDistance)) {
			item.networkDistance = 0.0;
		}

		item.networkDistance += event.getDistance();
	}

	@Override
	public void handleEvent(PersonStuckEvent event) {
		TripListenerItem item = ongoing.remove(event.getPersonId());

		if (item != null) {
			item.mode = "STUCK AND ABORTED";
			trips.add(item);
		}
	}
}
