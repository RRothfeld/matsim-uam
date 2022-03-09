package net.bhl.matsim.uam.analysis.linkspeed;

import net.bhl.matsim.uam.run.UAMConstants;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.vehicles.Vehicle;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class UAMLinkTravelSpeedHandler implements LinkEnterEventHandler,
            LinkLeaveEventHandler, PersonDepartureEventHandler {

    private Map<Id<Vehicle>,Double> enteredLinks = new HashMap<>() ;
    private Network network;
    private FileWriter fw;

    public UAMLinkTravelSpeedHandler(Network network, FileWriter writer) {
        this.network = network;
        this.fw = writer;

        try {
            fw.write("Time,Link,Length,Freespeed,Vehicle,ActualTime,ActualSpeed\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void reset(int iteration) {
        this.enteredLinks.clear();
    }

    @Override
    public void handleEvent(LinkEnterEvent event) {
        if(event.getVehicleId().toString().contains(UAMConstants.uam))
            this.enteredLinks.put(event.getVehicleId(), event.getTime());
    }

    @Override
    public void handleEvent(LinkLeaveEvent event) {
        if(this.enteredLinks.containsKey(event.getVehicleId())) {
            Link link = network.getLinks().get(event.getLinkId());
            double actTime = event.getTime() - this.enteredLinks.get(event.getVehicleId());
            double actSpeed = link.getLength() / actTime;
            try {
                fw.write(event.getTime() + ","
                    + event.getLinkId() + ","
                    + link.getLength() + ","
                    + link.getFreespeed() + ","
                    + event.getVehicleId() + ","
                    + actTime + ","
                    + actSpeed + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void handleEvent(PersonDepartureEvent event) {
        Id<Vehicle> vehId = Id.create(event.getPersonId(), Vehicle.class);
        this.enteredLinks.put(vehId, event.getTime());
    }

}
