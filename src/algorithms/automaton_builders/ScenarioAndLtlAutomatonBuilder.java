package algorithms.automaton_builders;

/**
 * (c) Igor Buzhinsky
 */

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import sat_solving.Assignment;
import scenario.StringActions;
import structures.Automaton;
import structures.Node;
import structures.ScenarioTree;
import structures.Transition;
import algorithms.AutomatonCompleter.CompletenessType;
import bnf_formulae.BooleanVariable;
import bool.MyBooleanExpression;

/**
 * (c) Igor Buzhinsky
 */

public abstract class ScenarioAndLtlAutomatonBuilder {
	protected static void deleteTrash() {
		// delete files from the previous run
		Arrays.stream(new File(".").listFiles())
			.filter(f -> f.getName().startsWith("_tmp."))
			.forEach(File::delete);
	}
	
	/*
	 * Returns (automaton, transition variables supported by scenarios).
	 */
	public static Pair<Automaton, List<BooleanVariable>> constructAutomatonFromAssignment(Logger logger, List<Assignment> ass,
			ScenarioTree tree, int colorSize, boolean complete, CompletenessType completenessType) {
		List<BooleanVariable> filteredYVars = new ArrayList<>();
		int[] nodeColors = new int[tree.nodesCount()];

		// color the scenario tree codes according to the assignment
		ass.stream()
				.filter(a -> a.value && a.var.name.startsWith("x_"))
				.forEach(a -> {
					String[] tokens = a.var.name.split("_");
					assert tokens.length == 3;
					int node = Integer.parseInt(tokens[1]);
					int color = Integer.parseInt(tokens[2]);
					nodeColors[node] = color;
				});
		// add transitions from scenarios
		Automaton ans = new Automaton(colorSize);
		for (int i = 0; i < tree.nodesCount(); i++) {
			int color = nodeColors[i];
			Node state = ans.getState(color);
			for (Transition t : tree.getNodes().get(i).getTransitions()) {
				if (!state.hasTransition(t.getEvent(), t.getExpr())) {
					int childColor = nodeColors[t.getDst().getNumber()];
					state.addTransition(t.getEvent(), t.getExpr(),
						t.getActions(), ans.getState(childColor));
				}
			}
		}

		if (complete) {
			// add other transitions
			for (Assignment a : ass.stream()
					.filter(a -> a.value && a.var.name.startsWith("y_"))
					.collect(Collectors.toList())) {
				String[] tokens = a.var.name.split("_");
				assert tokens.length == 4;
				int from = Integer.parseInt(tokens[1]);
				int to = Integer.parseInt(tokens[2]);
				String event = tokens[3];
	
				Node state = ans.getState(from);
	
				if (state.hasTransition(event, MyBooleanExpression.getTautology())) {
					filteredYVars.add(a.var);
				}
				
				// include transitions not from scenarios
				List<String> properUniqueActions = new ArrayList<>();
				for (Assignment az : ass) {
					if (az.value && az.var.name.startsWith("z_" + from + "_")
							&& az.var.name.endsWith("_" + event)) {
						properUniqueActions.add(az.var.name.split("_")[2]);
					}
				}
				Collections.sort(properUniqueActions);
	
				if (!state.hasTransition(event, MyBooleanExpression.getTautology())) {
					// add
					boolean include;
					if (completenessType == CompletenessType.NORMAL) {
						include = true;
					} else if (completenessType == CompletenessType.NO_DEAD_ENDS) {
						include = state.transitionsCount() == 0;
					} else {
						throw new AssertionError();
					}
					if (include) {
						state.addTransition(event, MyBooleanExpression.getTautology(),
							new StringActions(String.join(",",
							properUniqueActions)), ans.getState(to));
						logger.info("ADDING TRANSITION NOT FROM SCENARIOS " + a.var + " " + properUniqueActions);
					}
				} else {
					// check
					Transition t = state.getTransition(event, MyBooleanExpression.getTautology());
					if (t.getDst() != ans.getState(to)) {
						logger.severe("INVALID TRANSITION DESTINATION " + a.var);
					}
					List<String> actualActions = new ArrayList<>(new TreeSet<>(
							Arrays.asList(t.getActions().getActions())));
					if (!actualActions.equals(properUniqueActions)) {
						logger.severe("ACTIONS DO NOT MATCH");
					}
				}
			}
		}
		
		return Pair.of(ans, filteredYVars);
	}
}