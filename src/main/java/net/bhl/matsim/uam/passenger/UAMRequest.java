package net.bhl.matsim.uam.passenger;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.passenger.PassengerRequest;

import net.bhl.matsim.uam.dispatcher.UAMDispatcher;
import net.bhl.matsim.uam.run.UAMConstants;
import net.bhl.matsim.uam.schedule.UAMDropoffTask;
import net.bhl.matsim.uam.schedule.UAMPickupTask;

/**
 * This class defines the UAM Request and its properties.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMRequest implements PassengerRequest {
	final private Id<Request> id;
	final private double submissionTime;
	final private Link originLink;
	final private Link destinationLink;
	final private Id<Person> passengerId;
	private final double quantity;
	private final double earliestStartTime;
	private final double latestStartTime;
	private UAMDropoffTask dropoffTask;
	private UAMPickupTask pickupTask;
	private UAMDispatcher dispatcher;
	private double distance;

	public UAMRequest(Id<Request> id, Id<Person> passengerId, Link originLink, Link destinationLink, double pickupTime,
			double submissionTime, UAMDispatcher dispatcher, double distance) {
		this.id = id;
		this.submissionTime = submissionTime;
		this.quantity = 1.0;
		this.originLink = originLink;
		this.destinationLink = destinationLink;
		this.earliestStartTime = pickupTime;
		this.latestStartTime = pickupTime;
		this.passengerId = passengerId;
		this.dispatcher = dispatcher;
		this.distance = distance;
	}

	public double getQuantity() {
		return this.quantity;
	}

	@Override
	public double getEarliestStartTime() {
		return this.earliestStartTime;
	}

	@Override
	public double getLatestStartTime() {
		return this.latestStartTime;
	}

	@Override
	public double getSubmissionTime() {
		return this.submissionTime;
	}

	@Override
	public Id<Request> getId() {
		return this.id;
	}

	@Override
	public Id<Person> getPassengerId() {
		return passengerId;
	}

	@Override
	public Link getFromLink() {
		return this.originLink;
	}

	@Override
	public Link getToLink() {
		return this.destinationLink;
	}

	@Override
	public String getMode() {
		return UAMConstants.uam;
	}

	public UAMDropoffTask getDropoffTask() {
		return dropoffTask;
	}

	public void setDropoffTask(UAMDropoffTask dropoffTask) {
		this.dropoffTask = dropoffTask;
	}

	public UAMPickupTask getPickupTask() {
		return pickupTask;
	}

	public void setPickupTask(UAMPickupTask pickupTask) {
		this.pickupTask = pickupTask;
	}

	public UAMDispatcher getDispatcher() {
		return dispatcher;
	}

	public double getDistance() {
		return distance;
	}

	public void setDistance(double distance) {
		this.distance = distance;
	}

}
