package automaton_builders;

/**
 * (c) Igor Buzhinsky
 */

import algorithms.AutomatonCompleter;
import algorithms.AutomatonCompleter.CompletenessType;
import bool.MyBooleanExpression;
import exception.AutomatonFoundException;
import exception.TimeLimitExceededException;
import sat_solving.SolverResult;
import sat_solving.SolverResult.SolverResults;
import scenario.StringActions;
import scenario.StringScenario;
import structures.mealy.MealyAutomaton;
import structures.mealy.MealyNode;
import structures.mealy.MealyTransition;
import structures.mealy.ScenarioTree;
import verification.ltl.grammar.LtlNode;
import verification.verifier.Verifier;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class BacktrackingAutomatonBuilder {
    private static abstract class TraverseState {
        protected final int colorSize;
        protected final List<String> events;
        protected final List<String> actions;
        protected final long finishTime;
        protected final MealyAutomaton automaton;
        protected final int[] coloring;
        protected final Verifier verifier;
        protected CompletenessType completenessType;

        /*
         * auxiliary list for search space reduction
         */
        protected final int[] incomingTransitionNumbers;
        
        public TraverseState(int colorSize, List<String> events, List<String> actions, long finishTime,
                int[] coloring, Verifier verifier, CompletenessType completenessType) {
            this.colorSize = colorSize;
            this.events = events;
            this.actions = actions;
            this.finishTime = finishTime;
            this.coloring = coloring;
            this.verifier = verifier;
            this.completenessType = completenessType;
            automaton = new MealyAutomaton(colorSize);
            incomingTransitionNumbers = new int[colorSize];
        }
        
        protected void checkTimeLimit() throws TimeLimitExceededException {
            if (System.currentTimeMillis() > finishTime) {
                throw new TimeLimitExceededException();
            }
        }
        
        protected boolean verify() {
            return verifier.verify(automaton);
        }
        
        public abstract void backtracking() throws AutomatonFoundException, TimeLimitExceededException;
    }
    
    private static class OrdinaryTraverseState extends TraverseState {
        private List<MealyTransition> frontier;
        
        public OrdinaryTraverseState(ScenarioTree tree, Verifier verifier, int colorSize, long finishTime,
                List<String> events, List<String> actions, CompletenessType completenessType) {
            super(colorSize, events, actions, finishTime, new int[tree.nodeCount()],
                    verifier, completenessType);
            frontier = new ArrayList<>(tree.root().transitions());
        }
        
        /*
         * Returns whether the automaton is consistent with scenarios.
         */
        private boolean findNewFrontier() {
            final List<MealyTransition> finalFrontier = new ArrayList<>();
            final List<MealyTransition> currentFrontier = new ArrayList<>();
            currentFrontier.addAll(frontier);
            while (!currentFrontier.isEmpty()) {
                final MealyTransition t = currentFrontier.get(currentFrontier.size() - 1);
                currentFrontier.remove(currentFrontier.size() - 1);
                final int stateFrom = coloring[t.src().number()];
                final MealyTransition autoT = automaton.state(stateFrom)
                        .transition(t.event(), t.expr());
                if (autoT == null) {
                    finalFrontier.add(t);
                } else if (autoT.actions().equals(t.actions())) {
                    currentFrontier.addAll(t.dst().transitions());
                    coloring[t.dst().number()] = autoT.dst().number();
                } else {
                    return false;
                }
            }
            frontier = finalFrontier;
            return true;
        }

        @Override
        public void backtracking() throws AutomatonFoundException, TimeLimitExceededException {
            checkTimeLimit();
            
            MealyTransition t = frontier.get(0);
            // further edges should be added from this state:
            final MealyNode stateFrom = automaton.state(coloring[t.src().number()]);
            final String event = t.event();
            final MyBooleanExpression expression = t.expr();
            final StringActions stringActions = t.actions();
            assert stateFrom.transition(event, expression) == null;
            for (int dst = 0; dst < colorSize; dst++) {
                if (dst > 1 && incomingTransitionNumbers[dst - 1] == 0) {
                    break;
                    // this is done to reduce repeated checks (similar to BFS constraints)
                }
                
                MealyTransition autoT = new MealyTransition(stateFrom,
                        automaton.state(dst), event, expression, stringActions);
                automaton.addTransition(stateFrom, autoT);
                incomingTransitionNumbers[dst]++;
                final List<MealyTransition> frontierBackup = frontier;
                
                if (findNewFrontier() && verify()) {
                    if (frontier.isEmpty()) {
                        new AutomatonCompleter(verifier, automaton, events,
                                actions, finishTime, completenessType).ensureCompleteness();
                    } else {
                        backtracking();
                    }
                }
                
                frontier = frontierBackup;
                stateFrom.removeTransition(autoT);
                incomingTransitionNumbers[dst]--;
            }
        }
    }
    
    private static class TraverseStateWithMultiEdges extends TraverseState {
        private List<List<MealyTransition>> frontier;

        public TraverseStateWithMultiEdges(ScenarioTree tree, Verifier verifier, int colorSize, long finishTime,
                List<String> events, List<String> actions, CompletenessType completenessType) {
            super(colorSize, events, actions, finishTime, new int[tree.nodeCount()],
                    verifier, completenessType);
            frontier = new ArrayList<>(groupByDst(tree.root().transitions()));
        }
        
        /*
         * Returns whether the automaton is consistent with scenarios.
         */
        private boolean findNewFrontier() {
            final List<List<MealyTransition>> finalFrontier = new ArrayList<>();
            final List<List<MealyTransition>> currentFrontier = new ArrayList<>();
            currentFrontier.addAll(frontier);
            while (!currentFrontier.isEmpty()) {
                final List<MealyTransition> tList = currentFrontier.get(currentFrontier.size() - 1);
                currentFrontier.remove(currentFrontier.size() - 1);
                final int stateFrom = coloring[tList.get(0).src().number()];
                final List<MealyTransition> transitions = new ArrayList<>();
                boolean wasNull = false;
                boolean wasProper = false;
                for (MealyTransition t : tList) {
                    final MealyTransition autoT = automaton.state(stateFrom)
                            .transition(t.event(), tautology());
                    if (autoT != null && !autoT.actions().equals(t.actions())) {
                        return false;
                    }
                    wasNull |= autoT == null;
                    wasProper |= autoT != null;
                    transitions.add(autoT);
                }
                if (wasNull && wasProper) {
                    return false;
                } else if (wasProper) {
                    final int autoDst = transitions.get(0).dst().number();
                    if (!transitions.stream().allMatch(t -> t.dst().number() == autoDst)) {
                        return false;
                    }
                    final MealyNode scDst = tList.get(0).dst();
                    currentFrontier.addAll(groupByDst(scDst.transitions()));
                    coloring[scDst.number()] = autoDst;
                } else {
                    finalFrontier.add(tList);
                }
            }
            frontier = finalFrontier;
            return true;
        }

        @Override
        public void backtracking() throws AutomatonFoundException, TimeLimitExceededException {
            checkTimeLimit();
            
            final List<MealyTransition> tList = frontier.get(0);
            // further edges should be added from this state:
            final MealyNode stateFrom = automaton.state(coloring[tList.get(0).src().number()]);
            final StringActions stringActions = tList.get(0).actions();
            for (int dst = 0; dst < colorSize; dst++) {
                if (dst > 1 && incomingTransitionNumbers[dst - 1] == 0) {
                    break;
                    // this is done to reduce repeated checks
                }
                
                final List<MealyTransition> addedTransitions = new ArrayList<>();
                for (MealyTransition t : tList) {
                    MealyTransition autoT = new MealyTransition(stateFrom,
                            automaton.state(dst), t.event(), tautology(), stringActions);
                    addedTransitions.add(autoT);
                    if (automaton.state(stateFrom.number()).hasTransition(t.event(), tautology())) {
                        throw new AssertionError();
                    }
                    automaton.addTransition(stateFrom, autoT);
                }
                incomingTransitionNumbers[dst]++;
                
                final List<List<MealyTransition>> frontierBackup = frontier;
                
                if (findNewFrontier() && verify()) {
                    if (frontier.isEmpty()) {
                        new AutomatonCompleter(verifier, automaton, events,
                                actions, finishTime, completenessType).ensureCompleteness();
                    } else {
                        backtracking();
                    }
                }
                
                frontier = frontierBackup;
                addedTransitions.forEach(stateFrom::removeTransition);
                incomingTransitionNumbers[dst]--;
            }
        }
    }
    
    private static class TraverseStateWithCoverageAndWeakCompleteness extends TraverseState {
        private final Map<String, List<String>> eventExtensions;
        private List<List<MealyTransition>> frontier;
        private final List<String> eventNames;

        public TraverseStateWithCoverageAndWeakCompleteness(ScenarioTree tree, int colorSize, long finishTime,
                List<String> events, List<String> eventNames, int variables) {
            super(colorSize, events, null, finishTime, new int[tree.nodeCount()], null, null);
            frontier = new ArrayList<>(groupByDst(tree.root().transitions()));
            this.eventNames = eventNames;
            this.eventExtensions = eventExtensions(events, eventNames, variables);
        }
        
        /*
         * Returns whether the automaton is consistent with scenarios.
         */
        private boolean findNewFrontier() {
            final List<List<MealyTransition>> finalFrontier = new ArrayList<>();
            final List<List<MealyTransition>> currentFrontier = new ArrayList<>();
            currentFrontier.addAll(frontier);
            while (!currentFrontier.isEmpty()) {
                final List<MealyTransition> tList = currentFrontier.get(currentFrontier.size() - 1);
                currentFrontier.remove(currentFrontier.size() - 1);
                final int stateFrom = coloring[tList.get(0).src().number()];
                final List<MealyTransition> transitions = new ArrayList<>();
                boolean wasNull = false;
                boolean wasProper = false;
                for (MealyTransition t : tList) {
                    final MealyTransition autoT = automaton.state(stateFrom)
                            .transition(t.event(), tautology());
                    if (autoT != null && !autoT.actions().equals(t.actions())) {
                        return false;
                    }
                    wasNull |= autoT == null;
                    wasProper |= autoT != null;
                    transitions.add(autoT);
                }
                if (wasNull && wasProper) {
                    return false;
                } else if (wasProper) {
                    final int autoDst = transitions.get(0).dst().number();
                    if (!transitions.stream().allMatch(t -> t.dst().number() == autoDst)) {
                        return false;
                    }
                    final MealyNode scDst = tList.get(0).dst();
                    currentFrontier.addAll(groupByDst(scDst.transitions()));
                    coloring[scDst.number()] = autoDst;
                } else {
                    finalFrontier.add(tList);
                }
            }
            frontier = finalFrontier;
            return true;
        }

        @Override
        public void backtracking() throws AutomatonFoundException, TimeLimitExceededException {
            checkTimeLimit();
            
            final List<MealyTransition> tList = frontier.get(0);
            // further edges should be added from this state:
            final MealyNode stateFrom = automaton.state(coloring[tList.get(0).src().number()]);
            final StringActions stringActions = tList.get(0).actions();
            for (int dst = 0; dst < colorSize; dst++) {
                if (dst > 1 && incomingTransitionNumbers[dst - 1] == 0) {
                    break;
                    // this is done to reduce repeated checks
                }
                
                final List<MealyTransition> addedTransitions = new ArrayList<>();
                for (MealyTransition t : tList) {
                    MealyTransition autoT = new MealyTransition(stateFrom,
                            automaton.state(dst), t.event(),
                            tautology(), stringActions);
                    addedTransitions.add(autoT);
                    automaton.addTransition(stateFrom, autoT);
                }
                incomingTransitionNumbers[dst]++;
                
                final List<List<MealyTransition>> frontierBackup = frontier;
                
                if (findNewFrontier()) {
                    if (frontier.isEmpty()) {
                        // check weak completeness
                        if (isWeakComplete(automaton, eventNames, eventExtensions)) {
                            throw new AutomatonFoundException(automaton);
                        } // else do nothing
                    } else {
                        backtracking();
                    }
                }
                
                frontier = frontierBackup;
                addedTransitions.forEach(stateFrom::removeTransition);
                incomingTransitionNumbers[dst]--;
            }
        }
    }
    
    private static class TraverseStateWithErrors extends TraverseState {
        private final Map<String, List<String>> eventExtensions;
        private List<FrontierElement> frontier = new ArrayList<>();
        private final List<String> eventNames;
        private final int errorNumber;
        private final int[][] coloring; // overrides
        private final List<StringScenario> scenarios;

        private class FrontierElement {
            final int scenarioIndex;
            final int scenarioPosition;
            
            FrontierElement(int scenarioIndex, int scenarioPosition) {
                this.scenarioIndex = scenarioIndex;
                this.scenarioPosition = scenarioPosition;
            }
            
            int coloring() {
                return coloring[scenarioIndex][scenarioPosition];
            }
            
            void setColoring(int value) {
                coloring[scenarioIndex][scenarioPosition] = value;
            }
            
            StringScenario scenario() {
                return scenarios.get(scenarioIndex);
            }
            
            List<String> events() {
                return scenario().getEvents(scenarioPosition);
            }

            @Override
            public String toString() {
                return "(" + scenarioIndex + ":" + scenarioPosition + ")";
            }
            
            FrontierElement advance() {
                final int newPos = scenarioPosition + 1;
                return scenario().size() > newPos
                        ? new FrontierElement(scenarioIndex, newPos) : null;
            }
        }
        
        public TraverseStateWithErrors(List<StringScenario> scenarios, int colorSize, long finishTime,
                List<String> events, List<String> eventNames, int variables, int errorNumber) {
            super(colorSize, events, null, finishTime, null, null, null);
            for (int i = 0; i < scenarios.size(); i++) {
                frontier.add(new FrontierElement(i, 0));
            }
            this.eventNames = eventNames;
            this.eventExtensions = eventExtensions(events, eventNames, variables);
            this.errorNumber = errorNumber;
            coloring = new int[scenarios.size()][];
            for (int i = 0; i < scenarios.size(); i++) {
                coloring[i] = new int[scenarios.get(i).size()];
            }
            this.scenarios = scenarios;
        }

        /*
         * Returns whether the automaton is consistent with scenarios (assuming there is a number of possible errors).
         */
        private boolean findNewFrontier() {
            final List<FrontierElement> newFrontier = new ArrayList<>();
            for (FrontierElement elem : frontier) {
                FrontierElement cur = elem;
                while (true) {
                    final int stateFrom = cur.coloring();
                    final List<MealyTransition> transitions = new ArrayList<>();
                    boolean wasNull = false;
                    boolean wasProper = false;
                    for (String event : cur.events()) {
                        final MealyTransition autoT = automaton.state(stateFrom)
                                .transition(event, tautology());
                        wasNull |= autoT == null;
                        wasProper |= autoT != null;
                        transitions.add(autoT);
                    }
                    if (wasNull && wasProper) {
                        return false;
                    } else if (wasProper) {
                        final int autoDst = transitions.get(0).dst().number();
                        if (!transitions.stream().allMatch(t -> t.dst().number() == autoDst)) {
                            return false;
                        }
                        final FrontierElement newElem = cur.advance();
                        if (newElem != null) {
                            cur = newElem;
                            cur.setColoring(autoDst);
                        } else {
                            // the frontier becomes smaller
                            break;
                        }
                    } else {
                        // the frontier retains its size
                        newFrontier.add(cur);
                        break;
                    }
                }
            }
            frontier = newFrontier;
            return label();
        }

        private static StringActions mode(List<StringActions> list) {
            if (list.isEmpty()) {
                throw new AssertionError();
            }
            final Map<StringActions, Integer> map = new HashMap<>();
            int maxCount = 0;
            StringActions ans = null;
            for (StringActions elem : list) {
                if (!map.containsKey(elem)) {
                    map.put(elem, 0);
                }
                int count = map.get(elem) + 1;
                map.put(elem, count);
                if (count > maxCount) {
                    maxCount = count;
                    ans = elem;
                }
            }
            if (ans == null) {
                throw new AssertionError();
            }
            return ans;
        }
        
        /*
         * Returns whether the labeling was successful regarding the number of possible errors.
         */
        private boolean label() {
            final Map<MealyTransition, List<StringActions>> actionOccurrencies = new HashMap<>();
            for (int i = 0; i < colorSize; i++) {
                for (MealyTransition t : automaton.state(i).transitions()) {
                    actionOccurrencies.put(t, new ArrayList<>());
                }
            }
            for (StringScenario sc : scenarios) {
                MealyNode state = automaton.startState();
                for (int i = 0; i < sc.size(); i++) {
                    final MealyTransition t = state.transition(sc.getEvents(i).get(0), tautology());
                    if (t == null) {
                        break;
                    }
                    for (String e : sc.getEvents(i)) {
                        actionOccurrencies.get(state.transition(e, tautology())).add(sc.getActions(i));
                    }
                    state = t.dst();
                }
            }
            int madeErrors = 0;
            for (int i = 0; i < colorSize; i++) {
                for (MealyTransition t : new ArrayList<>(automaton.state(i).transitions())) {
                    final List<StringActions> actions = actionOccurrencies.get(t);
                    final StringActions mode = mode(actions);
                    automaton.state(i).removeTransition(t);
                    automaton.state(i).addTransition(t.event(), t.expr(), mode, t.dst());
                    for (StringActions a : actions) {
                        if (!a.equals(mode)) {
                            madeErrors++;
                        }
                    }
                }
            }
            return madeErrors <= errorNumber;
        }
        
        @Override
        public void backtracking() throws AutomatonFoundException, TimeLimitExceededException {
            checkTimeLimit();
            
            final FrontierElement elem = frontier.get(0);
            final MealyNode stateFrom = automaton.state(elem.coloring());
            for (int dst = 0; dst < colorSize; dst++) {
                if (dst > 1 && incomingTransitionNumbers[dst - 1] == 0) {
                    break;
                    // this is done to reduce repeated checks
                }
                
                final List<MealyTransition> addedTransitions = new ArrayList<>();
                for (String event : elem.events()) {
                    final MealyTransition autoT = new MealyTransition(stateFrom, automaton.state(dst),
                            event, tautology(), new StringActions(""));
                    addedTransitions.add(autoT);
                    automaton.addTransition(stateFrom, autoT);
                }
                incomingTransitionNumbers[dst]++;
                
                final List<FrontierElement> frontierBackup = frontier;
                
                if (findNewFrontier()) {
                    if (frontier.isEmpty()) {
                        // check weak completeness
                        if (isWeakComplete(automaton, eventNames, eventExtensions)) {
                            throw new AutomatonFoundException(automaton);
                        } // else do nothing
                    } else {
                        backtracking();
                    }
                }
                
                frontier = frontierBackup;
                addedTransitions.forEach(stateFrom::removeTransition);
                incomingTransitionNumbers[dst]--;
            }
        }
    }
    
    private static Map<String, List<String>> eventExtensions(List<String> events, List<String> eventNames, int variables) {
        final Map<String, List<String>> eventExtensions = new HashMap<>();
        for (String eventName : eventNames) {
            eventExtensions.put(eventName, events.stream().filter(e -> e.substring(0, e.length() - variables)
                    .equals(eventName)).collect(Collectors.toList()));
        }
        return eventExtensions;
    }
    
    private static MyBooleanExpression tautology() {
        return MyBooleanExpression.getTautology();
    }
    
    private static boolean isWeakComplete(MealyAutomaton automaton, List<String> eventNames, Map<String, List<String>> eventExtensions) {
        for (int i = 0; i < automaton.stateCount(); i++) {
            for (String initialEvent : eventNames) {
                // is there at least one transition with this initialEvent from this state?
                boolean hasTransition = false;
                boolean allTransitions = true;
                for (String e : eventExtensions.get(initialEvent)) {
                    if (automaton.state(i).hasTransition(e, MyBooleanExpression.getTautology())) {
                        hasTransition = true;
                    } else {
                        allTransitions = false;
                    }
                }
                if (hasTransition && !allTransitions) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /*
     * For multi-edges obtained in the case of variables.
     */
    private static Collection<List<MealyTransition>> groupByDst(Collection<MealyTransition> transitions) {
        final Map<Integer, List<MealyTransition>> transitionGroups = new TreeMap<>();
        for (MealyTransition t : transitions) {
            final int num = t.dst().number();
            if (!transitionGroups.containsKey(num)) {
                transitionGroups.put(num, new ArrayList<>());
            }
            transitionGroups.get(num).add(t);
        }
        
        return transitionGroups.values();
    }
    
    public static Optional<MealyAutomaton> build(Logger logger, ScenarioTree tree, int size,
                                                 List<LtlNode> formulae, List<String> events, List<String> actions, Verifier verifier,
                                                 long finishTime, CompletenessType completenessType, int variables,
                                                 boolean ensureCoverageAndWeakCompleteness, List<String> eventNames,
                                                 int errorNumber, List<StringScenario> scenarios) throws IOException {
        try {
            if (errorNumber >= 0) {
                // for Vladimir's comparison
                new TraverseStateWithErrors(scenarios, size, finishTime, events, eventNames,
                        variables, errorNumber).backtracking();
            } else if (ensureCoverageAndWeakCompleteness) {
                // for Vladimir's comparison
                new TraverseStateWithCoverageAndWeakCompleteness(tree, size, finishTime,
                        events, eventNames, variables).backtracking();
            } else if (variables == 0) {
                new OrdinaryTraverseState(tree, verifier, size, finishTime, events, actions,
                        completenessType).backtracking();
            } else {
                // for Daniil's instances
                new TraverseStateWithMultiEdges(tree, verifier, size, finishTime, events, actions,
                        completenessType).backtracking();
            }
        } catch (AutomatonFoundException e) {
            return Optional.of(e.automaton);
        } catch (TimeLimitExceededException e) {
            logger.info("TOTAL TIME LIMIT EXCEEDED, ANSWER IS UNKNOWN.");
            return Optional.empty();
        }
        logger.info(new SolverResult(SolverResults.UNSAT).toString());
        return Optional.empty();
    }
}
