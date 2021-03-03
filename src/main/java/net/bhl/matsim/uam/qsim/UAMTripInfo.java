package net.bhl.matsim.uam.qsim;

import java.util.Collections;
import java.util.Map;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.mobsim.framework.MobsimPassengerAgent;
import org.matsim.core.mobsim.qsim.interfaces.TripInfoWithRequiredBooking;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.facilities.Facility;

import net.bhl.matsim.uam.run.UAMConstants;

public class UAMTripInfo implements TripInfoWithRequiredBooking {
	private final BookedRequest request;

	public UAMTripInfo(Link fromLink, Link toLink, Route route, double departureTime) {
		this.request = new BookedRequest(fromLink, toLink, route, departureTime);
	}

	@Override
	public Facility getPickupLocation() {
		return request.getFromFacility();
	}

	@Override
	public Facility getDropoffLocation() {
		return request.getToFacility();
	}

	@Override
	public double getExpectedBoardingTime() {
		return request.getTime();
	}

	@Override
	public double getExpectedTravelTime() {
		return request.getPlannedRoute().getTravelTime().seconds();
	}

	@Override
	public double getMonetaryPrice() {
		return 0.0;
	}

	@Override
	public Map<String, String> getAdditionalAttributes() {
		return Collections.emptyMap();
	}

	@Override
	public String getMode() {
		return UAMConstants.uam;
	}

	@Override
	public double getLatestDecisionTime() {
		return 0.0;
	}

	@Override
	public Request getOriginalRequest() {
		return request;
	}

	@Override
	public void bookTrip(MobsimPassengerAgent agent) {
		throw new IllegalStateException();
	}

	static public class BookedRequest implements Request {
		private final Link fromLink;
		private final Link toLink;
		private final Route route;
		private final double departureTime;

		public BookedRequest(Link fromLink, Link toLink, Route route, double departureTime) {
			this.fromLink = fromLink;
			this.toLink = toLink;
			this.route = route;
			this.departureTime = departureTime;
		}

		@Override
		public Facility getFromFacility() {
			return new LinkWrapperFacility(fromLink);
		}

		@Override
		public Facility getToFacility() {
			return new LinkWrapperFacility(toLink);
		}

		@Override
		public double getTime() {
			return departureTime;
		}

		@Override
		public TimeInterpretation getTimeInterpretation() {
			return TimeInterpretation.departure;
		}

		@Override
		public Route getPlannedRoute() {
			return route;
		}
	}
}
