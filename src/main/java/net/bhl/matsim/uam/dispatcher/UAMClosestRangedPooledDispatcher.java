package net.bhl.matsim.uam.dispatcher;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.schedule.Schedule;
import org.matsim.contrib.dvrp.schedule.StayTask;
import org.matsim.contrib.dvrp.schedule.Task;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.QuadTree;

import com.google.inject.Inject;

import net.bhl.matsim.uam.infrastructure.UAMStation;
import net.bhl.matsim.uam.infrastructure.UAMVehicle;
import net.bhl.matsim.uam.infrastructure.UAMVehicleType;
import net.bhl.matsim.uam.passenger.UAMRequest;
import net.bhl.matsim.uam.schedule.UAMDropoffTask;
import net.bhl.matsim.uam.schedule.UAMPickupTask;
import net.bhl.matsim.uam.schedule.UAMSingleRideAppender;
import net.bhl.matsim.uam.schedule.UAMTaskType;

/**
 * UAM Dispatcher that allows pooled ride between passengers and uses vehicle
 * types' range restrictions.
 *
 * @author RRothfeld (Raoul Rothfeld)
 */
public class UAMClosestRangedPooledDispatcher implements UAMDispatcher {
	final Set<UAMVehicle> enRouteOrAwaitingPickupVehicles = new HashSet<>();
	@Inject
	final private UAMSingleRideAppender appender;
	final private Queue<UAMRequest> pendingRequests = new LinkedList<>();
	final private Map<UAMVehicleType, QuadTree<UAMVehicle>> availableVehiclesTree = new HashMap<>();
	final private Map<UAMVehicle, Coord> availableVehicleLocations = new HashMap<>();
	final boolean reoptimize = true;

	@Inject
	public UAMClosestRangedPooledDispatcher(UAMSingleRideAppender appender, UAMManager uamManager, Network network,
			Fleet data) {
		this.appender = appender;

		double[] bounds = NetworkUtils.getBoundingBox(network.getNodes().values()); // minX, minY, maxX, maxY

		for (DvrpVehicle veh : data.getVehicles().values()) {
			UAMVehicle vehicle = (UAMVehicle) veh;
			Id<UAMStation> stationId = vehicle.getInitialStationId();
			Coord coord = uamManager.getStations().getUAMStations().get(stationId).getLocationLink().getCoord();

			if (!availableVehiclesTree.containsKey(vehicle.getVehicleType())) {
				availableVehiclesTree.put(vehicle.getVehicleType(),
						new QuadTree<>(bounds[0], bounds[1], bounds[2], bounds[3]));
			}

			availableVehiclesTree.get(vehicle.getVehicleType()).put(coord.getX(), coord.getY(), vehicle);
			availableVehicleLocations.put(vehicle, coord);
		}
	}

	@Override
	public void onNextTimeStep(double now) {
		appender.update();
		if (reoptimize)
			reoptimize(now);
	}

	@Override
	public void onRequestSubmitted(UAMRequest request) {
		pendingRequests.add(request);
	}

	@Override
	public void onNextTaskStarted(UAMVehicle vehicle) {
		Schedule schedule = vehicle.getSchedule();
		Task task = schedule.getCurrentTask();

		if (task.getTaskType().equals(UAMTaskType.STAY)) {
			Coord coord = ((StayTask) task).getLink().getCoord();
			// check if this vehicle has a pickup task next
			// and then check if it has space
			// because this vehicle might have be rerouted previously
			// to another station for pickup
			int index = schedule.getTasks().indexOf(schedule.getCurrentTask());

			for (int i = index; i < schedule.getTaskCount(); i++) {
				Task taskN = schedule.getTasks().get(i);
				if (taskN instanceof UAMPickupTask) {
					if (((UAMPickupTask) schedule.getTasks().get(i)).getRequests().size() < vehicle.getCapacity()) {
						// TODO: probably should not be added at all here
						// as these are enroute vehicles
						//this.availableVehiclesTree.get(vehicle.getVehicleType()).put(coord.getX(), coord.getY(),
						//		vehicle);
						//this.availableVehicleLocations.put(vehicle, coord);
						return;
					}
					break;
				}
				else if (i == schedule.getTaskCount() - 1) {
					// means we do not have any pickup tasks
					// so we can add this vehicle to the available list
					// as it is empty
					this.availableVehiclesTree.get(vehicle.getVehicleType()).put(coord.getX(), coord.getY(),
							vehicle);
					this.availableVehicleLocations.put(vehicle, coord);
					return;
				}
			}
		}

		if (task instanceof UAMPickupTask)
			this.enRouteOrAwaitingPickupVehicles.remove(vehicle);
	}

	private void reoptimize(double now) {
		Queue<UAMRequest> deferredRequests = new LinkedList<>();
		while (pendingRequests.size() > 0) {
			UAMRequest request = pendingRequests.poll();
			Coord requestCoord = request.getFromLink().getCoord();

			Set<UAMVehicleType> sufficientRangeTypes = new HashSet<>();
			for (UAMVehicleType type : availableVehiclesTree.keySet()) {
				if (type.getRange() >= request.getDistance())
					sufficientRangeTypes.add(type);
			}

			UAMVehicle vehicle = null;
			double distance = Double.MAX_VALUE;
			for (UAMVehicleType type : sufficientRangeTypes) {
				if (availableVehiclesTree.get(type).size() == 0)
					continue;

				UAMVehicle closestVehiclesOfType = availableVehiclesTree.get(type).getClosest(requestCoord.getX(),
						requestCoord.getY());
				double currentDistance = NetworkUtils.getEuclideanDistance(requestCoord,
						availableVehicleLocations.get(closestVehiclesOfType));

				if (currentDistance < distance)
					vehicle = closestVehiclesOfType;
			}

			if (vehicle != null) {
				Coord coord = availableVehicleLocations.get(vehicle);
				this.availableVehiclesTree.get(vehicle.getVehicleType()).remove(coord.getX(), coord.getY(), vehicle);

				appender.schedule(request, vehicle, now);
				Schedule schedule = vehicle.getSchedule();
				int index = schedule.getTasks().indexOf(schedule.getCurrentTask());

				for (int i = index; i < schedule.getTaskCount(); i++) {
					Task task = schedule.getTasks().get(i);
					if (task instanceof UAMPickupTask) {
						if (((UAMPickupTask) schedule.getTasks().get(i)).getRequests().size() < vehicle
								.getCapacity()) {
							enRouteOrAwaitingPickupVehicles.add(vehicle);
						}
						break;
					}
				}

			} else {
				if (!findEligableEnRouteVehicle(request))
					deferredRequests.add(request);
			}
		}

		this.pendingRequests.addAll(deferredRequests);
	}

	/**
	 * @param request UAM Request
	 * @return True if origins and destinations of requests are the same and vehicle
	 *         capacity constraint is met, otherwise false.
	 */
	private boolean findEligableEnRouteVehicle(UAMRequest request) {
		for (UAMVehicle vehicle : enRouteOrAwaitingPickupVehicles) {
			Schedule schedule = vehicle.getSchedule();
			int index = schedule.getTasks().indexOf(schedule.getCurrentTask());

			if (!(schedule.getTasks().get(index) instanceof UAMPickupTask)) {
				index++;
				if (index >= schedule.getTaskCount() || !(schedule.getTasks().get(index) instanceof UAMPickupTask))
					continue;
			}

			UAMPickupTask pickupTask = (UAMPickupTask) schedule.getTasks().get(index);
			UAMRequest oldReq = (UAMRequest) pickupTask.getRequests().toArray()[0];
			if (oldReq.getToLink() == request.getToLink() && oldReq.getFromLink() == request.getFromLink()) {
				request.setDistance(oldReq.getDistance());
				pickupTask.getRequests().add(request);
				UAMDropoffTask dropOff = (UAMDropoffTask) schedule.getTasks().get(index + 2);
				dropOff.getRequests().add(request);

				if (vehicle.getCapacity() <= dropOff.getRequests().size())
					this.enRouteOrAwaitingPickupVehicles.remove(vehicle);

				return true;
			}

		}

		return false;
	}
}
