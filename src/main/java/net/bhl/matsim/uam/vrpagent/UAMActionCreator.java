package net.bhl.matsim.uam.vrpagent;

import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.passenger.PassengerEngine;
import org.matsim.contrib.dvrp.run.DvrpMode;
import org.matsim.contrib.dvrp.schedule.StayTask;
import org.matsim.contrib.dvrp.schedule.Task;
import org.matsim.contrib.dvrp.vrpagent.VrpAgentLogic;
import org.matsim.contrib.dvrp.vrpagent.VrpLegFactory;
import org.matsim.contrib.dynagent.DynAction;
import org.matsim.contrib.dynagent.DynAgent;
import org.matsim.contrib.dynagent.IdleDynActivity;

import com.google.inject.Inject;

import net.bhl.matsim.uam.passenger.UAMPassengerDropoffActivity;
import net.bhl.matsim.uam.passenger.UAMPassengerPickupActivity;
import net.bhl.matsim.uam.run.UAMConstants;
import net.bhl.matsim.uam.schedule.UAMDropoffTask;
import net.bhl.matsim.uam.schedule.UAMPickupTask;
import net.bhl.matsim.uam.schedule.UAMTaskType;

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

		if (task.getTaskType().equals(UAMTaskType.PICKUP)) {
			UAMPickupTask mpt = (UAMPickupTask) task;
			return new UAMPassengerPickupActivity(passengerEngine, dynAgent, vehicle, mpt, mpt.getRequests(),
					mpt.getBoardingTime(), PICKUP_ACTIVITY_TYPE);
		} else if (task.getTaskType().equals(UAMTaskType.DROPOFF)) {
			UAMDropoffTask mdt = (UAMDropoffTask) task;
			return new UAMPassengerDropoffActivity(passengerEngine, dynAgent, vehicle, mdt, mdt.getRequests(),
					mdt.getDeboardingTime(), DROPOFF_ACTIVITY_TYPE);
		} else if (task.getTaskType().equals(UAMTaskType.FLY)) {
			return legCreator.create(vehicle);
		} else if (task.getTaskType().equals(UAMTaskType.STAY)) {
			return new UAMStayActivity((StayTask) task, STAY_ACTIVITY_TYPE);
		} else if (task.getTaskType().equals(UAMTaskType.TURNAROUND)) {
			return new IdleDynActivity(TURNAROUND_ACTIVITY_TYPE, task.getEndTime());
		} else {
			throw new IllegalStateException();
		}
	}
}
