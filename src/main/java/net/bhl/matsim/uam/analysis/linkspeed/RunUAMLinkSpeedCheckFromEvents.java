package net.bhl.matsim.uam.analysis.linkspeed;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsReaderXMLv1;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;

import java.io.FileWriter;
import java.io.IOException;

public class RunUAMLinkSpeedCheckFromEvents {
    // PROVIDE: NETWORK EVENTS OUTFILE-NAME-CSV
    public static void main(String[] args) {
        EventsManager manager = EventsUtils.createEventsManager();

        Network network = NetworkUtils.createNetwork();
        MatsimNetworkReader reader = new MatsimNetworkReader(network);
        reader.readFile(args[0]);

        try {
            FileWriter fw = new FileWriter(args[2]);
            manager.addHandler(new UAMLinkTravelSpeedHandler(network, fw));

            EventsReaderXMLv1 eventsReader = new EventsReaderXMLv1(manager);
            eventsReader.readFile(args[1]);

            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("done.");

    }
}
