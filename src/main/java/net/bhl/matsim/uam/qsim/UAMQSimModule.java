package net.bhl.matsim.uam.qsim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.DvrpVehicleSpecification;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.optimizer.VrpOptimizer;
import org.matsim.contrib.dvrp.passenger.PassengerEngine;
import org.matsim.contrib.dvrp.passenger.PassengerEngineQSimModule;
import org.matsim.contrib.dvrp.passenger.PassengerRequestCreator;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.run.DvrpMode;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.contrib.dvrp.schedule.StayTask;
import org.matsim.contrib.dvrp.tracker.OnlineTrackerListener;
import org.matsim.contrib.dvrp.vrpagent.VrpAgentLogic.DynActionCreator;
import org.matsim.contrib.dvrp.vrpagent.VrpAgentSourceQSimModule;
import org.matsim.contrib.dvrp.vrpagent.VrpLeg;
import org.matsim.contrib.dvrp.vrpagent.VrpLegFactory;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.components.QSimComponentsConfigurator;

import com.google.inject.Provides;
import com.google.inject.Singleton;

import net.bhl.matsim.uam.data.UAMFleetData;
import net.bhl.matsim.uam.dispatcher.UAMClosestRangedPreferPooledDispatcher;
import net.bhl.matsim.uam.dispatcher.UAMDispatcher;
import net.bhl.matsim.uam.dispatcher.UAMDispatcherListener;
import net.bhl.matsim.uam.dispatcher.UAMManager;
import net.bhl.matsim.uam.infrastructure.UAMVehicle;
import net.bhl.matsim.uam.infrastructure.readers.UAMXMLReader;
import net.bhl.matsim.uam.passenger.UAMRequestCreator;
import net.bhl.matsim.uam.run.UAMConstants;
import net.bhl.matsim.uam.schedule.UAMOptimizer;
import net.bhl.matsim.uam.schedule.UAMSingleRideAppender;
import net.bhl.matsim.uam.schedule.UAMTaskType;
import net.bhl.matsim.uam.vrpagent.UAMActionCreator;

/**
 * A MATSim Abstract Module for classes used by Qsim for UAM simulation.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMQSimModule extends AbstractDvrpModeQSimModule {
	public final static String COMPONENT_NAME = UAMConstants.uam.toUpperCase() + "Extension";

	public UAMQSimModule() {
		super(UAMConstants.uam);
	}

	static public QSimComponentsConfigurator activateModes() {
		return DvrpQSimComponents.activateModes(UAMConstants.uam);
	}

	@Override
	protected void configureQSim() {

		addModalComponent(BookingEngine.class, modalProvider(getter -> {
			Scenario scenario = getter.get(Scenario.class);
			PassengerEngine passengerEngine = getter.getModal(PassengerEngine.class);
			EventsManager eventsManager = getter.get(EventsManager.class);
			Network network = getter.getModal(Network.class);
			return new BookingEngine(scenario, passengerEngine, eventsManager, network);
		}));

		bindModal(PassengerRequestCreator.class).to(UAMRequestCreator.class);
		bindModal(DynActionCreator.class).to(UAMActionCreator.class);
		bindModal(VrpOptimizer.class).to(UAMOptimizer.class);

		bind(UAMOptimizer.class);
		bind(UAMDispatcherListener.class);

		bindModal(UAMDispatcherListener.class).to(UAMDispatcherListener.class);
		bindModal(Fleet.class).to(UAMFleetData.class);

		bindModal(UAMSingleRideAppender.class).to(UAMSingleRideAppender.class);
		bind(UAMSingleRideAppender.class);

		addModalQSimComponentBinding().to(UAMDispatcherListener.class);
		addModalQSimComponentBinding().to(UAMOptimizer.class);

		install(new VrpAgentSourceQSimModule(getMode()));
		install(new PassengerEngineQSimModule(getMode()));
	}

	@Provides
	@Singleton
	VrpLegFactory provideLegCreator(@DvrpMode(UAMConstants.uam) VrpOptimizer optimizer, QSim qSim) {
		return new VrpLegFactory() {
			@Override
			public VrpLeg create(DvrpVehicle vehicle) {
				return VrpLegFactory.createWithOnlineTracker(TransportMode.car, vehicle,
						(OnlineTrackerListener) optimizer, qSim.getSimTimer());
			}
		};
	}

	@Provides
	@Singleton
	List<UAMDispatcher> provideDispatchers(UAMSingleRideAppender appender, UAMManager uamManager,
			@DvrpMode(UAMConstants.uam) Network network, @DvrpMode(UAMConstants.uam) Fleet data) {

		UAMDispatcher dispatcher = new UAMClosestRangedPreferPooledDispatcher(appender, uamManager, network, data);

		List<UAMDispatcher> dispatchers = new ArrayList<>();
		dispatchers.add(dispatcher);
		return dispatchers;
	}

	@Provides
	@Singleton
	public UAMFleetData provideData(UAMXMLReader uamReader, UAMManager uamManager) {
		Map<Id<DvrpVehicle>, UAMVehicle> returnVehicles = new HashMap<>();

		for (DvrpVehicleSpecification specification : uamReader.getFleetSpecification().getVehicleSpecifications()
				.values()) {

			returnVehicles.put(specification.getId(),
					new UAMVehicle(specification, uamReader.getVehicles().get(specification.getId()).getStartLink(),
							uamReader.getVehicles().get(specification.getId()).getInitialStationId(),
							uamReader.getVehicles().get(specification.getId()).getVehicleType()));
		}

		for (DvrpVehicle veh : returnVehicles.values()) {
			// create a new Fleet every new iteration
			veh.getSchedule().addTask(new StayTask(UAMTaskType.STAY, veh.getServiceBeginTime(),
					Double.POSITIVE_INFINITY, veh.getStartLink()));
			returnVehicles.put(veh.getId(), (UAMVehicle) veh);
		}

		// populate manager here
		return new UAMFleetData(returnVehicles);
	}

}
