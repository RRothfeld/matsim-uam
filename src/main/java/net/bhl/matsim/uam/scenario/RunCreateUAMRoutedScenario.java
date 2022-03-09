package net.bhl.matsim.uam.scenario;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.util.CSVReaders;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.io.MatsimXmlWriter;

import com.google.common.collect.Iterables;

import net.bhl.matsim.uam.router.UAMFlightSegments;
import net.bhl.matsim.uam.router.strategy.UAMStrategy;
import net.bhl.matsim.uam.run.UAMConstants;
import net.bhl.matsim.uam.scenario.utils.ConfigAddUAMParameters;

/**
 * This script creates UAM-including MATSim network and corresponding
 * uam-vehicles file, which are prerequisites for running a UAM-enabled MATSim
 * simulation.
 *
 * @author RRothfeld (Raoul Rothfeld)
 */
public class RunCreateUAMRoutedScenario {
	// SETTINGS
	private static final boolean useZCoordinate = false; // default: false, i.e. use pseudo-3D (i.e. 2D) network
	private static final double detourFactor = 1.0; // default: 1.0, i.e. no detour from link distance

	// LATERALS
	private static final String name_uam_nodes = UAMConstants.uam + "_st_";
	private static final String name_uam_waypoints = UAMConstants.uam + "_wp_";
	private static final String name_uam_ground_link = UAMConstants.uam + "_gl-";
	private static final String name_uam_station_link = UAMConstants.uam + "_sl-";
	private static final String name_uam_vertical_link = UAMConstants.uam + "_vl-";
	private static final String name_uam_horizontal_link = UAMConstants.uam + "_hl-";
	private static final String name_uam_station_ground_access = "_ga";
	private static final String name_uam_station_flight_access = "_fa";
	private static final String name_uam_station_flight_level = "_fl";

	private static final String name_uam_dtd = "src/main/resources/dtd/uam.dtd";

	private static final double min_link_length = 1;
	private static final double no_length = -1;

	private static final double max_horizontal_vtol_distance = 500;
	private static final double permlanes = 1;

	private static double uamMaxLinkSpeed = -1;
	private static double uamLinkCapacity = -1;

	public static void main(String[] args) {
		System.out.println("ARGS: config.xml* uam-stations.csv* flight-nodes.csv* flight-links.csv* uam-vehicles.csv");
		System.out.println("(* required)");

		// ARGS
		int j = 0;
		String configInput = args[j++];
		String stationInput = args[j++];
		String nodesInput = args[j++];
		String linksInput = args[j++];
		String vehicleInput = null;

		if (args.length > j)
			vehicleInput = args[j];

		// Run
		convert(configInput, stationInput, nodesInput, linksInput, vehicleInput);
	}

	public static void convert (String configInput, String stationInput, double uamMaxLinkSpeed, double uamLinkCapacity) {
		convert(configInput, stationInput, uamMaxLinkSpeed, uamLinkCapacity, null);
	}

	public static void convert (String configInput, String stationInput, double uamMaxLinkSpeed, double uamLinkCapacity,
								String vehicleInput) {
		RunCreateUAMRoutedScenario.uamMaxLinkSpeed = uamMaxLinkSpeed;
		RunCreateUAMRoutedScenario.uamLinkCapacity = uamLinkCapacity;
		convert(configInput, stationInput, null, null, vehicleInput);
	}

	public static void convert(String configInput, String stationInput, String nodesInput, String linksInput) {
		convert(configInput, stationInput, nodesInput, linksInput, null);
	}

	public static void convert(String configInput, String stationInput, String nodesInput, String linksInput,
				String vehicleInput) {
		// READ REQUIRED INPUT FILES
		Config config = ConfigUtils.loadConfig(configInput);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Network network = scenario.getNetwork();
		List<String[]> stations = CSVReaders.readCSV(stationInput);

		List<String[]> vehicles = null;
		if (vehicleInput != null)
			vehicles = CSVReaders.readCSV(vehicleInput);

		// GENERATE FLIGHT LINKS AND NODES FOR DIRECT OR USE INPUT FLIGHT NETWORK
		if (nodesInput != null) {
			List<String[]> nodes = CSVReaders.readCSV(nodesInput);
			List<String[]> links = CSVReaders.readCSV(linksInput);

			// INDIRECT FLIGHT
			// create flight nodes
			for (String[] line : Iterables.skip(nodes, 1)) { // skip CSV header
				Id<Node> id = Id.createNodeId(name_uam_waypoints + encode(line[0]) + name_uam_station_flight_level);
				addNode(network, id, Double.parseDouble(line[1]), Double.parseDouble(line[2]),
						Double.parseDouble(line[3]));
			}

			// create node-connecting links
			for (String[] line : Iterables.skip(links, 1)) { // skip CSV header
				Id<Node> from = Id.createNodeId(name_uam_waypoints + encode(line[0]) + name_uam_station_flight_level);
				Id<Node> to = Id.createNodeId(name_uam_waypoints + encode(line[1]) + name_uam_station_flight_level);

				double capacity = Double.parseDouble(line[2]);
				double freespeed = Double.parseDouble(line[3]);

				if (freespeed > uamMaxLinkSpeed)
					uamMaxLinkSpeed = freespeed;

				if (capacity > uamLinkCapacity)
					uamLinkCapacity = capacity;

				Set<String> modesUam = new HashSet<>();
				modesUam.add(UAMConstants.uam);

				addLink(network, from, to, modesUam, capacity, freespeed);
			}
		} else {
			// DIRECT FLIGHT BETWEEN ALL STATIONS
			// create flight level accesses
			List<Id<Node>> flightAccesses = new ArrayList<>();
			for (String[] line : Iterables.skip(stations, 1)) { // skip CSV header
				Id<Node> node_id = Id.createNodeId(name_uam_nodes + line[0] + name_uam_station_flight_level);
				double node_x = Double.parseDouble(line[2]);
				double node_y = Double.parseDouble(line[3]);
				double node_z = Double.parseDouble(line[5]);

				addNode(network, node_id, node_x, node_y, node_z);
				flightAccesses.add(node_id);
			}

			// connect flight accesses
			for (int i = 0; i < flightAccesses.size(); i++) {
				for (int k = i + 1; k < flightAccesses.size(); k++) {

					Id<Node> to = flightAccesses.get(i);
					Id<Node> from = flightAccesses.get(k);

					Set<String> mode = new HashSet<>();
					mode.add(UAMConstants.uam);

					addLink(network, to, from, mode);
					addLink(network, from, to, mode);
				}
			}
		}

		// PROVIDE MODE-SPECIFIC NETWORKS
		TransportModeNetworkFilter filter = new TransportModeNetworkFilter(network);
		Network networkUAM = NetworkUtils.createNetwork();
		Set<String> modesUam = new HashSet<>();
		modesUam.add(UAMConstants.uam);
		filter.filter(networkUAM, modesUam);

		// clean up UAM network
		new NetworkCleaner().run(networkUAM);

		filter = new TransportModeNetworkFilter(network);
		Set<String> modesCar = new HashSet<>();
		modesCar.add(TransportMode.car); // only car
		Network networkCar = NetworkUtils.createNetwork();
		filter.filter(networkCar, modesCar);

		// temp. storage of all UAM stations
		Set<Id<Node>> stationIDs = new HashSet<>();
		for (String[] line : Iterables.skip(stations, 1)) { // skip CSV header
			int i = 0;
			String station_id = line[i++];
			i++; // skip station_name
			double station_x = Double.parseDouble(line[i++]);
			double station_y = Double.parseDouble(line[i++]);
			double station_z = Double.parseDouble(line[i++]);
			double vtol_z = Double.parseDouble(line[i++]);
			double road_access_capacity = Double.parseDouble(line[i++]);
			double road_access_freespeed = Double.parseDouble(line[i++]);
			double flight_access_capacity = Double.parseDouble(line[i++]);
			double flight_access_freespeed = Double.parseDouble(line[i]);

			// create ground and flight access nodes and station link
			Id<Node> node_ga_id = Id.createNodeId(name_uam_nodes + station_id + name_uam_station_ground_access);
			Id<Node> node_fa_id = Id.createNodeId(name_uam_nodes + station_id + name_uam_station_flight_access);
			stationIDs.add(node_fa_id);

			// find nearest flight node
			Coord station = createCoord(station_x, station_y, station_z);
			Node uamNode = NetworkUtils.getNearestNode(networkUAM, station);

			// if no UAM flight node is nearby, create own flight level node and connect to
			// nearest UAM node via
			// horizontal flight (rather than vertical)
			if (CoordUtils.calcEuclideanDistance(station, uamNode.getCoord()) >= max_horizontal_vtol_distance) {
				Id<Node> node_fl_id = Id.createNodeId(name_uam_nodes + station_id + name_uam_station_flight_level);
				addNode(network, node_fl_id, station_x, station_y, vtol_z);

				addLink(network, node_fl_id, uamNode.getId(), modesUam);
				addLink(network, uamNode.getId(), node_fl_id, modesUam);

				uamNode = network.getNodes().get(node_fl_id);
			}

			// find nearest ground node
			Node roadNode = NetworkUtils.getNearestNode(networkCar, station);

			// add station nodes to network (after access node search, since search for
			// closest node would return station nodes)
			addNode(network, node_ga_id, station_x, station_y, station_z);
			addNode(network, node_fa_id, station_x, station_y, station_z);

			// station links
			Set<String> modes = new HashSet<>();
			modes.add(UAMConstants.uam);
			modes.add(TransportMode.car);

			addLink(network, node_ga_id, node_fa_id, modes);
			addLink(network, node_fa_id, node_ga_id, modes);

			// flight access links
			addLink(network, node_fa_id, uamNode.getId(), modesUam, flight_access_capacity, flight_access_freespeed,
					vtol_z);
			addLink(network, uamNode.getId(), node_fa_id, modesUam, flight_access_capacity, flight_access_freespeed,
					vtol_z);

			// ground access links
			addLink(network, node_ga_id, roadNode.getId(), modesCar, road_access_capacity, road_access_freespeed);
			addLink(network, roadNode.getId(), node_ga_id, modesCar, road_access_capacity, road_access_freespeed);

		}

		// OUTPUT FOLDER
		int pathIndex = configInput.lastIndexOf("\\");
		String path = pathIndex >= 0 ? configInput.substring(0, pathIndex) : ".";
		path += "\\" + UAMConstants.uam + "-scenario_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date());
		new File(path).mkdir();

		// WRITE STATION DISTANCE CSV
		try {
			calculateStationDistances(network, stationIDs, path + "\\" + UAMConstants.uam + "_distances.csv");
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}

		// ADD UAM VEHICLES
		String vehiclesFileName = "<REPLACE WITH UAM VEHICLES FILE NAME>";
		if (vehicleInput != null) {
			vehiclesFileName = UAMConstants.uam + "_vehicles.xml.gz";
			UAMVehiclesXmlWriter vehwriter = new UAMVehiclesXmlWriter();
			vehwriter.write(path + "\\" + vehiclesFileName, stations, vehicles);
		}

		// UPDATE CONFIG
		String networkFileName = UAMConstants.uam + (nodesInput != null ? "_routed" : "") + "_network.xml.gz";
		NetworkConfigGroup networkConfigGroup = (NetworkConfigGroup) config.getModules().get("network");
		networkConfigGroup.setInputFile(networkFileName);
		ConfigAddUAMParameters.addUAMParameters(config, vehiclesFileName,
				TransportMode.car + "," + TransportMode.walk, 2, 9999,
				500, UAMStrategy.UAMStrategyType.MINTRAVELTIME, true);

		// WRITE UAM CONFIG
		ConfigWriter configWriter = new ConfigWriter(config);
		configWriter.write(path + "\\" + UAMConstants.uam + "_config.xml");

		// WRITE UAM NETWORK
		//network.setName((network.getName().isEmpty() ? "" : network.getName() + "-") + UAMConstants.uam);
		network.getAttributes().putAttribute(UAMConstants.uam + "MaxLinkFreeSpeed", uamMaxLinkSpeed);
		network.getAttributes().putAttribute(UAMConstants.uam + "DetourFactor", detourFactor);
		NetworkWriter netwriter = new NetworkWriter(network);
		netwriter.write(path + "\\" + networkFileName);

		System.out.println("done.");
	}

	private static void calculateStationDistances(Network network, Set<Id<Node>> stations, String filename)
			throws IOException, InterruptedException, ExecutionException {
		TransportModeNetworkFilter filter = new TransportModeNetworkFilter(network);
		Set<String> modes = new HashSet<>();
		modes.add(UAMConstants.uam);
		Network networkUAM = NetworkUtils.createNetwork();
		filter.filter(networkUAM, modes);

		int routers = Runtime.getRuntime().availableProcessors();
		TravelTime travelTime = new FreeSpeedTravelTime();
		LeastCostPathCalculator uamPathCalculator = new DijkstraFactory().createPathCalculator(networkUAM, new OnlyTimeDependentTravelDisutility(travelTime), travelTime);

		FileWriter csvWriter = new FileWriter(filename);
		csvWriter.append("fromStation,fromNode,toStation,toNode,cruisedistance,vtoldistance\n");

		for (Id<Node> uamStationOrigin : stations) {
			for (Id<Node> uamStationDestination : stations) {
				if (uamStationDestination.equals(uamStationOrigin))
					continue;

				// calculate distance
				Path path = uamPathCalculator.calcLeastCostPath(networkUAM.getNodes().get(uamStationOrigin),
						networkUAM.getNodes().get(uamStationDestination), 0.0, null, null);

				double cruisedistance = 0;
				double vtoldistance = 0;
				for (Link link : path.links) {
					if (link.getId().toString().contains(name_uam_vertical_link))
						vtoldistance += link.getLength();

					else
						cruisedistance += link.getLength();
				}

				// write out to file
				String fromStation = uamStationOrigin.toString().substring(name_uam_nodes.length(),
						uamStationOrigin.toString().length() - name_uam_station_flight_access.length());
				String toStation = uamStationDestination.toString().substring(name_uam_nodes.length(),
						uamStationDestination.toString().length() - name_uam_station_flight_access.length());
				csvWriter.append(fromStation).append(",").append(String.valueOf(uamStationOrigin)).append(",")
						.append(toStation).append(",").append(String.valueOf(uamStationDestination)).append(",")
						.append(String.valueOf(cruisedistance)).append(",").append(String.valueOf(vtoldistance))
						.append("\n");
			}
		}

		csvWriter.flush();
		csvWriter.close();
	}

	private static void addNode(Network network, Id<Node> station_id, double station_x, double station_y,
								double station_z) {
		try {
			NetworkUtils.createAndAddNode(network, station_id, createCoord(station_x, station_y, station_z));
		} catch (IllegalArgumentException e) {
			networkAlreadyContainsUam(e);
		}
	}

	private static void addLink(Network network, Id<Node> from, Id<Node> to, Set<String> modes) {
		addLink(network, from, to, modes, uamLinkCapacity, uamMaxLinkSpeed);
	}

	private static void addLink(Network network, Id<Node> from, Id<Node> to, Set<String> modes, double capacity,
								double freespeed) {
		addLink(network, from, to, modes, capacity, freespeed, no_length);
	}

	private static void addLink(Network network, Id<Node> from, Id<Node> to, Set<String> modes, double capacity,
								double freespeed, double length){
		Map<Id<Node>, ? extends Node> allNodes = network.getNodes();
		Node fromNode = allNodes.get(from);
		Node toNode = allNodes.get(to);

		Id<Link> id;
		boolean vertical = false;
		boolean horizontal = false;
		String f = from.toString();
		String t = to.toString();

		if ((f.endsWith(name_uam_station_ground_access) && t.endsWith(name_uam_station_flight_access))
				|| (f.endsWith(name_uam_station_flight_access) && t.endsWith(name_uam_station_ground_access))) {
			// station link
			id = Id.createLinkId(name_uam_station_link + from + "-" + to);
			vertical = true;
		} else if ((f.endsWith(name_uam_station_flight_level) && t.endsWith(name_uam_station_flight_level))) {
			// flight level link (horizontal/cruise)
			id = Id.createLinkId(name_uam_horizontal_link + from + "-" + to);
			horizontal = true;
		} else if ((f.endsWith(name_uam_station_flight_access) && !t.endsWith(name_uam_station_flight_access))
				|| (!f.endsWith(name_uam_station_flight_access) && t.endsWith(name_uam_station_flight_access))) {
			// flight access link (vertical)
			id = Id.createLinkId(name_uam_vertical_link + from + "-" + to);
			vertical = true;
		} else if ((f.endsWith(name_uam_station_ground_access) && !t.endsWith(name_uam_station_ground_access))
				|| (!f.endsWith(name_uam_station_ground_access) && t.endsWith(name_uam_station_ground_access))) {
			// ground access link
			id = Id.createLinkId(name_uam_ground_link + from + "-" + to);
		} else {
			System.err.println("WARN: Unknown link type for link between nodes: " + from.toString()
					+ " and " + to.toString());
			id = Id.createLinkId(UAMConstants.uam + from + "-" + to);
		}

		if (length == no_length)
			length = Math.max(CoordUtils.calcEuclideanDistance(fromNode.getCoord(), toNode.getCoord()) * detourFactor,
					min_link_length);

		Link link = network.getFactory().createLink(id, fromNode, toNode);
		link.setLength(length);
		link.setFreespeed(freespeed);
		link.setCapacity(capacity);
		link.setNumberOfLanes(permlanes);
		link.setAllowedModes(modes);

		if (vertical)
			link.getAttributes().putAttribute(UAMFlightSegments.ATTRIBUTE, UAMFlightSegments.VERTICAL);

		if (horizontal)
			link.getAttributes().putAttribute(UAMFlightSegments.ATTRIBUTE, UAMFlightSegments.HORIZONTAL);

		try {
			network.addLink(link);
		} catch (IllegalArgumentException e) {
			networkAlreadyContainsUam(e);
		}
	}

	private static Coord createCoord(double x, double y, double z) {
		if (useZCoordinate)
			return new Coord(x, y, z);
		else
			return new Coord(x, y);
	}

	private static String encode(String s) {
		return s.replaceAll("[^a-zA-Z0-9]", "x").trim().toLowerCase();
	}

	private static void networkAlreadyContainsUam(Exception e) {
		e.printStackTrace();
		System.err.println("#---------------------------------------------------------------------------------------#");
		System.err.println("ERROR: Could not create " + UAMConstants.uam
				+ " network, the input network already contains " + UAMConstants.uam
				+ " nodes/links.");
		System.err.println("FIX: Make sure your input network does not already contain "
				+ UAMConstants.uam + ", e.g. by deleting all " + UAMConstants.uam + "nodes and links.");
		System.err.println("#---------------------------------------------------------------------------------------#");
		System.exit(-1);
	}

	private static class UAMVehiclesXmlWriter extends MatsimXmlWriter {

		public void write(String file, List<String[]> stations, List<String[]> vehicles) {
			openFile(file);
			writeXmlHead();
			writeDoctype(UAMConstants.uam, name_uam_dtd);
			writeStartTag(UAMConstants.uam, Collections.emptyList());

			writeStartTag("stations", Collections.emptyList());
			writeStations(stations);
			writeEndTag("stations");

			writeStartTag("vehicleTypes", Collections.emptyList());
			writeVehicleTypes(vehicles);
			writeEndTag("vehicleTypes");

			writeStartTag("vehicles", Collections.emptyList());
			writeVehicles(stations, vehicles);
			writeEndTag("vehicles");

			writeEndTag(UAMConstants.uam);
			close();
		}

		private void writeStations(List<String[]> stations) {
			for (String[] station : Iterables.skip(stations, 1)) {
				List<Tuple<String, String>> atts = new ArrayList<>();

				int i = 0;
				String station_id = station[i++];
				String station_name = station[i];
				i = 10;
				String station_preflighttime = station[i++];
				String station_postflighttime = station[i++];
				String station_defaultwaittime = station[i];

				atts.add(new Tuple<>("id", station_id));
				atts.add(new Tuple<>("name", station_name));
				atts.add(new Tuple<>("preflighttime", station_preflighttime));
				atts.add(new Tuple<>("postflighttime", station_postflighttime));
				atts.add(new Tuple<>("defaultwaittime", station_defaultwaittime));
				atts.add(new Tuple<>("link",
						name_uam_station_link + name_uam_nodes + station_id + name_uam_station_ground_access + "-"
								+ name_uam_nodes + station_id + name_uam_station_flight_access));

				writeStartTag("station", atts, true);
			}
		}

		private void writeVehicleTypes(List<String[]> vehicles) {
			for (String[] vehicle : Iterables.skip(vehicles, 1)) {

				int i = 0;
				String type = vehicle[i];
				i = 4;
				String range = vehicle[i++];
				String capacity = vehicle[i++];
				String cruisespeed = vehicle[i++];
				String verticalspeed = vehicle[i++];
				String boardingtime = vehicle[i++];
				String deboardingtime = vehicle[i++];
				String turnaroundtime = vehicle[i];

				List<Tuple<String, String>> atts = new ArrayList<>();
				atts.add(new Tuple<>("id", type));
				atts.add(new Tuple<>("range", range));
				atts.add(new Tuple<>("capacity", capacity));
				atts.add(new Tuple<>("cruisespeed", cruisespeed));
				atts.add(new Tuple<>("verticalspeed", verticalspeed));
				atts.add(new Tuple<>("boardingtime", boardingtime));
				atts.add(new Tuple<>("deboardingtime", deboardingtime));
				atts.add(new Tuple<>("turnaroundtime", turnaroundtime));
				writeStartTag("vehicleType", atts, true);
			}
		}

		private void writeVehicles(List<String[]> stations, List<String[]> vehicles) {
			for (String[] vehicle : Iterables.skip(vehicles, 1)) {

				int i = 0;
				String type = vehicle[i++];
				double vehiclesperstation = Double.parseDouble(vehicle[i++]);
				String starttime = vehicle[i++];
				String endtime = vehicle[i];

				for (String[] station : Iterables.skip(stations, 1)) {
					for (int j = 0; j < vehiclesperstation; j++) {
						List<Tuple<String, String>> atts = new ArrayList<>();
						atts.add(new Tuple<>("id", station[0] + "-" + type + "-" + j));
						atts.add(new Tuple<>("type", type));
						atts.add(new Tuple<>("initialstation", station[0]));
						atts.add(new Tuple<>("starttime", starttime));
						atts.add(new Tuple<>("endtime", endtime));
						writeStartTag("vehicle", atts, true);
					}
				}
			}
		}

	}
}