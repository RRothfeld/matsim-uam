package net.bhl.matsim.uam.analysis.traveltimes;

import com.opencsv.CSVParser;
import net.bhl.matsim.uam.analysis.traveltimes.utils.ThreadCounter;
import net.bhl.matsim.uam.analysis.traveltimes.utils.TripItem;
import net.bhl.matsim.uam.analysis.traveltimes.utils.TripItemReader;
import net.bhl.matsim.uam.config.UAMConfigGroup;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Injector;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.router.AStarLandmarksFactory;
import org.matsim.core.router.util.*;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This script generates csv file containing estimated travel times by CAR for
 * trips. The trips file must contain departure time and origin and destination
 * coordinates for the trips.
 *
 * @author Aitanm (Aitan Militao), RRothfeld (Raoul Rothfeld)
 */

public class RunCalculateCarTravelTimes {
	private static final int processes = Runtime.getRuntime().availableProcessors();
	private static final Logger log = Logger.getLogger(RunCalculateCarTravelTimes.class);
	private static ArrayBlockingQueue<LeastCostPathCalculator> carRouters = new ArrayBlockingQueue<>(processes);

	private static boolean writeDescription = true;

	public static void main(String[] args) throws Exception {
		System.out.println(
				"ARGS: config.xml* trips.csv* outputfile-name.csv* write-description");
		System.out.println("(* required)");

		// ARGS
		int j = 0;
		String configInput = args[j++];
		String tripsInput = args[j++];
		String outputPath = args[j++];

		if (args.length > 3)
			writeDescription = Boolean.parseBoolean(args[j]);

		Config config = ConfigUtils.loadConfig(configInput, new UAMConfigGroup());
		Scenario scenario = ScenarioUtils.createScenario(config);
		ScenarioUtils.loadScenario(scenario);
		Network network = scenario.getNetwork();

		// CREATE CAR NETWORK
		TransportModeNetworkFilter filter = new TransportModeNetworkFilter(network);
		Network networkCar = NetworkUtils.createNetwork();
		Set<String> modesCar = new HashSet<>();
		modesCar.add(TransportMode.car);
		filter.filter(networkCar, modesCar);

		// LEAST COST PATH CALCULATOR
		TravelTimeCalculator.Builder builder = new TravelTimeCalculator.Builder(network);
		builder.configure(config.travelTimeCalculator());
		TravelTimeCalculator ttc = builder.build();
		TravelTime travelTime = ttc.getLinkTravelTimes();
		TravelDisutility travelDisutility = TravelDisutilityUtils
				.createFreespeedTravelTimeAndDisutility(config.planCalcScore());

		com.google.inject.Injector injector = Injector.createInjector(config, new AbstractModule() {
			@Override
			public void install() {
				bind(LeastCostPathCalculatorFactory.class).to(AStarLandmarksFactory.class);
			}
		});
		LeastCostPathCalculatorFactory pathCalculatorFactory = injector
				.getInstance(LeastCostPathCalculatorFactory.class); // AStarLandmarksFactory

		// Provide routers
		for (int i = 0; i < processes; i++) {
			carRouters.add(pathCalculatorFactory.createPathCalculator(networkCar, travelDisutility, travelTime));
		}

		// READ TRIPS INPUT
		List<TripItem> trips = TripItemReader.getTripItems(tripsInput);

		// Calculate travel times
		log.info("Calculating travel times...");
		int counter = 1;
		ThreadCounter threadCounter = new ThreadCounter();
		ExecutorService es = Executors.newFixedThreadPool(processes);
		for (TripItem trip : trips) {
			if (trips.size() < 100 || counter % (trips.size() / 100) == 0)
				log.info("Calculation completion: " + counter + "/" + trips.size() + " ("
						+ String.format("%.0f", (double) counter / trips.size() * 100) + "%).");

			while (threadCounter.getProcesses() >= processes - 1)
				Thread.sleep(200);

			es.execute(new CarTravelTimeCalculator(threadCounter, networkCar, trip));
			counter++;
		}
		es.shutdown();
		// Make sure that the file is not written before all threads are finished
		while (!es.isTerminated())
			Thread.sleep(200);

		// Writes output file
		log.info("Writing travelTimes file...");
		write(outputPath, trips);
		log.info("...done.");
	}

	public static void write(String outputPath, List<TripItem> trips) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath)));

		writer.write(formatHeader() + "\n");
		for (TripItem trip : trips) {
			writer.write(String.join(String.valueOf(CSVParser.DEFAULT_SEPARATOR),
					new String[]{String.valueOf(trip.origin.getX()), String.valueOf(trip.origin.getY()),
							String.valueOf(trip.destination.getX()), String.valueOf(trip.destination.getY()),
							String.valueOf(trip.departureTime), String.valueOf(trip.travelTime),
							String.valueOf(trip.distance), trip.description})
					+ "\n");
		}

		writer.flush();
		writer.close();
	}

	private static String formatHeader() {
		return String.join(String.valueOf(CSVParser.DEFAULT_SEPARATOR),
				new String[]{"origin_x", "origin_y", "destination_x", "destination_y",
				"departure_time", "travel_time", "distance", "description"});
	}

	private static Path estimatePath(Link from, Link to, double departureTime, Network carNetwork,
									 LeastCostPathCalculator pathCalculator) {
		if (carNetwork.getLinks().get(from.getId()) == null)
			from = NetworkUtils.getNearestLinkExactly(carNetwork, from.getCoord());

		if (carNetwork.getLinks().get(to.getId()) == null)
			to = NetworkUtils.getNearestLinkExactly(carNetwork, to.getCoord());

		return pathCalculator.calcLeastCostPath(from.getFromNode(), to.getToNode(), departureTime, null, null);
	}

	static class CarTravelTimeCalculator implements Runnable {

		private TripItem trip;
		private ThreadCounter threadCounter;
		private Network networkCar;
		private LeastCostPathCalculator plcpccar;

		CarTravelTimeCalculator(ThreadCounter threadCounter, Network network, TripItem trip) {
			this.threadCounter = threadCounter;
			this.networkCar = network;
			this.trip = trip;
		}

		@Override
		public void run() {
			threadCounter.register();

			try {
				plcpccar = carRouters.take();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			Link from = NetworkUtils.getNearestLink(networkCar, trip.origin);
			Link to = NetworkUtils.getNearestLink(networkCar, trip.destination);

			try {
				Path path = estimatePath(from, to, trip.departureTime, networkCar, plcpccar);
				double distance = 0;
				StringBuilder linksList = new StringBuilder();
				for (Link link : path.links) {
					if (distance != 0 && writeDescription)
						linksList.append("->");
					distance += link.getLength();
					if (writeDescription)
						linksList.append("[link:").append(link.getId().toString()).append("]");
				}

				trip.distance = distance;
				trip.travelTime = path.travelTime;
				trip.description = linksList.toString();
			} catch (NullPointerException e) {
				// Do nothing; failed trip will show as null in results.
			}

			try {
				carRouters.put(plcpccar);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			threadCounter.deregister();
		}
	}
}
