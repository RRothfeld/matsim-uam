package net.bhl.matsim.uam.router;

import java.util.Map;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.pt.config.TransitConfigGroup;
import org.matsim.pt.router.TransitRouter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

import net.bhl.matsim.uam.config.UAMConfigGroup;
import net.bhl.matsim.uam.data.UAMStationConnectionGraph;
import net.bhl.matsim.uam.data.WaitingStationData;
import net.bhl.matsim.uam.dispatcher.UAMManager;
import net.bhl.matsim.uam.run.UAMConstants;

/**
 * This class provides the routing module for UAM mode.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMRoutingModuleProvider implements Provider<RoutingModule> {

	@Inject
	@Named("uam_car")
	Network networkCar;
	@Inject(optional = true)
	@Named(TransportMode.pt)
	RoutingModule transitRouting;
	@Inject
	private Scenario scenario;
	@Inject
	private UAMManager uamManager;
	@Inject
	@Named(UAMConstants.uam)
	private LeastCostPathCalculator uamPathCalculator;
	@Inject
	private LeastCostPathCalculatorFactory lcpcf;
	@Inject
	private Map<String, TravelTime> travelTimes;
	@Inject
	private Map<String, TravelDisutilityFactory> travelDisutilityFactories;
	@Inject
	private UAMConfigGroup uamConfig;
	@Inject
	private TransitConfigGroup transitConfig;
	@Inject(optional = true)
	private TransitRouter transitRouter;
	@Inject
	private WaitingStationData waitingData;

	@Inject
	private UAMStationConnectionGraph stationConnectionutilities;

	@Override
	public RoutingModule get() {
		TravelTime travelTime = travelTimes.get(TransportMode.car);

		TravelDisutility travelDisutility = travelDisutilityFactories.get(TransportMode.car)
				.createTravelDisutility(travelTime);

		LeastCostPathCalculator pathCalculator = lcpcf.createPathCalculator(networkCar, travelDisutility, travelTime);

		if (scenario.getConfig().transit().isUseTransit()) {
			return new UAMCachedIntermodalRoutingModule(scenario, uamManager.getStations(), uamPathCalculator, pathCalculator,
					networkCar, transitRouter, uamConfig, transitConfig, transitRouting, waitingData,
					stationConnectionutilities);
		} else {
			return new UAMCachedIntermodalRoutingModule(scenario, uamManager.getStations(), uamPathCalculator, pathCalculator,
					networkCar, uamConfig, transitConfig, waitingData, stationConnectionutilities);
		}
	}
}