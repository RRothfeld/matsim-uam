package net.bhl.matsim.uam.schedule;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.dvrp.schedule.StayTask;

import net.bhl.matsim.uam.passenger.UAMRequest;

/**
 * This task represents the local drop-off of passenger at stations, it doesn't
 * include the flying period.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMDropoffTask extends StayTask {
	private final Set<UAMRequest> requests = new HashSet<>();
	private final double deboardingTime;

	public UAMDropoffTask(double beginTime, double endTime, Link link, double deboardingTime) {
		super(UAMTaskType.DROPOFF, beginTime, endTime, link);
		this.deboardingTime = deboardingTime;
	}

	public UAMDropoffTask(double beginTime, double endTime, Link link, double deboardingTime,
			Collection<UAMRequest> requests) {
		this(beginTime, endTime, link, deboardingTime);

		this.requests.addAll(requests);
		for (UAMRequest request : requests)
			request.setDropoffTask(this);
	}

	public Set<UAMRequest> getRequests() {
		return this.requests;
	}

	public void addRequest(UAMRequest request) {
		this.requests.add(request);
	}

	public double getDeboardingTime() {
		return deboardingTime;
	}
}
