package net.bhl.matsim.uam.qsim;

import com.google.common.base.Verify;
import com.google.inject.Inject;
import net.bhl.matsim.uam.events.UAMPrebookVehicle;
import net.bhl.matsim.uam.passenger.UAMRequestCreator;
import net.bhl.matsim.uam.qsim.UAMTripInfo.BookedRequest;
import net.bhl.matsim.uam.run.UAMConstants;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.passenger.PassengerEngine;
import org.matsim.contrib.dvrp.passenger.PassengerRequest;
import org.matsim.contrib.dvrp.run.DvrpMode;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.MobsimPassengerAgent;
import org.matsim.core.mobsim.framework.PlanAgent;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.agents.WithinDayAgentUtils;
import org.matsim.core.mobsim.qsim.interfaces.DepartureHandler;
import org.matsim.pt.PtConstants;

import java.util.HashSet;
import java.util.Set;

/**
 * This class defines the departure handler for UAM simulation.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMDepartureHandler implements DepartureHandler {

	Set<Id<Person>> bookedTrips = new HashSet<>();
	@Inject
	@DvrpMode(UAMConstants.uam)
	private PassengerEngine passengerEngine;

	@Inject
	private QSim qsim;
	
	@Inject
	@DvrpMode(UAMConstants.uam)
	Network network;

	private Set<String> modesRequiringManualUAMPrebooking;

	public void initiateUAMDepartureHandler() {
		if (modesRequiringManualUAMPrebooking == null) {
			qsim.getEventsManager().addHandler(new UAMPrebookVehicle(this));

			modesRequiringManualUAMPrebooking = new HashSet<>();
			String mainMode = this.qsim.getScenario().getConfig().getModules().get("qsim").getParams().get("mainMode");
			if (mainMode.contains(UAMConstants.access + TransportMode.car))
				modesRequiringManualUAMPrebooking.add(UAMConstants.access + TransportMode.car);
			if (mainMode.contains(UAMConstants.egress + TransportMode.car))
				modesRequiringManualUAMPrebooking.add(UAMConstants.egress + TransportMode.car);
		}
		// else: already initiated
	}

	public void manualUAMPrebooking(String mode, double now, Id<Person> id, Id<Link> linkId) {
		if (modesRequiringManualUAMPrebooking.contains(mode))
			handleDeparture(now, qsim.getAgents().get(id), linkId);
	}	
	
	private void performPrebooking(Leg uamLeg, MobsimAgent agent, double departureTime, double submissionTime) {
		Route uamRoute = uamLeg.getRoute();

		Link startLink = network.getLinks().get(uamRoute.getStartLinkId());
		Link endLink = network.getLinks().get(uamRoute.getEndLinkId());

		UAMTripInfo tripInfo = new UAMTripInfo(startLink, endLink, uamRoute, departureTime);
		passengerEngine.bookTrip((MobsimPassengerAgent) agent, tripInfo);
	}

	@Override
	public boolean handleDeparture(double now, MobsimAgent agent, Id<Link> linkId) {
		// Must be initiated before first use (cannot be done in constructor as this itself is passes as a variable)
		initiateUAMDepartureHandler();
		// TODO: Is there a way to initiate the UAMDepartureHandler at the beginning of an iteration?

		if (agent instanceof PlanAgent) {
			if (agent.getMode().startsWith(UAMConstants.access)) {
				Plan plan = ((PlanAgent) agent).getCurrentPlan();
				final Integer planElementsIndex = WithinDayAgentUtils.getCurrentPlanElementIndex(agent);
				final Leg accessLeg = (Leg) plan.getPlanElements().get(planElementsIndex);
				final Leg leg = (Leg) plan.getPlanElements().get(planElementsIndex + 2);
				Activity uam_interaction = (Activity) plan.getPlanElements().get(planElementsIndex + 1);
				
				performPrebooking(leg, agent,
						now + uam_interaction.getMaximumDuration().seconds()
								+ (accessLeg.getTravelTime().seconds() <= 0 ? 1 : accessLeg.getTravelTime().seconds()),
						now);
			} else if (agent.getMode().equals(TransportMode.transit_walk)
					|| agent.getMode().equals(TransportMode.access_walk)) {
				Plan plan = ((PlanAgent) agent).getCurrentPlan();
				final Integer planElementsIndex = WithinDayAgentUtils.getCurrentPlanElementIndex(agent);
				if (isUamTrip(plan, planElementsIndex)) {
					if (!bookedTrips.contains(agent.getId())) {
						double travelTime = getTravelTime(plan, planElementsIndex);
						final Leg uamLeg = getUamLeg(plan, planElementsIndex);
						Activity uam_interaction = (Activity) plan.getPlanElements().get(planElementsIndex + 1);
						
						performPrebooking(uamLeg, agent,
								now + uam_interaction.getMaximumDuration().seconds() + (travelTime <= 0 ? 1 : travelTime),
								now);
					}
				}

			} else if (agent.getMode().equals(UAMConstants.uam))
				bookedTrips.remove(agent.getId());
		}

		return false;
	}

	private Leg getUamLeg(Plan plan, Integer planElementsIndex) {
		while (true) {
			PlanElement pe = plan.getPlanElements().get(planElementsIndex);
			if (pe instanceof Leg) {
				if (((Leg) pe).getMode().equals(UAMConstants.uam))
					return (Leg) pe;
			}
			planElementsIndex++;
		}
	}

	private double getTravelTime(Plan plan, Integer planElementsIndex) {
		boolean found = false;
		int index = planElementsIndex;
		double travelTime = 0.0;
		while (!found) {
			PlanElement pe = plan.getPlanElements().get(index);
			if (pe instanceof Leg) {
				if (((Leg) pe).getMode().equals(UAMConstants.uam))
					found = true;
				else
					travelTime += ((Leg) pe).getTravelTime().seconds();
			}
			index++;
		}
		return travelTime;
	}

	private boolean isUamTrip(Plan plan, Integer planElementsIndex) {
		int index = planElementsIndex + 1;
		while (true) {
			PlanElement pe = plan.getPlanElements().get(index);
			if (pe instanceof Activity) {
				if (((Activity) pe).getType().equals(UAMConstants.interaction))
					return true;
				else if (((Activity) pe).getType().equals(PtConstants.TRANSIT_ACTIVITY_TYPE)) {
					index++;

					continue;
				} else
					return false;
			}
			index++;
		}

	}

}
