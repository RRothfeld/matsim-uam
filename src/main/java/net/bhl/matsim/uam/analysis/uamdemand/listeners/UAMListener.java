package net.bhl.matsim.uam.analysis.uamdemand.listeners;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
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
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.pt.PtConstants;
import org.matsim.vehicles.Vehicle;

import net.bhl.matsim.uam.analysis.uamdemand.UAMDemandItem;
import net.bhl.matsim.uam.infrastructure.UAMStation;
import net.bhl.matsim.uam.infrastructure.UAMStations;
import net.bhl.matsim.uam.infrastructure.UAMVehicle;
import net.bhl.matsim.uam.infrastructure.readers.UAMXMLReader;
import net.bhl.matsim.uam.run.UAMConstants;

/**
 * A listener that retrieves information from UAMtrip events.
 *
 * @author Aitanm (Aitan Militao), RRothfeld (Raoul Rothfeld)
 */
public class UAMListener implements ActivityStartEventHandler, PersonDepartureEventHandler, PersonArrivalEventHandler,
		PersonEntersVehicleEventHandler {
	final private Network network;
	final private Collection<UAMDemandItem> uamData = new LinkedList<>();
	final private Map<Id<Person>, UAMListenerItem> ongoing = new HashMap<>();
	final private UAMStations uamStations;
	final private Map<Id<DvrpVehicle>, UAMVehicle> vehicles;
	Map<Id<Person>, PTData> tempPTData = new ConcurrentHashMap<>();
	Map<Id<Person>, Id<Vehicle>> personToVehicle = new ConcurrentHashMap<>();
	Map<Id<Vehicle>, Set<Id<Person>>> vehicleToPerson = new ConcurrentHashMap<>();
	UAMXMLReader uamReader;
	TransportModeNetworkFilter filter;

	public UAMListener(Network network, String uamConfigFile,
					   MainModeIdentifier mainModeIdentifier, Collection<String> networkRouteModes) {
		this.network = network;
		Set<String> modes = new HashSet<>();
		modes.add(UAMConstants.uam);
		Network networkUAM = NetworkUtils.createNetwork();
		filter = new TransportModeNetworkFilter(this.network);
		filter.filter(networkUAM, modes);
		this.uamReader = new UAMXMLReader(networkUAM);
		uamReader.readFile(uamConfigFile);
		this.uamStations = new UAMStations(this.uamReader.getStations(), network);
		this.vehicles = this.uamReader.getVehicles();
	}

	@Override
	public void reset(int iteration) {
		uamData.clear();
		ongoing.clear();
		tempPTData = new ConcurrentHashMap<>();
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		if (event.getLegMode().startsWith(UAMConstants.access)) {
			ongoing.put(event.getPersonId(), new UAMListenerItem(event.getPersonId(),
					network.getLinks().get(event.getLinkId()).getCoord(), event.getTime(), event.getLegMode()));
		} else if (event.getLegMode().startsWith(UAMConstants.egress)) {
			UAMDemandItem uamData = ongoing.get(event.getPersonId());
			uamData.departureFromStationTime = event.getTime();
		} else if (event.getLegMode().equals(TransportMode.access_walk)
				|| event.getLegMode().equals(TransportMode.transit_walk)
				|| event.getLegMode().equals(TransportMode.pt)) {
			// we need to store the information about the pt trip
			// in case this becomes an access or an egress uam trip
			if (!tempPTData.containsKey(event.getPersonId())) {
				PTData ptData = new PTData();
				ptData.startTime = event.getTime();
				ptData.originLink = network.getLinks().get(event.getLinkId());
				tempPTData.put(event.getPersonId(), ptData);
			}
		} else if (event.getLegMode().equals(UAMConstants.uam)) {
			Coord originStationCoord = network.getLinks().get(event.getLinkId()).getCoord();
			UAMStation station = uamStations.getNearestUAMStation(network.getLinks().get(event.getLinkId()));

			if (tempPTData.containsKey(event.getPersonId())) {
				// if there was pt information we need to add it
				// to the UAMData object
				PTData data = tempPTData.get(event.getPersonId());
				ongoing.put(event.getPersonId(), new UAMListenerItem(event.getPersonId(), data.originLink.getCoord(),
						data.startTime, TransportMode.pt));
				UAMDemandItem uamData = ongoing.get(event.getPersonId());
				uamData.originStationCoord = originStationCoord;
				uamData.originStationId = station.getId();
				uamData.arrivalAtStationTime = data.endTime;
				this.tempPTData.remove(event.getPersonId());
			} else {
				// there was a normal access mode
				UAMDemandItem uamData = ongoing.get(event.getPersonId());
				uamData.originStationCoord = originStationCoord;
				uamData.originStationId = station.getId();
				uamData.setOriginStationId(station.getId());
			}
		}

		if (event.getLegMode().equals(TransportMode.car)
				&& event.getPersonId().toString().startsWith(UAMConstants.vehicle)) {
			if (vehicleToPerson.containsKey(event.getPersonId())) {
				for (Id<Person> passenger : this.vehicleToPerson.get(event.getPersonId())) {
					UAMDemandItem uamData = ongoing.get(passenger);
					uamData.setTakeOffTime(event.getTime());
				}
				this.vehicleToPerson.remove(event.getPersonId());
			}
		}
	}

	@Override
	public void handleEvent(PersonArrivalEvent event) {
		if (event.getLegMode().equals(UAMConstants.uam)) {
			UAMDemandItem uamData = ongoing.get(event.getPersonId());
			tempPTData.remove(event.getPersonId());
			uamData.destinationStationCoord = network.getLinks().get(event.getLinkId()).getCoord();
			UAMStation station = uamStations.getNearestUAMStation(network.getLinks().get(event.getLinkId()));
			uamData.destinationStationId = station.getId();
			double deboardingTime = vehicles.get(this.personToVehicle.get(event.getPersonId())).getDeboardingTime();
			uamData.landingTime = event.getTime() - deboardingTime; // #Landing time is when the vehicle touches the
			// ground
			uamData.vehicleId = this.personToVehicle.get(event.getPersonId()).toString();
			uamData.uamTrip = true;
		} else if (event.getLegMode().startsWith(UAMConstants.egress)) {
			UAMDemandItem uamData = ongoing.get(event.getPersonId());
			uamData.egressMode = event.getLegMode();
			uamData.endTime = event.getTime();
			uamData.destination = network.getLinks().get(event.getLinkId()).getCoord();
		} else if (event.getLegMode().startsWith(UAMConstants.access)) {
			UAMDemandItem uamData = ongoing.get(event.getPersonId());
			uamData.arrivalAtStationTime = event.getTime();
		} else if (event.getLegMode().equals(TransportMode.egress_walk)
				|| event.getLegMode().equals(TransportMode.transit_walk)
				|| (event.getLegMode().equals(TransportMode.pt))) {
			// we are still in the potential access or egress pt trip
			PTData data = tempPTData.get(event.getPersonId());
			data.endTime = event.getTime();
			data.destinationLink = network.getLinks().get(event.getLinkId());
		}
	}

	@Override
	public void handleEvent(ActivityStartEvent event) {
		// when we arrive at the destination we need to check if there was an egress pt
		// trip and add that information to the UAMData
		UAMDemandItem uamData = ongoing.get(event.getPersonId());
		if (!event.getActType().equals(UAMConstants.interaction)
				&& !event.getActType().equals(PtConstants.TRANSIT_ACTIVITY_TYPE)) {
			if (ongoing.containsKey(event.getPersonId())) {
				if (uamData.uamTrip) {
					if (tempPTData.containsKey(event.getPersonId())) {
						PTData ptData = tempPTData.get(event.getPersonId());
						uamData.departureFromStationTime = ptData.startTime;
						uamData.endTime = ptData.endTime;
						uamData.destination = ptData.destinationLink.getCoord();
						uamData.egressMode = TransportMode.pt;
						tempPTData.remove(event.getPersonId());
					}
				}
			}
			tempPTData.remove(event.getPersonId());
		}
		// if it is not an stage activity it is the end of a trip
		if (!TripStructureUtils.isStageActivityType(event.getActType()) && ongoing.containsKey(event.getPersonId())) {
			UAMDemandItem uamItem = ongoing.remove(event.getPersonId());
			this.uamData.add(uamItem);
		}
	}

	public Collection<UAMDemandItem> getUAMItems() {
		return uamData;
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
