package net.bhl.matsim.uam.analysis.traveltimes.utils;

import org.matsim.api.core.v01.Coord;


public class TripItem {
	public Coord origin;
	public Coord destination;
	public double departureTime;
	public double travelTime;
	public String description;

	public double distance;
	public double accessTime;
	public double flightTime;
	public double egressTime;
	public double processTime;
	public String accessMode;
	public String egressMode;
	public String originStation;
	public String destinationStation;
}
