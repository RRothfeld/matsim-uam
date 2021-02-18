package net.bhl.matsim.uam.analysis.uamstations;

import net.bhl.matsim.uam.run.UAMConstants;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.HashSet;

/**
 * This class writes a CSV file containing UAM Stations data.
 *
 * @author Aitanm (Aitan Militao), RRothfeld (Raoul Rothfeld)
 */
public class CSVUAMStationWriter {
	final private HashSet<UAMStationItem> uamStations;
	final private String delimiter;

	public CSVUAMStationWriter(Collection<UAMStationItem> uamStations) {
		this(uamStations, ",");
	}

	public CSVUAMStationWriter(Collection<UAMStationItem> uamStations, String delimiter) {
		this.uamStations = new HashSet<>(uamStations);
		this.delimiter = delimiter;
	}

	public void write(String outputPath) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath)));
		writer.write(formatHeader() + "\n");
		writer.flush();
		for (UAMStationItem station : uamStations) {
			writer.write(formatData(station) + "\n");
			writer.flush();
		}
		writer.flush();
		writer.close();
	}

	private String formatHeader() {
		return String.join(delimiter, new String[]{"name", "id", "landingcap", "preflighttime", "postflighttime",
				"defaultwaittime", "link"});
	}

	private String formatData(UAMStationItem station) {
		try {
			return String.join(delimiter,
					new String[]{station.name,
							station.id.toString(),
							String.valueOf(station.preflighttime),
							String.valueOf(station.postflighttime),
							String.valueOf(station.defaultwaittime),
							station.link});
		} catch (Exception ignored) {
		}
		return UAMConstants.uam + "Data could not be read";
	}
}
