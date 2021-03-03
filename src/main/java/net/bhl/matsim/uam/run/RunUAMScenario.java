package net.bhl.matsim.uam.run;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contribs.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.config.groups.QSimConfigGroup.StarttimeInterpretation;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import net.bhl.matsim.uam.config.UAMConfigGroup;
import net.bhl.matsim.uam.qsim.UAMQSimModule;
import net.bhl.matsim.uam.qsim.UAMSpeedModule;

/**
 * The RunUAMScenario program start a MATSim run including Urban Air Mobility
 * capabilities.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class RunUAMScenario {

	private static UAMConfigGroup uamConfigGroup;
	private static CommandLine cmd;
	private static String path;
	private static Config config;
	private static Controler controler;
	private static Scenario scenario;

	public static void main(String[] args) {
		parseArguments(args);
		setConfig(path);
		createScenario();
		createControler().run();
	}

	public static void parseArguments(String[] args) {
		try {
			cmd = new CommandLine.Builder(args).allowOptions("config-path").build();

			if (cmd.hasOption("config-path"))
				path = cmd.getOption("config-path").get();
			else
				path = args[0];
		} catch (ConfigurationException e) {
			e.printStackTrace();
		}
		uamConfigGroup = new UAMConfigGroup();
	}

	public static Config createConfig() {
		return config = ConfigUtils.createConfig(uamConfigGroup, new DvrpConfigGroup());
	}

	public static Config setConfig(String path) {
		return config = ConfigUtils.loadConfig(path, uamConfigGroup, new DvrpConfigGroup());
	}

	public static Scenario createScenario() {
		scenario = ScenarioUtils.createScenario(config);
		ScenarioUtils.loadScenario(scenario);
		return scenario;
	}

	public static Scenario setScenario(Scenario scenario) {
		return RunUAMScenario.scenario = scenario;
	}

	public static Controler createControler() {
		try {
			cmd.applyConfiguration(config);
		} catch (ConfigurationException e) {
			e.printStackTrace();
		}

		controler = new Controler(scenario);

		/**
		 * TESTING START TODO
		 */

		UAMConfigGroup uamConfig = (UAMConfigGroup) config.getModules().get(UAMConfigGroup.GROUP_NAME);
		uamConfig.setInputFile("uam_network.xml");

		uamConfig.setAccessEgressModesAsString("car");
		uamConfig.setSearchRadius(1e5);

		/** TESTING END */

		// Initiate Urban Air Mobility XML reading and parsing
		Network network = controler.getScenario().getNetwork();

		/* TESTING START */

		NetworkFactory factory = network.getFactory();

		Link hubAjaccio = network.getLinks().get(Id.createLinkId("62245"));
		Link hubBastia = network.getLinks().get(Id.createLinkId("15431"));

		Set<String> linkModes = new HashSet<>(hubAjaccio.getAllowedModes());
		linkModes.add("uam");
		hubAjaccio.setAllowedModes(linkModes);

		linkModes = new HashSet<>(hubBastia.getAllowedModes());
		linkModes.add("uam");
		hubBastia.setAllowedModes(linkModes);

		Link uam1 = factory.createLink(Id.createLinkId("uam1"), hubAjaccio.getToNode(), hubBastia.getFromNode());
		Link uam2 = factory.createLink(Id.createLinkId("uam2"), hubBastia.getToNode(), hubAjaccio.getFromNode());

		uam1.setAllowedModes(Collections.singleton("uam"));
		uam2.setAllowedModes(Collections.singleton("uam"));

		network.addLink(hubAjaccio);
		network.addLink(hubBastia);
		network.addLink(uam1);
		network.addLink(uam2);

		hubAjaccio.getAttributes().putAttribute("type", "uam_horizontal");
		hubBastia.getAttributes().putAttribute("type", "uam_horizontal");
		uam1.getAttributes().putAttribute("type", "uam_horizontal");
		uam2.getAttributes().putAttribute("type", "uam_horizontal");
		
		uam1.setFreespeed(100.0);
		uam2.setFreespeed(100.0);
		uam1.setCapacity(1000000.0);
		uam2.setCapacity(1000000.0);

		/* TESTING END */

		// sets transit modules in case of simulating/not pT
		/*controler.getConfig().transit().setUseTransit(uamConfigGroup.getPtSimulation());
		if (uamConfigGroup.getPtSimulation()) {
			controler.addOverridingModule(new SwissRailRaptorModule());
			// controler.addOverridingModule(new BaselineTransitModule());
		}*/

		controler.addOverridingModule(new DvrpModule());

		controler.addOverridingModule(new UAMModule());
		controler.addOverridingModule(new UAMSpeedModule());
		// controler.addOverridingModule(new DvrpTravelTimeModule());

		controler.configureQSimComponents(configurator -> {
			UAMQSimModule.activateModes().configure(configurator);
		});

		// TODO: Did this for testing 11 Feb
		controler.getConfig().transit().setUseTransit(true);
		controler.getConfig().transit().setUsingTransitInMobsim(false);
		controler.getConfig().qsim().setSimStarttimeInterpretation(StarttimeInterpretation.onlyUseStarttime);
		controler.getConfig().qsim().setStartTime(0.0);

		DvrpConfigGroup.get(config).setNetworkModesAsString("uam");
		config.getModules().remove(DiscreteModeChoiceConfigGroup.GROUP_NAME);
		config.getModules().remove("eqasim");
		config.getModules().remove("eqasim:calibration");
		config.getModules().remove("swissRailRaptor");

		config.planCalcScore().addModeParams(new ModeParams("access_uam_car"));
		config.planCalcScore().addModeParams(new ModeParams("egress_uam_car"));
		config.planCalcScore().addModeParams(new ModeParams("uam"));
		config.planCalcScore()
				.addActivityParams(new ActivityParams("uam_interaction").setScoringThisActivityAtAll(false));

		config.controler().setWriteEventsInterval(1);
		
		return controler;
	}
}
