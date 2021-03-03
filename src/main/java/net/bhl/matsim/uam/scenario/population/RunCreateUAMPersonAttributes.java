package net.bhl.matsim.uam.scenario.population;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

import net.bhl.matsim.uam.config.UAMConfigGroup;
import net.bhl.matsim.uam.infrastructure.UAMStation;
import net.bhl.matsim.uam.infrastructure.UAMStations;
import net.bhl.matsim.uam.infrastructure.readers.UAMXMLReader;
import net.bhl.matsim.uam.run.UAMConstants;

/**
 * This script generates a xml subpopulation attributes file containing the id
 * of potential uam users based on a provided config file containing a search
 * radius. If there is at least one station within reach, the user is considered
 * a potential uam user.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
@Deprecated
public class RunCreateUAMPersonAttributes {
	public static void main(final String[] args) {
		// cmd-line input: input-config
		String inputConfig = args[0];

		UAMConfigGroup uamConfigGroup = new UAMConfigGroup();
		Config config = ConfigUtils.loadConfig(inputConfig, uamConfigGroup, new DvrpConfigGroup());

		Scenario scenario = ScenarioUtils.createScenario(config);
		ScenarioUtils.loadScenario(scenario);

		Network network = scenario.getNetwork();
		TransportModeNetworkFilter filter = new TransportModeNetworkFilter(scenario.getNetwork());
		Set<String> modes = new HashSet<>();
		modes.add(UAMConstants.uam);
		Network networkUAM = NetworkUtils.createNetwork();
		filter.filter(networkUAM, modes);

		UAMXMLReader uamReader = new UAMXMLReader(networkUAM);
		uamReader.readFile(ConfigGroup.getInputFileURL(config.getContext(), uamConfigGroup.getInputFile()).getPath()
				.replace("%20", " "));
		final UAMStations uamStations = new UAMStations(uamReader.getStations(), network);

		Population pop = scenario.getPopulation();
		ObjectAttributes objattr = new ObjectAttributes();

		// filter persons with activities outside the radius
		personloop:
		for (Person person : pop.getPersons().values()) {
			for (PlanElement p : person.getSelectedPlan().getPlanElements()) {
				if (p instanceof Activity) {

					Coord originCoord = ((Activity) p).getCoord();

					if (uamConfigGroup.getUseDynamicSearchRadius())
						System.err.println("Warning: UAM search radius must be dynamic = false! Setting it to static.");

					Collection<UAMStation> stations = uamStations.getUAMStationsInRadius(originCoord,
							uamConfigGroup.getSearchRadius());

					if (!stations.isEmpty()) {
						// at least one station within reach
						objattr.putAttribute(person.getId().toString(), "subpopulation", "potential-uam");
						continue personloop;
					}
				}
			}
			// no UAM stations in reach
		}

		int radius = (int) uamConfigGroup.getSearchRadius();
		String rangeString = radius > 9999 ? "" + (radius / 1000) + "km" : "" + radius + "m";

		ObjectAttributesXmlWriter objwriter = new ObjectAttributesXmlWriter(objattr);
		objwriter.writeFile(inputConfig.substring(0, inputConfig.lastIndexOf("\\")) + "\\"
				+ config.plans().getInputFile().split(".xml")[0] + "_attributes_uam-" + rangeString + ".xml.gz");

		System.out.println("done.");
	}
}