package net.bhl.matsim.uam.run;

import java.util.Collections;

import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.passenger.DefaultPassengerRequestValidator;
import org.matsim.contrib.dvrp.passenger.PassengerRequestValidator;
import org.matsim.contrib.dvrp.router.DvrpModeRoutingNetworkModule;
import org.matsim.contrib.dvrp.run.DvrpMode;
import org.matsim.contrib.dvrp.run.DvrpModes;
import org.matsim.contrib.dvrp.vrpagent.VrpAgentSourceQSimModule;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.MainModeIdentifierImpl;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import net.bhl.matsim.uam.config.UAMConfigGroup;
import net.bhl.matsim.uam.data.UAMStationConnectionGraph;
import net.bhl.matsim.uam.data.WaitingStationData;
import net.bhl.matsim.uam.dispatcher.UAMManager;
import net.bhl.matsim.uam.events.UAMDemand;
import net.bhl.matsim.uam.infrastructure.UAMStations;
import net.bhl.matsim.uam.infrastructure.readers.UAMXMLReader;
import net.bhl.matsim.uam.listeners.UAMListener;
import net.bhl.matsim.uam.listeners.UAMShutdownListener;
import net.bhl.matsim.uam.qsim.UAMQSimModule;
import net.bhl.matsim.uam.router.UAMMainModeIdentifier;
import net.bhl.matsim.uam.router.UAMRoutingModuleProvider;
import net.bhl.matsim.uam.scoring.UAMScoringFunctionFactory;

/**
 * A MATSim Abstract Module for the classes used by the UAM simulation.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMModule extends AbstractModule {
	@Override
	public void install() {
		DvrpModes.registerDvrpMode(binder(), UAMConstants.uam);
		install(new DvrpModeRoutingNetworkModule(UAMConstants.uam, true));

		bind(DvrpModes.key(PassengerRequestValidator.class, UAMConstants.uam))
				.toInstance(new DefaultPassengerRequestValidator());
		installQSimModule(new UAMQSimModule());

		// bining our own scoring function factory
		bind(ScoringFunctionFactory.class).to(UAMScoringFunctionFactory.class).asEagerSingleton();

		// adding UAMDemand as singleton
		bind(UAMDemand.class).asEagerSingleton();

		// controler listeners need to be binded
		addControlerListenerBinding().to(UAMManager.class);
		addControlerListenerBinding().to(UAMListener.class);
		addControlerListenerBinding().to(UAMShutdownListener.class);
		addControlerListenerBinding().to(WaitingStationData.class);

		bind(WaitingStationData.class).asEagerSingleton();
		// bindng of event handlers
		addEventHandlerBinding().to(UAMDemand.class);

		// we need to bind our router for the uam trips
		addRoutingModuleBinding(UAMConstants.uam).toProvider(UAMRoutingModuleProvider.class);

		// we still need to provide a way to identify our trips
		// as being uam trips.
		// This is for instance used at re-routing.
		bind(MainModeIdentifier.class).toInstance(new UAMMainModeIdentifier(new MainModeIdentifierImpl()));

		// here we provide vehicles and network to be used for uam trips
		bind(VehicleType.class).annotatedWith(Names.named(UAMConstants.uam))
				.toInstance(VehicleUtils.getDefaultVehicleType());

		bind(VehicleType.class).annotatedWith(Names.named(VrpAgentSourceQSimModule.DVRP_VEHICLE_TYPE))
				.toInstance(VehicleUtils.getDefaultVehicleType());

		bind(TravelTime.class).annotatedWith(Names.named(UAMConstants.uam)).toInstance(new FreeSpeedTravelTime());

		// TODO: Not sure if we need to adjust something here?
		// bind(TravelTime.class).annotatedWith(Names.named(UAMConstants.uam))
		// .to(Key.get(TravelTime.class,
		// Names.named(DvrpTravelTimeModule.DVRP_ESTIMATED)));
	}

	@Provides
	@Singleton
	@Named("uam_car")
	public Network provideCarNetwork(Network network) {
		Network carNetwork = NetworkUtils.createNetwork();
		new TransportModeNetworkFilter(network).filter(carNetwork, Collections.singleton("car"));
		new NetworkCleaner().run(carNetwork);
		return carNetwork;
	}

	@Provides
	@Singleton
	@Named(UAMConstants.uam)
	private LeastCostPathCalculator provideUAMLeastCostPathCalculator(UAMConfigGroup uamConfig,
			@Named(UAMConstants.uam) TravelTime travelTime, @DvrpMode(UAMConstants.uam) Network network) {
		return new DijkstraFactory().createPathCalculator(network, new OnlyTimeDependentTravelDisutility(travelTime),
				travelTime);
	}

	@Provides
	@Singleton
	public UAMStationConnectionGraph provideUAMStationConnectionGraph(UAMManager uamManager,
			@Named(UAMConstants.uam) LeastCostPathCalculator uamPathCalculator) {
		return new UAMStationConnectionGraph(uamManager, uamPathCalculator);
	}

	@Provides
	@Singleton
	public UAMManager provideUAMManager(Network network, UAMStations stations, UAMXMLReader reader) {
		return new UAMManager(network, stations, reader.getVehicles());
	}

	@Provides
	@Singleton
	public UAMXMLReader provideReader(@DvrpMode(UAMConstants.uam) Network network, UAMConfigGroup uamConfigGroup) {
		UAMXMLReader uamReader = new UAMXMLReader(network);
		uamReader.readURL(ConfigGroup.getInputFileURL(getConfig().getContext(), uamConfigGroup.getInputFile()));
		return uamReader;
	}

	@Provides
	@Singleton
	public UAMStations proideUAMStations(UAMXMLReader uamReader, Network network) {
		return new UAMStations(uamReader.getStations(), network);
	}

}
