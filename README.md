
# UAM-Extension

This extension enhances MATSim (see the project's [Website](https://www.matsim.org) or [GitHub](https://github.com/matsim-org) pages) by allowing the definition and simulation of Urban Air Mobility infrastructure, vehicles, and operations. This extension is a collaborative development project between [Airbus Urban Mobility](https://www.airbus.com/innovation/urban-air-mobility.html), [Bauhaus Luftfahrt e.V.](https://www.bauhaus-luftfahrt.net), [ETH Zürich](https://www.ethz.ch), and [TU München](https://www.tse.bgu.tum.de) and authored by [Raoul Rothfeld](https://github.com/RRothfeld) and [Milos Balac](https://github.com/balacmi), with support from [Aitan Militão](https://github.com/Aitanm).

## Installation
Add the following to your maven pom.xml under `repositories`:
```xml
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
```
And the following to you maven pom.xml under `dependencies` for the latest version under active development:
```xml
        <dependency>
            <groupId>com.github.BauhausLuftfahrt</groupId>
            <artifactId>MATSim-UAM</artifactId>
            <version>master-SNAPSHOT</version>
        </dependency>
```
Older versions can be used by replacing the version text with any of the listed tags on [GitHub MATSim-UAM tags](https://github.com/BauhausLuftfahrt/MATSim-UAM/tags).

## Versions and Change Log

### v2.1
General:
- Updated documentation for scenario creation and travel time scripts
- Refactoring (e.g. replacement of "landing stations" with "stations")
- Removal of unused code and marking others as deprecated

UAM vehicles types and stations:
- Vehicle types must now include maximum range (applied per leg)
- Removal of all landing and parking space capacity fragments (their inclusion would warant new implementation)
- **No backwards compatibility of input uam files.**

Dispatcher:
- Now separately stores available UAM vehicles based on vehicle type
- Requests remain being resolved in a queue but based on requested range and vehicle type (if the required ranged vehicle type is unavailable, the request is being deferred, other requests may still be resolved if a vehicle of their required type is available)

Station selection:
- Introduction of isStaticSearchRadius config parameter (default: true), if set to false, the search radius is not an absolut distance for possible UAM stations from any given location but is read as a percentage which is being applied to the beeline distance between origin and destination location.

Scenario creation:
- Rework of scenario creation with separation into beeline and routes scenario creator
- Simplification of input parameters for scenario creation via input config
- Integration of non-uam config to uam-enabled config conversion into scenario creation

### v2.0
Travel time scripts:
- Replaced provision of, e.g., network and transit schedule via programme arguments to retrieval from config file
- Added distance and links path list to CalculateCarTravelTimes
- Added distance and route information to CalculatePTTravelTimes

Updates the extension to MATSim version 11 and current DVRP version. Major changes includes:
- UAMQSimPlugin is substituted by UAMQSimModule
- A new fleet is provided every new iteration by using UAMFleetData

### v1.1
Input/output:
- UAM-enabled MATSim networks are required to provide an flight attribute indicating every flight links' flight segment (e.g. vertical or horizontal)
- **No backwards compatibility of input network files.**
- Included script for generating networkChangeEvents (files) from simulation results

Logging:
- Limited waiting time and failed UAM routing warnings to ten occurrences

### v1.0
- Publication of first open source MATSim-UAM version

## Related Publications
The following list provides a reverse-chronological overview of publications related to or based on or related to the UAM-Extension:
* Rothfeld, R., Fu, M., Balac, M., & Antoniou, C. (2020). PRE-PRINT Potential Urban Air Mobility Travel Time Savings: An Exploratory Analysis of Munich, Paris, and San Francisco.
* Straubinger, A., Rothfeld, R., Shamiyeh, M., Buechter, K.-D., Kaiser, J., & Ploetner, K. O. (2020). An Overview of Current Research and Developments in Urban Air Mobility - Setting the Scene for UAM Introduction. Journal of Air Transport Management, 87(101852). https://doi.org/10.1016/j.jairtraman.2020.101852
* Ploetner, K., Rothfeld, R., Shamiyeh, M., Kabel, S., Frank, F., Straubinger, A., Llorca, C., Fu, M., Moreno, A., Pukhova, A., Zhang, Q., Al Haddad, C., Wagner, H., Antoniou, C., & Moeckel, R. (2020). Long-term Application Potential of Urban Air Mobility Complementing Public Transport: An Upper Bavaria Example. CEAS Aeronautical Journal: An Official Journal of the Council of European Aerospace Societies.
* Rothfeld, R., Straubinger, A., Fu, M., Al Haddad, C., & Antoniou, C. (2020). Urban air mobility. In C. Antoniou, D. Efthymiou, & E. Chaniotakis (Eds.), Demand for Emerging Transportation Systems - Modeling Adoption, Satisfaction, and Mobility Patterns (1st ed., pp. 267–284). Elsevier.
* Balac, M., Rothfeld, R. L., & Horl, S. (2019). The Prospects of on-demand Urban Air Mobility in Zurich, Switzerland. 2019 IEEE Intelligent Transportation Systems Conference, ITSC 2019, 906–913. https://doi.org/10.1109/ITSC.2019.8916972
* Rothfeld, R., Straubinger, A., Paul, A., & Antoniou, C. (2019). Analysis of European airports’ access and egress travel times using Google Maps. Transport Policy, 81(May), 148–162. https://doi.org/10.1016/j.tranpol.2019.05.021
* Balac, M., Rothfeld, R. L., & Horl, S. (2019). The Prospects of on-demand Urban Air Mobility in Zurich, Switzerland. 2019 IEEE Intelligent Transportation Systems Conference, ITSC 2019, 906–913. https://doi.org/10.1109/ITSC.2019.8916972
* Balac, M., Vetrella, A. R., Rothfeld, R., & Schmid, B. (2019). Demand Estimation for Aerial Vehicles in Urban Settings. IEEE Intelligent Transportation Systems Magazine, 11(3), 105–116. https://doi.org/10.1109/MITS.2019.2919500
* Fu, M., Rothfeld, R., & Antoniou, C. (2019). Exploring Preferences for Transportation Modes in an Urban Air Mobility Environment: Munich Case Study. Transportation Research Record. https://doi.org/10.1177/0361198119843858
* Rothfeld, R. L., Fu, M., & Antoniou, C. (2019). Analysis of Urban Air Mobility’s Transport Performance in Munich Metropolitan Region. In mobil.TUM 2019. https://doi.org/10.13140/RG.2.2.15444.42886
* Straubinger, A., & Rothfeld, R. (2018). Identification of Relevant Aspects for Personal Air Transport System Integration in Urban Mobility Modelling. Transport Research Arena TRA.
* Rothfeld, R. L., Balac, M., Ploetner, K. O., & Antoniou, C. (2018). Agent-based Simulation of Urban Air Mobility. 2018 Modeling and Simulation Technologies Conference, 1–10. https://doi.org/10.2514/6.2018-3891
* Rothfeld, R. L., Balac, M., Ploetner, K. O., & Antoniou, C. (2018). Initial Analysis of Urban Air Mobility’s Transport Performance in Sioux Falls. 2018 Aviation Technology, Integration, and Operations Conference, 1–13. https://doi.org/10.2514/6.2018-2886
* Shamiyeh, M., Rothfeld, R., & Hornung, M. (2018). A performance benchmark of recent personal air vehicle concepts for urban air mobility. 31st Congress of the International Council of the Aeronautical Sciences, ICAS.
* M. Balac, A. Vetrella, & K.W. Axhausen (2018). "Towards the integration of aerial transportation in urban settings", 97th Transportation Research Board Conference, Washington D.C.,USA, 2018.
