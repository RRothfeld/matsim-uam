package net.bhl.matsim.uam.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import org.matsim.core.config.Config;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.config.consistency.BeanValidationConfigConsistencyChecker;

import net.bhl.matsim.uam.router.strategy.UAMStrategy.UAMStrategyType;

/**
 * Config group for the UAM. This class sets the parameters required for UAM
 * simulation. Its parameters can be set in the config file, under the uam
 * module.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMConfigGroup extends ReflectiveConfigGroup {
	public static final String GROUP_NAME = "uam";

	static public final String ACCESS_EGRESS_MODES = "accessEgressModes";
	static public final String INPUT_FILE = "inputFile";
	static public final String SEARCH_RADIUS = "searchRadius";
	static public final String USE_DYNAMIC_SEARCH_RADIUS = "useDynamicSearchRadius";
	static public final String WALK_DISTANCE = "walkDistance";
	static public final String ROUTING_STRATEGY = "routingStrategy";

	static private final String ACCESS_EGRESS_MODES_EXP = "Comma-separated list of possible access/egress modes";
	static private final String INPUT_FILE_EXP = "Path to the input file for UAM infrastructure and vehicles";
	static private final String SEARCH_RADIUS_EXP = "Maximum euclidean distance to a station";
	static private final String USE_DYNAMIC_SEARCH_RADIUS_EXP = "TODO";
	static private final String WALK_DISTANCE_EXP = "If the access/egress distance is less than this distance, walk will be the UAM access and egress mode";
	static private final String ROUTING_STRATEGY_EXP = "Selects the used routing strategy among " + String.join(", ",
			Arrays.asList(UAMStrategyType.values()).stream().map(String::valueOf).collect(Collectors.toList()));

	@NotEmpty
	private Set<String> accessEgressModes = new HashSet<>();

	@NotBlank
	private String inputFile = null;

	@Positive
	private double searchRadius = 5000.0;

	private boolean useDynamicSearchRadius = true;

	@Positive
	private double walkDistance = 500.0;

	@NotNull
	private UAMStrategyType routingStrategy = UAMStrategyType.MINACCESSTRAVELTIME;

	protected void checkConsistency(Config config) {
		super.checkConsistency(config);
		new BeanValidationConfigConsistencyChecker().checkConsistency(config);
	}

	public Map<String, String> getComments() {
		Map<String, String> map = super.getComments();
		map.put(ACCESS_EGRESS_MODES, ACCESS_EGRESS_MODES_EXP);
		map.put(INPUT_FILE, INPUT_FILE_EXP);
		map.put(SEARCH_RADIUS, SEARCH_RADIUS_EXP);
		map.put(USE_DYNAMIC_SEARCH_RADIUS, USE_DYNAMIC_SEARCH_RADIUS_EXP);
		map.put(WALK_DISTANCE, WALK_DISTANCE_EXP);
		map.put(ROUTING_STRATEGY, ROUTING_STRATEGY_EXP);
		return map;
	}

	public UAMConfigGroup() {
		super(GROUP_NAME);
	}

	public Set<String> getAccessEgressModes() {
		return accessEgressModes;
	}

	public void setAccessEgressModes(Set<String> accessEgressModes) {
		this.accessEgressModes = accessEgressModes;
	}

	@StringGetter(ACCESS_EGRESS_MODES)
	public String getAccessEgressModesAsString() {
		return String.join(", ", accessEgressModes);
	}

	@StringSetter(ACCESS_EGRESS_MODES)
	public void setAccessEgressModesAsString(String accessEgressModes) {
		this.accessEgressModes = Arrays.asList(accessEgressModes.split(",")).stream().map(String::trim)
				.collect(Collectors.toSet());
	}

	@StringGetter(INPUT_FILE)
	public String getInputFile() {
		return this.inputFile;
	}

	@StringSetter(INPUT_FILE)
	public void setInputFile(String inputFile) {
		this.inputFile = inputFile;
	}

	@StringGetter(SEARCH_RADIUS)
	public double getSearchRadius() {
		return searchRadius;
	}

	@StringSetter(SEARCH_RADIUS)
	public void setSearchRadius(double searchRadius) {
		this.searchRadius = searchRadius;
	}

	@StringGetter(USE_DYNAMIC_SEARCH_RADIUS)
	public boolean getUseDynamicSearchRadius() {
		return useDynamicSearchRadius;
	}

	@StringSetter(USE_DYNAMIC_SEARCH_RADIUS)
	public void setStaticSearchRadius(boolean useDynamicSearchRadius) {
		this.useDynamicSearchRadius = useDynamicSearchRadius;
	}

	@StringGetter(WALK_DISTANCE)
	public double getWalkDistance() {
		return walkDistance;
	}

	@StringSetter(WALK_DISTANCE)
	public void setWalkDistance(double walkDistance) {
		this.walkDistance = walkDistance;
	}

	@StringGetter(ROUTING_STRATEGY)
	public UAMStrategyType getRoutingStrategy() {
		return routingStrategy;
	}

	@StringSetter(ROUTING_STRATEGY)
	public void setRoutingStrategy(UAMStrategyType routingStrategy) {
		this.routingStrategy = routingStrategy;
	}
}
