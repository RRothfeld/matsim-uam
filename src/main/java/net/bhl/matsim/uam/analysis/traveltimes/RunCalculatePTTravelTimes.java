package net.bhl.matsim.uam.analysis.traveltimes;

import ch.sbb.matsim.routing.pt.raptor.*;
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
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TeleportationRoutingModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.router.TransitRouter;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This script generates csv file containing estimated travel times by Pt for
 * trips. The trips file must contain departure time and origin and destination
 * coordinates for the trips.
 *
 * @author Aitanm (Aitan Militao), RRothfeld (Raoul Rothfeld)
 */

public class RunCalculatePTTravelTimes {
	private static final int processes = Runtime.getRuntime().availableProcessors();
	private static final Logger log = Logger.getLogger(RunCalculatePTTravelTimes.class);
	private static ArrayBlockingQueue<TransitRouter> ptRouters = new ArrayBlockingQueue<>(processes);

	private static boolean writeDescription = true;

	public static void main(String[] args) throws Exception {
		System.out.println(
				"ARGS: config.xml* trips.csv* outputfile-name* write-description");
		System.out.println("(* required)");

		// ARGS
		int j = 0;
		String configInput = args[j++];
		String tripsInput = args[j++];
		String outputPath = args[j++];

		if (args.length > 3)
			writeDescription = Boolean.parseBoolean(args[j]);

		// READ NETWORK
		Config config = ConfigUtils.loadConfig(configInput, new UAMConfigGroup());
		Scenario scenario = ScenarioUtils.createScenario(config);
		ScenarioUtils.loadScenario(scenario);
		Network network = scenario.getNetwork();

		RaptorStaticConfig raptorStaticConfig = RaptorUtils.createStaticConfig(config);
		SwissRailRaptorData data = SwissRailRaptorData.create(scenario.getTransitSchedule(), raptorStaticConfig,
				network);

		//Provide routers
		for (int i = 0; i < processes; i++) {
			Map<String, RoutingModule> router = new HashMap<>();
			router.put(TransportMode.pt, new TeleportationRoutingModule(TransportMode.pt,
					scenario.getPopulation().getFactory(), 0, 1.5));
			ptRouters.add(new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(config),
					new LeastCostRaptorRouteSelector(),
					new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), router)));
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

			es.execute(new PTTravelTimeCalculator(threadCounter, network, trip));
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

	static class PTTravelTimeCalculator implements Runnable {

		private TripItem trip;
		private Network network;
		private ThreadCounter threadCounter;
		private TransitRouter transitRouter;

		PTTravelTimeCalculator(ThreadCounter threadCounter, Network network, TripItem trip) {
			this.trip = trip;
			this.network = network;
			this.threadCounter = threadCounter;
		}

		@Override
		public void run() {
			threadCounter.register();

			try {
				transitRouter = ptRouters.take();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			Link from = NetworkUtils.getNearestLink(network, trip.origin);
			Link to = NetworkUtils.getNearestLink(network, trip.destination);

			try {
				List<Leg> legs = transitRouter.calcRoute(new LinkWrapperFacility(from),
						new LinkWrapperFacility(to), trip.departureTime, null);
				double time = 0;
				double distance = 0;
				StringBuilder routeList = new StringBuilder();
				for (Leg leg : legs) {
					if (time != 0 && writeDescription)
						routeList.append("->");
					time += leg.getTravelTime();
					distance += leg.getRoute().getDistance();
					if (writeDescription) {
						routeList.append("[mode:").append(leg.getMode()).append("]");
						routeList.append("[start:").append(leg.getRoute().getStartLinkId()).append("]");
						routeList.append("[end:").append(leg.getRoute().getEndLinkId()).append("]");
						routeList.append("[time:").append(leg.getTravelTime()).append("]");
						routeList.append("[distance:").append(leg.getRoute().getDistance()).append("]");
					}
				}
				trip.travelTime = time;
				trip.distance = distance;
				trip.description = routeList.toString();
			} catch (NullPointerException e) {
				// Do nothing; failed trip will show as null in results.
			}

			try {
				ptRouters.put(transitRouter);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			threadCounter.deregister();
		}
	}
}
