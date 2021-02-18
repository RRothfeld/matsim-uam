package net.bhl.matsim.uam.infrastructure.readers;

import com.google.common.collect.ImmutableMap;
import net.bhl.matsim.uam.infrastructure.UAMStation;
import net.bhl.matsim.uam.infrastructure.UAMStationSimple;
import net.bhl.matsim.uam.infrastructure.UAMVehicle;
import net.bhl.matsim.uam.infrastructure.UAMVehicleType;
import net.bhl.matsim.uam.run.UAMConstants;
import org.jfree.util.Log;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.FleetSpecificationImpl;
import org.matsim.contrib.dvrp.fleet.ImmutableDvrpVehicleSpecification;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.io.MatsimXmlParser;
import org.matsim.core.utils.misc.Time;
import org.xml.sax.Attributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * This class reads reads the UAM xml file containing the parameters for UAM
 * simulation.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMXMLReader extends MatsimXmlParser {

    public final Network network;
    private final Map<Id<DvrpVehicle>, UAMVehicle> vehiclesForData = new HashMap<>();
    private final Map<Id<UAMStation>, UAMStation> stations = new HashMap<>();
    private final Map<Id<DvrpVehicle>, UAMVehicle> vehicles = new HashMap<>();
    private final Map<Id<UAMVehicleType>, UAMVehicleType> vehicleTypes = new HashMap<>();
    private final Map<String, Double> mapVehicleHorizontalSpeeds = new HashMap<>();
    private final Map<String, Double> mapVehicleVerticalSpeeds = new HashMap<>();
    private final FleetSpecificationImpl fleetSpecification = new FleetSpecificationImpl();

    public UAMXMLReader(Network uamNetwork) {
        this.network = uamNetwork;
    }

    @Override
    public void startTag(String name, Attributes atts, Stack<String> context) {
		switch (name) {
			case "station": {
				// Get station ID
				Id<UAMStation> id = Id.create(atts.getValue("id"), UAMStation.class);

				String stationName = atts.getValue("name");
				if (stationName == null)
					stationName = id.toString();

				// Get station attributes: preFlighTime, postFlightTime, and defaultWaitTime
				double preFlightTime = Double.parseDouble(atts.getValue("preflighttime"));
				double postFlightTime = Double.parseDouble(atts.getValue("postflighttime"));
				double defaultWaitTime = Double.parseDouble(atts.getValue("defaultwaittime"));

				// Get station access/egress link or coordinates, to find nearest link
				String linkName = atts.getValue("link");
				List<Link> links = NetworkUtils.getLinks(network, linkName);
				Link link = links.get(0);

				// Create UAm station and register it on station map
				UAMStation ls = new UAMStationSimple(preFlightTime, postFlightTime, defaultWaitTime, link, id, stationName);
				stations.put(id, ls);
				break;
			}
			case "vehicleType": {
				Id<UAMVehicleType> id = Id.create(atts.getValue("id"), UAMVehicleType.class);
				int capacity = Integer.parseInt(atts.getValue("capacity"));
				double range = Double.parseDouble(atts.getValue("range"));
				double horizontalSpeed = Double.parseDouble(atts.getValue("cruisespeed"));
				double verticalSpeed = Double.parseDouble(atts.getValue("verticalspeed"));

				// Get vehicle type attributes: boardingTime, deboardingTime and turnAroundTime
				double boardingTime = Double.parseDouble(atts.getValue("boardingtime"));
				double deboardingTime = Double.parseDouble(atts.getValue("deboardingtime"));
				double turnAroundTime = Double.parseDouble(atts.getValue("turnaroundtime"));

				UAMVehicleType vehicleType = new UAMVehicleType(id, capacity, range, horizontalSpeed, verticalSpeed,
						boardingTime, deboardingTime, turnAroundTime);
				vehicleTypes.put(id, vehicleType);
				break;
			}
			case "vehicle": {
				Id<DvrpVehicle> id = Id.create(UAMConstants.vehicle + atts.getValue("id"), DvrpVehicle.class);
				Id<UAMVehicleType> vehicleTypeId = Id.create(atts.getValue("type"), UAMVehicleType.class);

				// gets starttime and endtime
				double starttime = Time.parseTime(atts.getValue("starttime"));
				double endtime = Time.parseTime(atts.getValue("endtime"));
				// TODO check whether start- and endtimes work correctly

				// gets initial station
				Id<UAMStation> stationid = Id.create(atts.getValue("initialstation"), UAMStation.class);

				// liftofftime, width, length, range);
				double horizontalSpeed = vehicleTypes.get(vehicleTypeId).getCruiseSpeed();
				double verticalSpeed = vehicleTypes.get(vehicleTypeId).getVerticalSpeed();
				int capacity = vehicleTypes.get(vehicleTypeId).getCapacity();

				this.mapVehicleVerticalSpeeds.put(id.toString(), verticalSpeed);
				this.mapVehicleHorizontalSpeeds.put(id.toString(), horizontalSpeed);

				try {
					if (vehicles.containsKey(id)) {
						id = Id.create(atts.getValue("id").concat("_" + atts.getValue("type")),
								DvrpVehicle.class);
					}
					fleetSpecification.addVehicleSpecification(ImmutableDvrpVehicleSpecification.newBuilder().id(id)
							.capacity(capacity).startLinkId(this.stations.get(stationid).getLocationLink().getId())
							.serviceBeginTime(starttime).serviceEndTime(endtime).build());

					UAMVehicle vehicle = new UAMVehicle(fleetSpecification.getVehicleSpecifications().get(id),
							this.stations.get(stationid).getLocationLink(), stationid, vehicleTypes.get(vehicleTypeId));

					vehicles.put(id, vehicle);
					UAMVehicle vehicleCopy = new UAMVehicle(fleetSpecification.getVehicleSpecifications().get(id),
							this.stations.get(stationid).getLocationLink(), stationid, vehicleTypes.get(vehicleTypeId));
					vehiclesForData.put(id, vehicleCopy);

				} catch (NullPointerException e) {
					Log.warn(UAMConstants.uam.toUpperCase() + " vehicle " + id + " could not be added. Check correct initial station or vehicle type.");
				}
				break;
			}
			default:
				Log.warn("There was an error parsing the UAM xml-file. Non vehicle/station element found.");
		}
    }

    @Override
    public void endTag(String name, String content, Stack<String> context) {
    }

    public Map<Id<UAMStation>, UAMStation> getStations() {
        return stations;
    }

    public Map<Id<DvrpVehicle>, UAMVehicle> getVehicles() {
        return ImmutableMap.copyOf(vehicles);
    }

    public Map<Id<DvrpVehicle>, UAMVehicle> getVehiclesForData() {
		return new HashMap<>(vehiclesForData);
    }

    public Map<String, Double> getMapVehicleHorizontalSpeeds() {
        return this.mapVehicleHorizontalSpeeds;
    }

    public Map<String, Double> getMapVehicleVerticalSpeeds() {
        return this.mapVehicleVerticalSpeeds;
    }

    public FleetSpecificationImpl getFleetSpecification() {
        return this.fleetSpecification;
    }

}
