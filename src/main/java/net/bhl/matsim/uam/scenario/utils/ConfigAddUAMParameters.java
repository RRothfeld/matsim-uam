package net.bhl.matsim.uam.scenario.utils;

import net.bhl.matsim.uam.config.UAMConfigGroup;
import net.bhl.matsim.uam.router.strategy.UAMStrategy;
import net.bhl.matsim.uam.run.UAMConstants;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;

import java.util.Collection;

/**
 * This class adds UAM parameters to the Config file.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class ConfigAddUAMParameters {

	public static void main(String[] args) {
		System.out.println("ARGS: path to config, UAM input file, modes");

		int i = 0;
		Config config = ConfigUtils.loadConfig(args[i++], new DvrpConfigGroup());
		// Using default inputs for optional parameters
		addUAMParameters(config, args[i++], args[i++], 2, 5000, 500,
				UAMStrategy.UAMStrategyType.MINTRAVELTIME, true);
		ConfigWriter configWriter = new ConfigWriter(config);
		configWriter.write(args[0] + "." + UAMConstants.uam + ".xml");
	}

	public static void addUAMParameters(Config config, String inputUAMFile, String modes, int threads, int searchRadius,
										int walkDistance, UAMStrategy.UAMStrategyType routingStrategy, boolean ptSimulation) {
		config.getModules().put(UAMConstants.uam, new UAMConfigGroup());
		config.getModules().get(UAMConstants.uam).addParam("inputFile", inputUAMFile);
		config.getModules().get(UAMConstants.uam).addParam("accessEgressModes", modes);
		//config.getModules().get(UAMConstants.uam).addParam("parallelRouters", "" + threads);
		config.getModules().get(UAMConstants.uam).addParam("searchRadius", "" + searchRadius);
		config.getModules().get(UAMConstants.uam).addParam("walkDistance", "" + walkDistance);
		config.getModules().get(UAMConstants.uam).addParam("routingStrategy", routingStrategy.toString());
		//config.getModules().get(UAMConstants.uam).addParam("ptSimulation", String.valueOf(ptSimulation));

		// If PT simulation is desired (true), PT may not be listed under teleportedModeParameters, else it must be
		ConfigGroup planscalcroute = config.getModules().get("planscalcroute");
		Collection<? extends ConfigGroup> teleportedModeParameters = planscalcroute.getParameterSets("teleportedModeParameters");

		boolean ptParamsExistent = false;
		for (ConfigGroup cg : teleportedModeParameters) {
			if (!(cg instanceof PlansCalcRouteConfigGroup.ModeRoutingParams))
				continue;

			PlansCalcRouteConfigGroup.ModeRoutingParams mrp = (PlansCalcRouteConfigGroup.ModeRoutingParams) cg;

			if (mrp.getMode().equals(TransportMode.pt)) {
				ptParamsExistent = true;
				if (ptSimulation)
					mrp.setMode("disabled-" + TransportMode.pt);
				break;
			}
		}

		if (!ptSimulation && !ptParamsExistent) {
			PlansCalcRouteConfigGroup.ModeRoutingParams modeRoutingParams =
					new PlansCalcRouteConfigGroup.ModeRoutingParams();
			modeRoutingParams.setMode(TransportMode.pt);
			modeRoutingParams.setTeleportedModeSpeed(10.0);
			modeRoutingParams.setBeelineDistanceFactor(1.0);
			planscalcroute.addParameterSet(modeRoutingParams);
		}

		// UAM planCalcScore activities
		ConfigGroup uamInteractionParam = config.getModules().get("planCalcScore").createParameterSet("activityParams");
		uamInteractionParam.addParam("activityType", UAMConstants.interaction);
		uamInteractionParam.addParam("scoringThisActivityAtAll", "false");
		config.getModules().get("planCalcScore").addParameterSet(uamInteractionParam);

		String mainMode = config.getModules().get("qsim").getParams().get("mainMode");
		config.getModules().get("qsim").addParam("mainMode",
				mainMode.replace("[", ""). replace("]", "")
						+ "," + UAMConstants.access + TransportMode.car
						+ "," + UAMConstants.egress + TransportMode.car);

		// UAM planCalcScore modes
		String[] modeScores = {UAMConstants.uam,
				UAMConstants.access + TransportMode.walk, UAMConstants.egress + TransportMode.walk,
				UAMConstants.access + TransportMode.car, UAMConstants.egress + TransportMode.car,
				UAMConstants.access + TransportMode.bike, UAMConstants.egress + TransportMode.bike};
		for (String modeScore : modeScores) {
			ConfigGroup modeParam = config.getModules().get("planCalcScore").createParameterSet("modeParams");
			modeParam.addParam("mode", modeScore);
			modeParam.addParam("constant", "0.0");
			modeParam.addParam("marginalUtilityOfDistance_util_m", "0.0");
			modeParam.addParam("marginalUtilityOfTraveling_util_hr", "0.0");
			modeParam.addParam("monetaryDistanceRate", "0.0");
			config.getModules().get("planCalcScore").addParameterSet(modeParam);
		}
	}
}
