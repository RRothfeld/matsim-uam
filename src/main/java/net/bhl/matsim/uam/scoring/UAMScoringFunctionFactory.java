package net.bhl.matsim.uam.scoring;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.CharyparNagelScoringFunctionFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Create new scoring functions for UAM mode.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
@Singleton
public class UAMScoringFunctionFactory implements ScoringFunctionFactory {
	final private ScoringFunctionFactory standardFactory;

	@Inject
	public UAMScoringFunctionFactory(Scenario scenario) {
		standardFactory = new CharyparNagelScoringFunctionFactory(scenario);
	}

	@Override
	public ScoringFunction createNewScoringFunction(Person person) {
		return (SumScoringFunction) standardFactory.createNewScoringFunction(person);
	}
}