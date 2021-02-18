package net.bhl.matsim.uam.analysis.uamdemand;

import net.bhl.matsim.uam.run.UAMConstants;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.HashSet;

/**
 * This class writes a CSV file containing UAMDemand data.
 *
 * @author Aitanm (Aitan Militao), RRothfeld (Raoul Rothfeld)
 */
public class CSVUAMDemandWriter {
	final private HashSet<UAMDemandItem> uamData;
	final private String delimiter;

	public CSVUAMDemandWriter(Collection<UAMDemandItem> uamData) {
		this(uamData, ",");
	}

	public CSVUAMDemandWriter(Collection<UAMDemandItem> uamData, String delimiter) {
		this.uamData = new HashSet<>(uamData);
		this.delimiter = delimiter;
	}

	public void write(String outputPath) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath)));
		writer.write(formatHeader() + "\n");
		writer.flush();
		for (UAMDemandItem data : uamData) {
			writer.write(formatData(data) + "\n");
			writer.flush();
		}
		writer.flush();
		writer.close();
	}

	private String formatHeader() {
		return String.join(delimiter,
				new String[]{"personId", "originCoordX", "originCoordY", "originStationCoordX", "originStationCoordY",
						"destinationStationCoordX", "destinationStationCoordY", "destinationCoordX",
						"destinationCoordY", "startTime", "arrivalAtStationTime", "takeOffTime", "landingTime",
						"departureFromStationTime", "endTime", "vehicleId", "originStationId", "destinationStationId",
						"accessMode", "egressMode", UAMConstants.uam + "Trip"});
	}

	private String formatData(UAMDemandItem uamData) {
		try {
			return String.join(delimiter,
					new String[]{uamData.personId.toString(), String.valueOf(uamData.origin.getX()),
							String.valueOf(uamData.origin.getY()), String.valueOf(uamData.originStationCoord.getX()),
							String.valueOf(uamData.originStationCoord.getY()),
							String.valueOf(uamData.destinationStationCoord.getX()),
							String.valueOf(uamData.destinationStationCoord.getY()),
							String.valueOf(uamData.destination.getX()), String.valueOf(uamData.destination.getY()),
							String.valueOf(uamData.startTime), String.valueOf(uamData.arrivalAtStationTime),
							String.valueOf(uamData.takeOffTime), String.valueOf(uamData.landingTime),
							String.valueOf(uamData.departureFromStationTime), String.valueOf(uamData.endTime),
							uamData.vehicleId, uamData.originStationId.toString(),
							uamData.destinationStationId.toString(), uamData.accessMode,
							uamData.egressMode, String.valueOf(uamData.uamTrip)});
		} catch (Exception ignored) {
		}
		return UAMConstants.uam + "Data could not be read";
	}
}
