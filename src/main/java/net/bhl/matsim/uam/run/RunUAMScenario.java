package net.bhl.matsim.uam.run;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import net.bhl.matsim.uam.config.UAMConfigGroup;
import net.bhl.matsim.uam.qsim.UAMQSimModule;
import net.bhl.matsim.uam.qsim.UAMSpeedModule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.config.groups.QSimConfigGroup.StarttimeInterpretation;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

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

		controler.addOverridingModule(new DvrpModule());

		controler.addOverridingModule(new UAMModule());
		controler.addOverridingQSimModule(new UAMSpeedModule());
		controler.addOverridingModule(new SwissRailRaptorModule());

		controler.configureQSimComponents(configurator -> {
			UAMQSimModule.activateModes().configure(configurator);
		});

		controler.getConfig().transit().setUseTransit(true);
		controler.getConfig().transit().setUsingTransitInMobsim(true);
		controler.getConfig().qsim().setSimStarttimeInterpretation(StarttimeInterpretation.onlyUseStarttime);
		controler.getConfig().qsim().setStartTime(0.0);

		DvrpConfigGroup.get(config).setNetworkModesAsString("uam");
		config.planCalcScore().addModeParams(new ModeParams("access_uam_car"));
		config.planCalcScore().addModeParams(new ModeParams("egress_uam_car"));
		config.planCalcScore().addModeParams(new ModeParams("uam"));
		config.planCalcScore()
				.addActivityParams(new ActivityParams("uam_interaction").setScoringThisActivityAtAll(false));

		config.controler().setWriteEventsInterval(1);
		
		return controler;
	}
}
