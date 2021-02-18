package net.bhl.matsim.uam.config;

import net.bhl.matsim.uam.router.strategy.UAMStrategy;
import net.bhl.matsim.uam.router.strategy.UAMStrategy.UAMStrategyType;
import net.bhl.matsim.uam.run.UAMConstants;
import org.apache.log4j.Logger;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Config group for the UAM. This class sets the parameters required for UAM
 * simulation. Its parameters can be set in the config file, under the uam
 * module.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMConfigGroup extends ReflectiveConfigGroup {
	public static final String GROUP_NAME = UAMConstants.uam;
	private static final Logger log = Logger.getLogger(UAMConfigGroup.class);
	private String inputUAMFile;
	private Set<String> availableAccessModes;
	private UAMStrategyType routingStrategy = UAMStrategyType.MINACCESSTRAVELTIME;
	private int parallelRouters = 2;
	private boolean staticSearchRadius = true;
	private double searchRadius = 5000; // maximum crow fly distance to origin/destination stations
	// the uam access and egress mode, otherwise the fastest (car or pt)
	private double walkDistance = 500; // if the access/egress distance is less than walkDistance, then walk will be
	private boolean ptSimulation = true; // selects whether public transport will be simulated or performed by

	public UAMConfigGroup() {
		super(GROUP_NAME);
	}

	@StringGetter("inputUAMFile")
	public String getUAM() {
		return this.inputUAMFile;
	}

	@StringSetter("inputUAMFile")
	public void setUAM(final String inputUAMFile) {
		this.inputUAMFile = inputUAMFile;
	}

	@StringGetter("routingStrategy")
	public UAMStrategyType getUAMRoutingStrategy() {
		return this.routingStrategy;
	}

	@StringSetter("routingStrategy")
	public void setUAMRoutingStrategy(final String routingStrategy) {
		try {
			this.routingStrategy = UAMStrategyType.valueOf(routingStrategy.toUpperCase());
		} catch (IllegalArgumentException e) {
			log.warn("Unknown UAM routing strategy: " + routingStrategy + "; Possible strategies are:");
			int i = 0;
			for (UAMStrategyType st : UAMStrategy.UAMStrategyType.values())
				log.warn(i++ + ". " + st.toString());

			e.printStackTrace();
			System.exit(-1);
		}

	}

	@StringGetter("parallelRouters")
	public int getParallelRouters() {
		return this.parallelRouters;
	}

	@StringSetter("parallelRouters")
	public void setParallelRouters(final String parallelRouters) {
		this.parallelRouters = Integer.parseInt(parallelRouters);
	}

	@StringGetter("staticSearchRadius")
	public boolean getStaticSearchRadius() {
		return this.staticSearchRadius;
	}

	@StringSetter("staticSearchRadius")
	public void setStaticSearchRadius(final String staticSearchRadius) {
		this.staticSearchRadius = Boolean.parseBoolean(staticSearchRadius);
	}

	@StringGetter("searchRadius")
	public double getSearchRadius() {
		return this.searchRadius;
	}

	@StringSetter("searchRadius")
	public void setSearchRadius(final String searchRadius) {
		this.searchRadius = Double.parseDouble(searchRadius);
	}

	@StringGetter("walkDistance")
	public double getWalkDistance() {
		return this.walkDistance;
	}

	@StringSetter("walkDistance")
	public void setWalkDistance(final String walkDistance) {
		this.walkDistance = Double.parseDouble(walkDistance);
	}

	@StringGetter("ptSimulation")
	public boolean getPtSimulation() {
		return this.ptSimulation;
	}

	@StringSetter("ptSimulation")
	public void setPtSimulation(final String ptSimulation) {
		if (Boolean.parseBoolean(ptSimulation)) {
			log.warn(
					"In case of simulating PT do not add \"pt\" as a parameterset type=\"teleportedModeParameteres\" in the planscalcroute module!");
		} else {
			log.warn(
					"You chose to not simulate PT, please add \"pt\" as a parameterset type=\"teleportedModeParameteres\" in the planscalcroute module!");
		}
		this.ptSimulation = Boolean.parseBoolean(ptSimulation);
	}

	@StringGetter("availableAccessModes")
	public Set<String> getAvailableAccessModes() {
		return this.availableAccessModes;
	}

	@StringSetter("availableAccessModes")
	public void setAvailableAccessModes(final String availableAccessModes) {
		String[] arr = availableAccessModes.replaceAll("[\\[\\]]", "").split("[,; ]+");
		this.availableAccessModes = new HashSet<>();
		this.availableAccessModes.addAll(Arrays.asList(arr));
	}
}
