package net.bhl.matsim.uam.schedule;

import net.bhl.matsim.uam.passenger.UAMRequest;
import net.bhl.matsim.uam.vrpagent.UAMActionCreator;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.dvrp.schedule.StayTaskImpl;

import java.util.Collection;

/**
 * During this task the UAM Vehicle is idle.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMStayTask extends StayTaskImpl implements UAMTask {
	public UAMStayTask(double beginTime, double endTime, Link link, String name) {
		super(beginTime, endTime, link, name);
	}

	public UAMStayTask(double beginTime, double endTime, Link link) {
		this(beginTime, endTime, link, UAMActionCreator.STAY_ACTIVITY_TYPE);
	}

	@Override
	protected String commonToString() {
		return "[" + getUAMTaskType().toString() + "]" + super.commonToString();
	}

	@Override
	public UAMTaskType getUAMTaskType() {
		return UAMTaskType.STAY;
	}

	@Override
	public Collection<UAMRequest> getRequests() {
		return null;
	}

	@Override
	public void addRequest(UAMRequest request) {

	}
}
