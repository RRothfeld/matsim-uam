package net.bhl.matsim.uam.schedule;

import org.matsim.contrib.dvrp.schedule.Task;

public enum UAMTaskType implements Task.TaskType {
	PICKUP, DROPOFF, FLY, STAY, PARK, TURNAROUND
}
