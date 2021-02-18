package net.bhl.matsim.uam.router;

import net.bhl.matsim.uam.run.UAMConstants;

/**
 * Provides flight segment identifiers for MATSim network links. Currently uses two flight segments: horizontal (i.e.
 * cruise flight), which is the default flight segment, and vertical (i.e. take-off and landings). Can be extended to
 * various flight segments in the future.
 *
 * @author RRothfeld (Raoul Rothfeld)
 */
public class UAMFlightSegments {
	public static final String ATTRIBUTE = "type";

	public static final String HORIZONTAL = UAMConstants.uam + "_horizontal";
	public static final String VERTICAL = UAMConstants.uam + "_vertical";
}
