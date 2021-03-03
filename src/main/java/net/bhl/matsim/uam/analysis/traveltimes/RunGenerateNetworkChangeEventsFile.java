package net.bhl.matsim.uam.analysis.traveltimes;

import net.bhl.matsim.uam.run.UAMConstants;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.TravelTimeCalculatorConfigGroup;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkChangeEvent;
import org.matsim.core.network.NetworkChangeEvent.ChangeType;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkChangeEventsWriter;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;

import java.util.ArrayList;
import java.util.List;

/**
 * This script generates a NetworkChangeEvents file containing changes in the
 * network throughout the day. Necessary inputs are in the following order:
 * -Network file; -Events file; -output;
 *
 * @author Aitanm (Aitan Militao), RRothfeld (Raoul Rothfeld)
 */

public class RunGenerateNetworkChangeEventsFile {
	private double timeStep = 15 * 60; // Time step set to 15 minutes
	private double minFreeSpeed = 3;
	private double endTime = 30 * 60 * 60; // end time set to 30 hours

	public RunGenerateNetworkChangeEventsFile() {
	}

	public static void main(String[] args) throws Exception {
		// ARGS network-file events-file network-change-events-output
		int j = 0;
		String networkInput = args[j++];
		String eventsFileInput = args[j++];
		String networkEventsChangeFile = args[j];

		Config config = ConfigUtils.createConfig();
		config.network().setInputFile(networkInput);

		RunGenerateNetworkChangeEventsFile fileGenerator = new RunGenerateNetworkChangeEventsFile();
		fileGenerator.generateNetworkChangeEventsFile(networkInput, eventsFileInput, networkEventsChangeFile, config);

	}

	public void generateNetworkChangeEventsFile(String networkInput, String eventsFileInput,
												String networkEventsChangeFile, Config config) {
		// Generate networkChangeEvents file for the Time-Dependent Network
		Network networkForReader = NetworkUtils.createNetwork();
		new MatsimNetworkReader(networkForReader).readFile(networkInput);
		TravelTimeCalculator tcc = readEventsIntoTravelTimeCalculator(networkForReader, eventsFileInput,
				config.travelTimeCalculator());
		config.qsim().setEndTime(endTime);
		List<NetworkChangeEvent> networkChangeEvents = createNetworkChangeEvents(networkForReader, tcc,
				config.qsim().getEndTime().seconds(), timeStep, minFreeSpeed);
		new NetworkChangeEventsWriter().write(networkEventsChangeFile, networkChangeEvents);
	}

	private TravelTimeCalculator readEventsIntoTravelTimeCalculator(Network network, String eventsFile,
																	TravelTimeCalculatorConfigGroup group) {
		EventsManager manager = EventsUtils.createEventsManager();

		TravelTimeCalculator.Builder builder = new TravelTimeCalculator.Builder(network);
		builder.configure(group);
		TravelTimeCalculator ttc = builder.build();
		manager.addHandler(ttc);

		new MatsimEventsReader(manager).readFile(eventsFile);
		return ttc;
	}

	private List<NetworkChangeEvent> createNetworkChangeEvents(Network network, TravelTimeCalculator tcc,
															   Double endTime, Double timeStep, Double MinFreeSpeed) {
		List<NetworkChangeEvent> networkChangeEvents = new ArrayList<>();
		for (Link l : network.getLinks().values()) {

			// skip UAM links
			if (l.getAllowedModes().contains(UAMConstants.uam)) continue;

			double length = l.getLength();
			double previousTravelTime = l.getLength() / l.getFreespeed();

			for (double time = 0; time < endTime; time = time + timeStep) {

				double newTravelTime = tcc.getLinkTravelTimes().getLinkTravelTime(l, time, null, null);

				if (newTravelTime != previousTravelTime) {
					NetworkChangeEvent nce = new NetworkChangeEvent(time);
					nce.addLink(l);
					double newFreespeed = length / newTravelTime;
					if (newFreespeed < MinFreeSpeed)
						newFreespeed = MinFreeSpeed;
					if (Double.isInfinite(newFreespeed))
						newFreespeed = Double.MAX_VALUE;

					NetworkChangeEvent.ChangeValue freespeedChange = new NetworkChangeEvent.ChangeValue(
							ChangeType.ABSOLUTE_IN_SI_UNITS, newFreespeed);
					nce.setFreespeedChange(freespeedChange);

					networkChangeEvents.add(nce);
					previousTravelTime = newTravelTime;
				}
			}
		}
		return networkChangeEvents;
	}

}
