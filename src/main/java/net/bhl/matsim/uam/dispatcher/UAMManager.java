package net.bhl.matsim.uam.dispatcher;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.QuadTree;

import net.bhl.matsim.uam.infrastructure.UAMStation;
import net.bhl.matsim.uam.infrastructure.UAMStations;
import net.bhl.matsim.uam.infrastructure.UAMVehicle;

/**
 * A class that stores information about UAM infrastructure and manages its
 * changes.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */

public class UAMManager implements IterationStartsListener {

	public QuadTree<UAMVehicle> mapAvailableVehicles;
	private Set<UAMVehicle> availableVehicles = new HashSet<>();
	private Map<Id<DvrpVehicle>, Id<UAMStation>> vehicleLocations = new HashMap<>();

	private UAMStations stations;
	private QuadTree<UAMStation> stationsTree;
	private Map<Id<DvrpVehicle>, UAMVehicle> vehicles = new HashMap<>();

	private Network network;

	public UAMManager(Network network, UAMStations stations, Map<Id<DvrpVehicle>, UAMVehicle> vehicles) {
		this.network = network;
		this.stations = stations;
		this.vehicles = vehicles;
	}

	public UAMStations getStations() {
		return stations;
	}

	public Map<Id<DvrpVehicle>, UAMVehicle> getVehicles() {
		return vehicles;
	}
	
	/**
	 * Initialize all datasets.
	 */
	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		double[] bounds = NetworkUtils.getBoundingBox(network.getNodes().values()); // minX, minY, maxX, maxY

		mapAvailableVehicles = new QuadTree<>(bounds[0], bounds[1], bounds[2], bounds[3]);
		stationsTree = new QuadTree<>(bounds[0], bounds[1], bounds[2], bounds[3]);
		availableVehicles = new HashSet<>();

		vehicleLocations = new HashMap<>();

		for (UAMStation station : stations.getUAMStations().values()) {
			stationsTree.put(station.getLocationLink().getCoord().getX(), station.getLocationLink().getCoord().getY(),
					station);
		}

		for (UAMVehicle vehicle : vehicles.values()) {
			UAMStation station = this.stations.getUAMStations().get((vehicle).getInitialStationId());
			this.vehicleLocations.put(vehicle.getId(), station.getId());
			this.availableVehicles.add(vehicle);

			Link stationLink = station.getLocationLink();
			this.mapAvailableVehicles.put(stationLink.getCoord().getX(), stationLink.getCoord().getY(), vehicle);
		}
	}
}
