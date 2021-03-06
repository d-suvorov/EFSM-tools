package automaton_builders;

/**
 * (c) Igor Buzhinsky
 */

import algorithms.AutomatonCompleter.CompletenessType;
import bnf_formulae.BooleanVariable;
import bool.MyBooleanExpression;
import org.apache.commons.lang3.tuple.Pair;
import sat_solving.Assignment;
import scenario.StringActions;
import structures.mealy.MealyAutomaton;
import structures.mealy.MealyNode;
import structures.mealy.MealyTransition;
import structures.mealy.ScenarioTree;
import verification.ltl.grammar.LtlUtils;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
    public static Pair<MealyAutomaton, List<BooleanVariable>> constructAutomatonFromAssignment(Logger logger, List<Assignment> ass,
                                                                                               ScenarioTree tree, int colorSize, boolean complete, CompletenessType completenessType) {
        final List<BooleanVariable> filteredYVars = new ArrayList<>();
        final int[] nodeColors = new int[tree.nodeCount()];
        
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
        MealyAutomaton ans = new MealyAutomaton(colorSize);
        for (int i = 0; i < tree.nodeCount(); i++) {
            int color = nodeColors[i];
            MealyNode state = ans.state(color);
            for (MealyTransition t : tree.nodes().get(i).transitions()) {
                if (!state.hasTransition(t.event(), t.expr())) {
                    int childColor = nodeColors[t.dst().number()];
                    state.addTransition(t.event(), t.expr(),
                        t.actions(), ans.state(childColor));
                }
            }
        }

        if (complete) {
            // add other transitions
            for (Assignment a : ass.stream()
                    .filter(aa -> aa.value && aa.var.name.startsWith("y_"))
                    .collect(Collectors.toList())) {
                String[] tokens = a.var.name.split("_");
                assert tokens.length == 4;
                int from = Integer.parseInt(tokens[1]);
                int to = Integer.parseInt(tokens[2]);
                String event = tokens[3];
    
                MealyNode state = ans.state(from);
    
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
                        include = state.transitionCount() == 0;
                    } else {
                        throw new AssertionError();
                    }
                    if (include) {
                        state.addTransition(event, MyBooleanExpression.getTautology(),
                            new StringActions(String.join(",",
                            properUniqueActions)), ans.state(to));
                        //logger.info("ADDING TRANSITION NOT FROM SCENARIOS " + a.var + " " + properUniqueActions);
                    }
                } else {
                    // check
                    MealyTransition t = state.transition(event, MyBooleanExpression.getTautology());
                    if (t.dst() != ans.state(to)) {
                        logger.severe("INVALID TRANSITION DESTINATION " + a.var);
                    }
                    List<String> actualActions = new ArrayList<>(new TreeSet<>(
                            Arrays.asList(t.actions().getActions())));
                    if (!actualActions.equals(properUniqueActions)) {
                        logger.severe("ACTIONS DO NOT MATCH");
                    }
                }
            }
        }
        
        return Pair.of(ans, filteredYVars);
    }
    
    protected static String ltl2limboole(String formula) {
        return LtlUtils.expandEventList(formula)
                .replace("&&", "&").replace("||", "|")
                .replaceAll("\\band\\b", "&").replaceAll("\\bor\\b", "|")
                .replaceAll("\\bnot\\b", "!");
    }
    
    protected static int timeLeftForSolver(long finishTime) {
        return (int) (finishTime - System.currentTimeMillis()) / 1000 + 1;
    }
}
