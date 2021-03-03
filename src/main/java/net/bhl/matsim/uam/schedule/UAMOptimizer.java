package net.bhl.matsim.uam.schedule;

import java.util.List;

import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.optimizer.VrpOptimizer;
import org.matsim.contrib.dvrp.schedule.Schedule;
import org.matsim.contrib.dvrp.schedule.Schedules;
import org.matsim.contrib.dvrp.schedule.Task;
import org.matsim.contrib.dvrp.tracker.OnlineTrackerListener;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.events.MobsimBeforeSimStepEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimBeforeSimStepListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.bhl.matsim.uam.dispatcher.UAMDispatcher;
import net.bhl.matsim.uam.infrastructure.UAMVehicle;
import net.bhl.matsim.uam.passenger.UAMRequest;

/**
 * An optimizer for UAM vehicles schedule.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
@Singleton
public class UAMOptimizer implements VrpOptimizer, OnlineTrackerListener, MobsimBeforeSimStepListener {
	private double now;
	private UAMDispatcher dispatcher;
	@Inject
	private EventsManager eventsManager;

	@Override
	public void requestSubmitted(Request request) {
		UAMRequest uamRequest = (UAMRequest) request;
		dispatcher = uamRequest.getDispatcher();

		synchronized (dispatcher) {
			dispatcher.onRequestSubmitted(uamRequest);
		}
	}

	@Override
	public void nextTask(DvrpVehicle vehicle) {

		Schedule schedule = vehicle.getSchedule();
		// this happens at the start of the simulation since
		// the schedule has not started yet
		if (schedule.getStatus() != Schedule.ScheduleStatus.STARTED) {
			schedule.nextTask();
			return;
		}

		// get the current task and make it end now
		Task currentTask = schedule.getCurrentTask();
		currentTask.setEndTime(now);

		List<? extends Task> tasks = schedule.getTasks();
		int index = currentTask.getTaskIdx() + 1;

		Task nextTask;
		if (index < tasks.size()) {
			nextTask = tasks.get(index);
		} else {
			throw new IllegalStateException("A UAM schedule should never end!");
		}

		double startTime = now;

		Task indexTask;
		// we have to adapt the times of the rest of the tasks
		// in order to take into account any delays vehicle has experienced so far
		while (index < tasks.size()) {
			indexTask = tasks.get(index);

			if (indexTask.getTaskType().equals(UAMTaskType.STAY)) {
				if (indexTask.getEndTime() < startTime)
					indexTask.setEndTime(startTime);
			} else {
				indexTask.setEndTime(indexTask.getEndTime() - indexTask.getBeginTime() + startTime);
			}

			indexTask.setBeginTime(startTime);
			startTime = indexTask.getEndTime();
			index++;
		}

		ensureNonFinishingSchedule(schedule);
		schedule.nextTask();
		ensureNonFinishingSchedule(schedule);

		if (nextTask != null) {
			synchronized (dispatcher) {
				dispatcher.onNextTaskStarted((UAMVehicle) vehicle);
			}
		}

		if (nextTask instanceof UAMDropoffTask) {
			// throws a transit event in order to let us know that the drop off
			// has been performed
			// this is used later in the analysis to know which person was
			// dropped off
			processTransitEvent((UAMDropoffTask) nextTask);
		}
	}

	private void ensureNonFinishingSchedule(Schedule schedule) {
		Task lastTask = (Task) Schedules.getLastTask(schedule);

		if (!lastTask.getTaskType().equals(UAMTaskType.STAY)) {
			throw new IllegalStateException("A UAM schedule should always end with a STAY task");
		}

		if (!Double.isInfinite(lastTask.getEndTime())) {
			throw new IllegalStateException("A UAM schedule should always end at time Infinity");
		}
	}

	@Override
	public void notifyMobsimBeforeSimStep(MobsimBeforeSimStepEvent e) {
		now = e.getSimulationTime();
	}

	private void processTransitEvent(UAMDropoffTask task) {
		for (UAMRequest request : task.getRequests()) {
			eventsManager.processEvent(new UAMTransitEvent(request, now));
		}
	}

	public void vehicleEnteredNextLink(DvrpVehicle vehicle, Link link) {

	}
}
