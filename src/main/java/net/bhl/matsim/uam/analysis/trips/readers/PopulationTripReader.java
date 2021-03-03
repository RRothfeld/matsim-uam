package net.bhl.matsim.uam.analysis.trips.readers;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;

import net.bhl.matsim.uam.analysis.trips.TripItem;
import net.bhl.matsim.uam.analysis.trips.utils.HomeActivityTypes;

/**
 * A reader for trips based on a population file (plans file).
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class PopulationTripReader {
	final private Network network;
	final private HomeActivityTypes homeActivityTypes;
	final private MainModeIdentifier mainModeIdentifier;

	public PopulationTripReader(Network network, HomeActivityTypes homeActivityTypes,
			MainModeIdentifier mainModeIdentifier) {
		this.network = network;
		this.homeActivityTypes = homeActivityTypes;
		this.mainModeIdentifier = mainModeIdentifier;
	}

	public Collection<TripItem> readTrips(String populationPath) {
		Config config = ConfigUtils.createConfig();
		Scenario scenario = ScenarioUtils.createScenario(config);
		new PopulationReader(scenario).readFile(populationPath);
		return readTrips(scenario.getPopulation());
	}

	public Collection<TripItem> readTrips(Population population) {
		List<TripItem> tripItems = new LinkedList<>();

		for (Person person : population.getPersons().values()) {
			List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());

			int personTripIndex = 0;

			for (TripStructureUtils.Trip trip : trips) {
				boolean isHomeTrip = homeActivityTypes.isHomeActivity(trip.getDestinationActivity().getType());

				List<Leg> legs = trip.getLegsOnly();

				tripItems.add(new TripItem(person.getId(), personTripIndex, trip.getOriginActivity().getCoord(),
						trip.getDestinationActivity().getCoord(), trip.getOriginActivity().getEndTime().seconds(),
						legs.get(legs.size() - 1).getDepartureTime().seconds()
								+ legs.get(legs.size() - 1).getTravelTime().seconds()
								- legs.get(0).getDepartureTime().seconds(),
						getNetworkDistance(trip) / 1000.0, mainModeIdentifier.identifyMainMode(trip.getTripElements()),
						trip.getOriginActivity().getType(), trip.getDestinationActivity().getType(), isHomeTrip,
						CoordUtils.calcEuclideanDistance(trip.getOriginActivity().getCoord(),
								trip.getDestinationActivity().getCoord()) / 1000.0));

				personTripIndex++;
			}
		}

		return tripItems;
	}

	private double getNetworkDistance(TripStructureUtils.Trip trip) {
		if (mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(TransportMode.car)) {
			NetworkRoute route = (NetworkRoute) trip.getLegsOnly().get(0).getRoute();
			double distance = 0.0;

			if (route != null) {
				for (Id<Link> linkId : route.getLinkIds()) {
					distance += network.getLinks().get(linkId).getLength();
				}
			}

			return distance;
		} else {
			double distance = 0.0;

			for (Leg leg : trip.getLegsOnly()) {
				distance += leg.getRoute().getDistance();
			}

			return distance;
		}
	}
}
