package net.bhl.matsim.uam.vrpagent;

import org.matsim.contrib.dvrp.schedule.StayTask;
import org.matsim.contrib.dynagent.FirstLastSimStepDynActivity;

/**
 * An implementation of AbstractDynActivity for UAM DynAgents for the
 * UAMStayTask.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMStayActivity extends FirstLastSimStepDynActivity {
	final private StayTask task;

	public UAMStayActivity(StayTask task, String activityType) {
		super(activityType);
		this.task = task;
	}

	@Override
	protected boolean isLastStep(double now) {
		if (Double.isInfinite(task.getEndTime())) {
			return false;
		} else {
			return task.getEndTime() <= now;
		}
	}
}
