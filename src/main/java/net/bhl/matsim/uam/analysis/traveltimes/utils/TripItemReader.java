package net.bhl.matsim.uam.analysis.traveltimes.utils;

import org.matsim.api.core.v01.Coord;
import org.matsim.contrib.util.CSVReaders;
import org.matsim.core.utils.misc.Time;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TripItemReader {
	public static List<TripItem> getTripItems(String tripsInput) throws IOException {
		List<TripItem> trips = new ArrayList<>();
		List<String[]> rows = CSVReaders.readCSV(tripsInput);
 		for (String[] row : rows.subList(1, rows.size())) {
 			int j = 0;
			TripItem trip = new TripItem();
			trip.origin = new Coord(Double.parseDouble(row[j++]), Double.parseDouble(row[j++]));
			trip.destination = new Coord(Double.parseDouble(row[j++]), Double.parseDouble(row[j++]));
			trip.departureTime = Time.parseTime(row[j]);
			trips.add(trip);
		}
		return trips;
	}
}
