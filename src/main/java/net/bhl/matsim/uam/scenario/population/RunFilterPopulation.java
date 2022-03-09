package net.bhl.matsim.uam.scenario.population;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Set;

@Deprecated
public class RunFilterPopulation {
    public static void main(final String[] args) {
        RunFilterPopulation app = new RunFilterPopulation();
        app.run(args);
    }

    void run(final String[] args) {
        String inputPopFilename = args[0];
        String outputPopFilename = args[1];

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        ScenarioUtils.loadScenario(scenario);
        Population pop = scenario.getPopulation();

        PopulationReader pr = new PopulationReader(scenario);
        pr.readFile(inputPopFilename);

        Scenario newScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        ScenarioUtils.loadScenario(newScenario);
        Population newPop = newScenario.getPopulation();

        String[] persons = {"65250301","64445001"};
        Set<String> desiredPersons = Set.of(persons);
        for (Person person : pop.getPersons().values()) {
            if (desiredPersons.contains(person.getId().toString()))
                newPop.addPerson(person);
        }

        PopulationWriter popwriter = new PopulationWriter(newPop);
        popwriter.write(outputPopFilename);
        System.out.println("done.");
    }

}
