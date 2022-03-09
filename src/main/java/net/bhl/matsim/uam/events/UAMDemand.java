package net.bhl.matsim.uam.events;

import com.google.inject.Inject;
import net.bhl.matsim.uam.data.WaitingStationData;
import net.bhl.matsim.uam.dispatcher.UAMManager;
import net.bhl.matsim.uam.infrastructure.UAMStation;
import net.bhl.matsim.uam.run.UAMConstants;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.pt.PtConstants;
import org.matsim.vehicles.Vehicle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class that saves information about UAM trips for each agent.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMDemand implements PersonArrivalEventHandler, PersonDepartureEventHandler, ActivityStartEventHandler,
		PersonEntersVehicleEventHandler {
	Map<Id<Person>, ArrayList<UAMData>> demand = new ConcurrentHashMap<>();
	Map<Id<Person>, PTData> tempPTData = new ConcurrentHashMap<>();
	Map<Id<Person>, Boolean> uamTrips = new ConcurrentHashMap<>();
	Map<Id<Person>, Id<Vehicle>> personToVehicle = new ConcurrentHashMap<>();
	Map<Id<Vehicle>, Set<Id<Person>>> vehicleToPerson = new ConcurrentHashMap<>();
	@Inject
	private Scenario scenario;
	@Inject
	private UAMManager manager;
	@Inject
	private WaitingStationData waitingData;

	@Override
	public void reset(int iteration) {
		demand = new ConcurrentHashMap<>();
		tempPTData = new ConcurrentHashMap<>();
		uamTrips = new ConcurrentHashMap<>();
		personToVehicle = new ConcurrentHashMap<>();
		vehicleToPerson = new ConcurrentHashMap<>();
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		Network network = scenario.getNetwork();
		if (event.getLegMode().startsWith(UAMConstants.access)) {

			UAMData data = new UAMData();
			data.startTime = event.getTime();
			data.originLink = network.getLinks().get(event.getLinkId());
			data.accessMode = event.getLegMode();
			tempPTData.remove(event.getPersonId());
			if (demand.containsKey(event.getPersonId())) {
				this.demand.get(event.getPersonId()).add(data);
			} else {

				ArrayList<UAMData> newEntry = new ArrayList<>();
				newEntry.add(data);
				this.demand.put(event.getPersonId(), newEntry);
			}

		} else if (event.getLegMode().startsWith(UAMConstants.egress)) {
			tempPTData.remove(event.getPersonId());
			UAMData data = this.demand.get(event.getPersonId()).get(this.demand.get(event.getPersonId()).size() - 1);
			data.departureFromStationTime = event.getTime();
			uamTrips.remove(event.getPersonId());
			// TODO: this can be made nicer with the routingMode attribute
		} else if (event.getLegMode().equals(TransportMode.walk) || event.getLegMode().equals(TransportMode.pt)) {

			// we need to store the information about the pt trip
			// in case this becomes an access or an egress uam trip
			if (!tempPTData.containsKey(event.getPersonId())) {
				PTData ptData = new PTData();
				ptData.startTime = event.getTime();
				ptData.originLink = network.getLinks().get(event.getLinkId());
				tempPTData.put(event.getPersonId(), ptData);
			}

		} else if (event.getLegMode().equals(UAMConstants.uam)) {
			Link link = network.getLinks().get(event.getLinkId());
			UAMStation station = this.manager.getStations().getNearestUAMStation(link);
			if (tempPTData.containsKey(event.getPersonId())) {
				// if there was pt information we need to add it
				// to the UAMData object
				PTData data = tempPTData.get(event.getPersonId());

				UAMData uamData = new UAMData();
				uamData.originStationLink = link;
				uamData.originStationId = station.getId();
				uamData.startTime = data.startTime;
				uamData.accessMode = TransportMode.pt;
				uamData.arrivalAtStationTime = data.endTime;
				uamData.originLink = data.originLink;

				if (demand.containsKey(event.getPersonId())) {
					this.demand.get(event.getPersonId()).add(uamData);
				} else {
					ArrayList<UAMData> newEntry = new ArrayList<>();
					newEntry.add(uamData);
					this.demand.put(event.getPersonId(), newEntry);
				}

				this.tempPTData.remove(event.getPersonId());
			} else {
				// there was a normal access mode
				UAMData data = this.demand.get(event.getPersonId())
						.get(this.demand.get(event.getPersonId()).size() - 1);
				data.originStationLink = link;
				data.originStationId = station.getId();

			}
		}
		if (event.getPersonId().toString().startsWith(UAMConstants.vehicle)) {
			if (vehicleToPerson.containsKey(event.getPersonId())) {
				for (Id<Person> passenger : this.vehicleToPerson.get(event.getPersonId())) {
					UAMData data = this.demand.get(passenger).get(this.demand.get(passenger).size() - 1);

					data.takeOffTime = event.getTime();

					Link link = network.getLinks().get(event.getLinkId());
					UAMStation station = this.manager.getStations().getNearestUAMStation(link);
					this.waitingData.getWaitingDataForStation(station.getId()).addWaitingTime(
							event.getTime() - data.arrivalAtStationTime - station.getPreFlightTime(),
							data.arrivalAtStationTime);
				}
				this.vehicleToPerson.remove(event.getPersonId());
			}
		}
	}

	@Override
	public void handleEvent(PersonArrivalEvent event) {
		Network network = scenario.getNetwork();

		if (event.getLegMode().equals(UAMConstants.uam)) {
			uamTrips.put(event.getPersonId(), true);

			UAMData data = this.demand.get(event.getPersonId()).get(this.demand.get(event.getPersonId()).size() - 1);
			tempPTData.remove(event.getPersonId());
			data.destinationStationLink = network.getLinks().get(event.getLinkId());
			UAMStation station = this.manager.getStations()
					.getNearestUAMStation(network.getLinks().get(event.getLinkId()));
			data.destinationStationId = station.getId();
			double deboardingTime = this.manager.getVehicles().get(this.personToVehicle.get(event.getPersonId()))
					.getDeboardingTime();
			data.landingTime = event.getTime() - deboardingTime; // #Landing time is when the vehicle touches the ground
			data.vehicleId = this.personToVehicle.get(event.getPersonId()).toString();
			data.uamTrip = true;
		} else if (event.getLegMode().startsWith(UAMConstants.egress)) {

			UAMData data = this.demand.get(event.getPersonId()).get(this.demand.get(event.getPersonId()).size() - 1);
			data.egressMode = event.getLegMode();
			data.endTime = event.getTime();
			data.destinationLink = network.getLinks().get(event.getLinkId());
			uamTrips.put(event.getPersonId(), false);

		} else if (event.getLegMode().startsWith(UAMConstants.access)) {
			UAMData data = this.demand.get(event.getPersonId()).get(this.demand.get(event.getPersonId()).size() - 1);
			data.arrivalAtStationTime = event.getTime();
		} else if (event.getLegMode().equals(TransportMode.walk)
				|| (event.getLegMode().equals(TransportMode.pt) && !scenario.getConfig().transit().isUseTransit())) {
			// we are still in the potential access or egress pt trip
			PTData data = tempPTData.get(event.getPersonId());
			data.endTime = event.getTime();
			data.destinationLink = network.getLinks().get(event.getLinkId());
		}

	}

	public Map<Id<Person>, ArrayList<UAMData>> getDemand() {
		return demand;
	}

	@Override
	public void handleEvent(ActivityStartEvent event) {

		// when we arrive at the destination we need to check if there was an egress pt
		// trip and add that information to the UAMData
		if (!event.getActType().equals(UAMConstants.interaction)
				&& !event.getActType().equals(PtConstants.TRANSIT_ACTIVITY_TYPE)) {
			if (uamTrips.containsKey(event.getPersonId()) && uamTrips.get(event.getPersonId())) {

				if (tempPTData.containsKey(event.getPersonId())) {

					UAMData data = this.demand.get(event.getPersonId())
							.get(this.demand.get(event.getPersonId()).size() - 1);

					PTData ptData = tempPTData.get(event.getPersonId());
					data.departureFromStationTime = ptData.startTime;
					data.endTime = ptData.endTime;
					data.destinationLink = ptData.destinationLink;
					data.egressMode = TransportMode.pt;
					tempPTData.remove(event.getPersonId());
				}
			}
			tempPTData.remove(event.getPersonId());
			uamTrips.put(event.getPersonId(), false);
		}
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if (!event.getPersonId().toString().equals(event.getVehicleId().toString())) {
			this.personToVehicle.put(event.getPersonId(), event.getVehicleId()); // person to vehicle
			if (this.vehicleToPerson.containsKey(event.getVehicleId())) {
				this.vehicleToPerson.get(event.getVehicleId()).add(event.getPersonId());
			} else {
				Set<Id<Person>> newEntry = new HashSet<>();
				newEntry.add(event.getPersonId());
				this.vehicleToPerson.put(event.getVehicleId(), newEntry);
			}
		}
	}

	class PTData {
		double startTime;
		double endTime;
		Link originLink;
		Link destinationLink;
	}
}
