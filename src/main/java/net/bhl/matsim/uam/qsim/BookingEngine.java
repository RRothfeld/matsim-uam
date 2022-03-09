package net.bhl.matsim.uam.qsim;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.contrib.dvrp.passenger.PassengerEngine;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.MobsimPassengerAgent;
import org.matsim.core.mobsim.framework.PlanAgent;
import org.matsim.core.mobsim.framework.events.MobsimBeforeSimStepEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimBeforeSimStepListener;
import org.matsim.core.mobsim.qsim.InternalInterface;
import org.matsim.core.mobsim.qsim.agents.WithinDayAgentUtils;
import org.matsim.core.mobsim.qsim.interfaces.MobsimEngine;
import org.matsim.pt.PtConstants;

import net.bhl.matsim.uam.run.UAMConstants;

public class BookingEngine implements MobsimEngine, PersonDepartureEventHandler, MobsimBeforeSimStepListener {
	private Set<Id<Person>> bookedTrips = new HashSet<>();

	private final List<MobsimAgent> bookingAgents = new LinkedList<>();
	private final List<MobsimAgent> processBookingAgents = new LinkedList<>();
	private InternalInterface internalInterface;
	private Set<String> uamModes = new HashSet<>();
	private Scenario scenario;
	private PassengerEngine passengerEngine;
	private EventsManager eventsManager;
	private Network network;

	public BookingEngine(Scenario scenario, PassengerEngine passengerEngine, EventsManager eventsManager,
			Network network) {
		this.scenario = scenario;
		this.passengerEngine = passengerEngine;
		this.eventsManager = eventsManager;
		this.network = network;
	}

	@Override
	public void doSimStep(double time) {
		for (MobsimAgent ma : this.processBookingAgents) {
			manualUAMPrebooking(time, ma);
		}

	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		if (uamModes.contains(event.getLegMode())) {
			MobsimAgent agent = internalInterface.getMobsim().getAgents().get(event.getPersonId());
			bookingAgents.add(agent);
		}

	}

	public boolean manualUAMPrebooking(double now, MobsimAgent agent) {

		if (agent instanceof PlanAgent) {

			if (((PlanAgent) agent).getCurrentPlanElement() instanceof Leg) {

				if (agent.getMode().startsWith(UAMConstants.access)) {
					Plan plan = ((PlanAgent) agent).getCurrentPlan();
					final Integer planElementsIndex = WithinDayAgentUtils.getCurrentPlanElementIndex(agent);
					final Leg accessLeg = (Leg) plan.getPlanElements().get(planElementsIndex);
					final Leg leg = (Leg) plan.getPlanElements().get(planElementsIndex + 2);
					Activity uam_interaction = (Activity) plan.getPlanElements().get(planElementsIndex + 1);

					performPrebooking(leg, agent, now + uam_interaction.getMaximumDuration().seconds()
							+ (accessLeg.getTravelTime().seconds() <= 0 ? 1 : accessLeg.getTravelTime().seconds()),
							now);
				} else if (agent.getMode().equals(TransportMode.walk)) {
					Plan plan = ((PlanAgent) agent).getCurrentPlan();
					final Integer planElementsIndex = WithinDayAgentUtils.getCurrentPlanElementIndex(agent);
					final Leg accessLeg = (Leg) plan.getPlanElements().get(planElementsIndex);

					if (accessLeg.getAttributes().getAttribute("routingMode").equals(UAMConstants.uam)) {
						if (!bookedTrips.contains(agent.getId())) {
							double travelTime = getTravelTime(plan, planElementsIndex);
							final Leg uamLeg = getUamLeg(plan, planElementsIndex);
							Activity uam_interaction = (Activity) plan.getPlanElements().get(planElementsIndex + 1);

							performPrebooking(uamLeg, agent, now + uam_interaction.getMaximumDuration().seconds()
									+ (travelTime <= 0 ? 1 : travelTime), now);
						}
					}

				} else if (agent.getMode().equals(UAMConstants.uam))
					bookedTrips.remove(agent.getId());
			} else {

				// we are not on the leg, this could happen if the access leg is
				// zero seconds long
				// first check this
				Plan plan = ((PlanAgent) agent).getCurrentPlan();
				final Integer planElementsIndex = WithinDayAgentUtils.getCurrentPlanElementIndex(agent);
				final Leg accessLeg = (Leg) plan.getPlanElements().get(planElementsIndex - 1);
				if (!(accessLeg.getTravelTime().seconds()  < 1.0))
					throw new RuntimeException("Person with id " + agent.getId().toString()
							+ " should be on a leg" + accessLeg.toString() + " but it is not. It is on "
							+ ((PlanAgent) agent).getCurrentPlanElement().toString());
				else {
					if (accessLeg.getMode().startsWith(UAMConstants.access)) {
						final Leg leg = (Leg) plan.getPlanElements().get(planElementsIndex + 1);
						Activity uam_interaction = (Activity) plan.getPlanElements().get(planElementsIndex);

						performPrebooking(leg, agent, now + uam_interaction.getMaximumDuration().seconds()
								+ (accessLeg.getTravelTime().seconds() <= 0 ? 1 : accessLeg.getTravelTime().seconds()),
								now);
					} else if (accessLeg.getMode().equals(TransportMode.walk)) {

						if (accessLeg.getAttributes().getAttribute("routingMode").equals(UAMConstants.uam)) {
							if (!bookedTrips.contains(agent.getId())) {
								double travelTime = getTravelTime(plan, planElementsIndex - 1);
								final Leg uamLeg = getUamLeg(plan, planElementsIndex - 1);
								Activity uam_interaction = (Activity) plan.getPlanElements().get(planElementsIndex);

								performPrebooking(uamLeg, agent, now + uam_interaction.getMaximumDuration().seconds()
										+ (travelTime <= 0 ? 1 : travelTime), now);
							}
						}

					} else if (agent.getMode().equals(UAMConstants.uam))
						bookedTrips.remove(agent.getId());

				}
			}
		}

		return false;
	}

	private void performPrebooking(Leg uamLeg, MobsimAgent agent, double departureTime, double submissionTime) {
		Route uamRoute = uamLeg.getRoute();

		Link startLink = network.getLinks().get(uamRoute.getStartLinkId());
		Link endLink = network.getLinks().get(uamRoute.getEndLinkId());

		UAMTripInfo tripInfo = new UAMTripInfo(startLink, endLink, uamRoute, departureTime);
		passengerEngine.bookTrip((MobsimPassengerAgent) agent, tripInfo);
	}

	@Override
	public void onPrepareSim() {
		uamModes = new HashSet<>();

		uamModes.add(UAMConstants.access + TransportMode.car);
		uamModes.add(UAMConstants.access + TransportMode.walk);
		eventsManager.addHandler(this);
	}

	@Override
	public void afterSim() {
		eventsManager.removeHandler(this);

	}

	@Override
	public void setInternalInterface(InternalInterface internalInterface) {
		this.internalInterface = internalInterface;

	}

	@Override
	public void notifyMobsimBeforeSimStep(@SuppressWarnings("rawtypes") MobsimBeforeSimStepEvent e) {

		processBookingAgents.clear();

		processBookingAgents.addAll(bookingAgents);

		bookingAgents.clear();
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

}
