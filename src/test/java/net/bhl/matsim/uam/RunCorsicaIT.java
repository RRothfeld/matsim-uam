package net.bhl.matsim.uam;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.config.groups.QSimConfigGroup.StarttimeInterpretation;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.config.groups.SubtourModeChoiceConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;

import com.google.common.collect.ImmutableSet;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import net.bhl.matsim.uam.config.UAMConfigGroup;
import net.bhl.matsim.uam.qsim.UAMQSimModule;
import net.bhl.matsim.uam.qsim.UAMSpeedModule;
import net.bhl.matsim.uam.run.UAMModule;

public class RunCorsicaIT {
	@Test
	public void testCorsica() {
		System.out.println(RunCorsicaIT.class.getResource("/corsica/corsica_config.xml"));

		Config config = ConfigUtils.loadConfig(RunCorsicaIT.class.getResource("/corsica/corsica_config.xml"));
		config.controler().setLastIteration(2);

		{
			// Remove some standard eqasim config groups
			config.getModules().remove("DiscreteModeChoice");
			config.getModules().remove("eqasim");
			config.getModules().remove("eqasim:calibration");

			// Replace some standard eqasim settings

			for (StrategySettings strategy : config.strategy().getStrategySettings()) {
				if (strategy.getStrategyName().equals("DiscreteModeChoice")) {
					strategy.setStrategyName("SubtourModeChoice");
				} else if (strategy.getStrategyName().equals("KeepLastSelected")) {
					strategy.setStrategyName("ChangeExpBeta");
				}
			}
			String[] modes = { "car", "pt", "walk", "uam", "bike" };
			((SubtourModeChoiceConfigGroup) config.getModules().get("subtourModeChoice")).setModes(modes);
			config.strategy().setPlanSelectorForRemoval("WorstPlanSelector");
			config.transit().setUseTransit(true);
			config.transit().setUsingTransitInMobsim(true);
		}

		{
			// Add DVRP configuration
			DvrpConfigGroup dvrpConfig = new DvrpConfigGroup();
			config.addModule(dvrpConfig);

			DvrpConfigGroup.get(config).setNetworkModes(ImmutableSet.of("uam"));

			// Some necessary DVRP settings
			config.qsim().setSimStarttimeInterpretation(StarttimeInterpretation.onlyUseStarttime);
			config.qsim().setStartTime(0.0);
		}

		{
			// Add UAM configuration
			UAMConfigGroup uamConfig = new UAMConfigGroup();
			config.addModule(uamConfig);

			uamConfig.setInputFile("uam_network.xml");
			uamConfig.setAccessEgressModes(ImmutableSet.of("car"));
			uamConfig.setSearchRadius(10 * 1e3);

			// Add UAM scoring
			config.planCalcScore().addModeParams(new ModeParams("access_uam_car"));
			config.planCalcScore().addModeParams(new ModeParams("egress_uam_car"));
			config.planCalcScore().addModeParams(new ModeParams("uam"));
			config.planCalcScore()
					.addActivityParams(new ActivityParams("uam_interaction").setScoringThisActivityAtAll(false));
		}

		// Load scenario
		Scenario scenario = ScenarioUtils.loadScenario(config);

		{
			// Modify network to have UAM components

			Network network = scenario.getNetwork();
			NetworkFactory factory = network.getFactory();

			// Prepare the links of the hubs

			Link hubAjaccio = network.getLinks().get(Id.createLinkId("62245"));
			Link hubBastia = network.getLinks().get(Id.createLinkId("15431"));

			for (Link hub : Arrays.asList(hubAjaccio, hubBastia)) {
				Set<String> modes = new HashSet<>(hub.getAllowedModes());
				modes.add("uam");
				hub.setAllowedModes(modes);
				hub.getAttributes().putAttribute("type", "uam_horizontal");
			}

			// Prepare the connecting UAM links

			Link linkA = factory.createLink(Id.createLinkId("uamA"), hubAjaccio.getToNode(), hubBastia.getFromNode());
			Link linkB = factory.createLink(Id.createLinkId("uamB"), hubBastia.getToNode(), hubAjaccio.getFromNode());

			for (Link link : Arrays.asList(linkA, linkB)) {
				link.setAllowedModes(Collections.singleton("uam"));
				link.getAttributes().putAttribute("type", "uam_horizontal");
				link.setFreespeed(100.0);
				link.setCapacity(1000000.0);
				network.addLink(link);
			}
		}

		for (Person person : scenario.getPopulation().getPersons().values()) {
			for (Plan plan : person.getPlans()) {
				for (Trip trip : TripStructureUtils.getTrips(plan)) {

					for (PlanElement pe : trip.getTripElements()) {

						if (pe instanceof Leg) {
							((Leg) pe).setRoute(null);
						}
					}
				}
			}
		}

		// Set up controller
		Controler controller = new Controler(scenario);

		controller.addOverridingModule(new DvrpModule());
		controller.addOverridingModule(new UAMModule());
		controller.addOverridingQSimModule(new UAMSpeedModule());
		controller.addOverridingModule(new SwissRailRaptorModule());
		controller.configureQSimComponents(UAMQSimModule.activateModes());

		controller.run();
	}
}
