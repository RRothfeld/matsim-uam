package net.bhl.matsim.uam.qsim;

import com.google.inject.Provides;
import com.google.inject.Singleton;

import net.bhl.matsim.uam.infrastructure.readers.UAMXMLReader;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;
import org.matsim.core.mobsim.qsim.qnetsimengine.ConfigurableQNetworkFactory;
import org.matsim.core.mobsim.qsim.qnetsimengine.QNetworkFactory;
import org.matsim.core.mobsim.qsim.qnetsimengine.linkspeedcalculator.DefaultLinkSpeedCalculator;

import java.util.Map;

/**
 * A MATSim Abstract Module for classes used by UAM simulation regarding link
 * speeds in the simulation.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMSpeedModule extends AbstractQSimModule {

	@Override
	public void configureQSim() {

	}

	@Provides
	@Singleton
	public QNetworkFactory provideQNetworkFactory(EventsManager events, Scenario scenario,
												  UAMLinkSpeedCalculator linkSpeedCalculator) {
		ConfigurableQNetworkFactory networkFactory = new ConfigurableQNetworkFactory(events, scenario);
		networkFactory.setLinkSpeedCalculator(linkSpeedCalculator);
		return networkFactory;
	}

	@Provides
	@Singleton
	public UAMLinkSpeedCalculator provideUAMLinkSpeedCalculator(UAMXMLReader uamReader) {
		DefaultLinkSpeedCalculator delegate = new DefaultLinkSpeedCalculator();
		return new UAMLinkSpeedCalculator(uamReader.getMapVehicleVerticalSpeeds(), uamReader.getMapVehicleHorizontalSpeeds(), delegate);
	}
}
