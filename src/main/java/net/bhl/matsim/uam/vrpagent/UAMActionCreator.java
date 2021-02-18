package net.bhl.matsim.uam.vrpagent;

import com.google.inject.Inject;
import net.bhl.matsim.uam.passenger.UAMPassengerDropoffActivity;
import net.bhl.matsim.uam.passenger.UAMPassengerPickupActivity;
import net.bhl.matsim.uam.run.UAMConstants;
import net.bhl.matsim.uam.schedule.*;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.passenger.PassengerEngine;
import org.matsim.contrib.dvrp.run.DvrpMode;
import org.matsim.contrib.dvrp.schedule.Task;
import org.matsim.contrib.dvrp.vrpagent.VrpAgentLogic;
import org.matsim.contrib.dvrp.vrpagent.VrpLegFactory;
import org.matsim.contrib.dynagent.DynAction;
import org.matsim.contrib.dynagent.DynAgent;

/**
 * Class that is responsible for creating DynAction activities depending on the
 * task of the vehicle.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMActionCreator implements VrpAgentLogic.DynActionCreator {
	public static final String PICKUP_ACTIVITY_TYPE = UAMConstants.uam.toUpperCase() + "Pickup";
	public static final String DROPOFF_ACTIVITY_TYPE = UAMConstants.uam.toUpperCase() + "Dropoff";
	public static final String STAY_ACTIVITY_TYPE = UAMConstants.uam.toUpperCase() + "Stay";
	public static final String TURNAROUND_ACTIVITY_TYPE = UAMConstants.uam.toUpperCase() + "TurnAround";
	public static final String TRANSIT_ACTIVITY_TYPE = UAMConstants.uam.toUpperCase() + "Transit";

	@Inject
	@DvrpMode(UAMConstants.uam)
	private PassengerEngine passengerEngine;

	@Inject
	private VrpLegFactory legCreator;
	@Override
	public DynAction createAction(DynAgent dynAgent, DvrpVehicle vehicle, double now) {
		Task task = vehicle.getSchedule().getCurrentTask();
		if (task instanceof UAMTask) {
			switch (((UAMTask) task).getUAMTaskType()) {
				case PICKUP:
					UAMPickupTask mpt = (UAMPickupTask) task;
					return new UAMPassengerPickupActivity(passengerEngine, dynAgent, vehicle, mpt, mpt.getRequests(),
							mpt.getBoardingTime(), PICKUP_ACTIVITY_TYPE);
				case DROPOFF:
					UAMDropoffTask mdt = (UAMDropoffTask) task;
					return new UAMPassengerDropoffActivity(passengerEngine, dynAgent, vehicle, mdt, mdt.getRequests(),
							mdt.getDeboardingTime(), DROPOFF_ACTIVITY_TYPE);
				case FLY:
					return legCreator.create(vehicle);
				case STAY:
					return new UAMStayActivity((UAMStayTask) task);
				case TURNAROUND:
					return new UAMTurnAroundActivity((UAMTurnAroundTask) task);
				default:
					throw new IllegalStateException();
			}
		} else {
			throw new IllegalArgumentException();
		}
	}
}
