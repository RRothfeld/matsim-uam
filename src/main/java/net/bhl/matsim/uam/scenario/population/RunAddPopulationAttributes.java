package net.bhl.matsim.uam.scenario.population;

import java.util.Random;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

//Adjusted from RunPopulationDownsamplingExample.java by matsim-code-examples

/**
 * This script adds socio-demographic attributes to each person object in an
 * existing population (or plan) file.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
@Deprecated
public class RunAddPopulationAttributes {

	private double carOwnsershipPercent = 0.75;
	private double ptSubscriptionOwnsershipPercent = 0.75;
	private double bikeOwnsershipPercent = 0.95;
	private double employedPercent = 0.95;

	public static void main(final String[] args) {
		RunAddPopulationAttributes app = new RunAddPopulationAttributes();
		app.run(args);
	}

	void run(final String[] args) {
		// ARGS: population
		String inputPopFilename = args[0];
		Config config = ConfigUtils.createConfig();
		config.plans().setInputFile(inputPopFilename);

		Scenario scenario = ScenarioUtils.createScenario(config);
		ScenarioUtils.loadScenario(scenario);

		Population pop = scenario.getPopulation();
		for (Person p : pop.getPersons().values()) {
			p.getAttributes().putAttribute("age", 30);
			p.getAttributes().putAttribute("employed", new Random().nextDouble() < employedPercent);
			p.getAttributes().putAttribute("ptSubscription",
					new Random().nextDouble() < ptSubscriptionOwnsershipPercent);
			p.getAttributes().putAttribute("sex", new Random().nextBoolean() ? "m" : "f");
			p.getAttributes().putAttribute("bikeAvailability",
					new Random().nextDouble() < bikeOwnsershipPercent ? "always" : "never");

			double rand = new Random().nextDouble();
			p.getAttributes().putAttribute("hasLicense", rand < carOwnsershipPercent ? "true" : "false"); // excepted as
			// string
			// instead
			// of
			// boolean
			p.getAttributes().putAttribute("carAvailability", rand < carOwnsershipPercent ? "always" : "never");
		}

		// add pax to total pop and print new population file
		PopulationWriter popwriter = new PopulationWriter(pop);
		String[] inputPop = inputPopFilename.split(".xml");
		popwriter.write(inputPop[0] + "_added_attrs.xml" + inputPop[1]);

		System.out.println("done.");
	}
}
