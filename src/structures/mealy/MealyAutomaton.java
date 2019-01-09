package structures.mealy;

import bool.MyBooleanExpression;
import scenario.StringActions;
import scenario.StringScenario;

import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

public class MealyAutomaton {
    private final MealyNode startState;
    private final List<MealyNode> states;

    public MealyAutomaton(int statesCount) {
        this.startState = new MealyNode(0);
        this.states = new ArrayList<>();
        this.states.add(startState);
        for (int i = 1; i < statesCount; i++) {
            this.states.add(new MealyNode(i));
        }
    }

    public MealyNode startState() {
        return startState;
    }

    public MealyNode state(int i) {
        return states.get(i);
    }

    public List<MealyNode> states() {
        return states;
    }

    public int stateCount() {
        return states.size();
    }

    public void addTransition(MealyNode state, MealyTransition transition) {
        state.addTransition(transition.event(), transition.expr(), transition.actions(), transition.dst());
    }

    private MealyNode nextNode(MealyNode node, String event, MyBooleanExpression expr) {
        for (MealyTransition tr : node.transitions()) {
            if (tr.event().equals(event) && tr.expr() == expr) {
                return tr.dst();
            }
        }
        return null;
    }

    private StringActions nextActions(MealyNode node, String event, MyBooleanExpression expr) {
        for (MealyTransition tr : node.transitions()) {
            if (tr.event().equals(event) && tr.expr() == expr) {
                return tr.actions();
            }
        }
        return null;        
    }
    
    private MealyNode next(MealyNode node, String event, MyBooleanExpression expr, StringActions actions) {
        for (MealyTransition tr : node.transitions()) {
            boolean eventsEq = tr.event().equals(event);
            boolean exprEq = tr.expr() == expr;
            boolean actionsEq = tr.actions().equals(actions);                                                        
            if (eventsEq && exprEq && actionsEq) {
                return tr.dst();
            }
        }
        return null;
    }
    
    public boolean compliesWith(StringScenario scenario) {
        MealyNode node = startState;
        for (int pos = 0; pos < scenario.size(); pos++) {
            List<MealyNode> newNodes = new ArrayList<>();
            // multi-edge support
            for (String e : scenario.getEvents(pos)) {
                MealyNode newNode = next(node, e, scenario.getExpr(pos), scenario.getActions(pos));
                if (newNode == null) {
                    return false;
                }
                newNodes.add(newNode);
            }
            node = newNodes.get(0);
            if (new HashSet<>(newNodes).size() > 1) {
                return false;
            }
        }
        return true;
    }

    public int calcMissedActions(StringScenario scenario) {
        MealyNode node = startState;
        int missed = 0;
        for (int pos = 0; pos < scenario.size(); pos++) {
            StringActions nextActions = nextActions(node, scenario.getEvents(pos).get(0), scenario.getExpr(pos));
            node = nextNode(node, scenario.getEvents(pos).get(0), scenario.getExpr(pos));
            if (node == null) {
                return missed + scenario.size() - pos;
            }
            if (!scenario.getActions(pos).equals(nextActions)) {
                missed++;
            }
        }
        return missed;        
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("# generated file, don't try to modify\n"
            + "# command: dot -Tpng <filename> > tree.png\n"
            + "digraph Automaton {\n"
            + "    node [shape = circle];\n"
            + "    0 [style = \"bold\"];\n");

        for (MealyNode state : states) {
            Collection<MealyTransition> transitions = state.transitions();

            Collection<MealyTransition> mergedTransitions = new ArrayList<>();
            Map<String, List<MealyTransition>> groupedTransitions = transitionsByEventName(transitions);
            groupedTransitions.forEach((event, ts) -> {
                MyBooleanExpression common = canBeMerged(ts);
                if (common != null) {
                    MealyTransition t = ts.get(0);
                    mergedTransitions.add(new MealyTransition(
                            t.src(), t.dst(), event, common, t.actions()
                    ));
                } else {
                    mergedTransitions.addAll(ts);
                }
            });

            for (MealyTransition t : mergedTransitions) {
                sb.append("    ").append(t.src().number()).append(" -> ").append(t.dst().number());
                sb.append(" [label = \"").append(t.event()).append(" [").append(t.expr().toString()).append("] (")
                        .append(t.actions().toString()).append(") \"];\n");
            }
        }

        sb.append("}");
        return sb.toString();
    }

    private Map<String, List<MealyTransition>> transitionsByEventName(Collection<MealyTransition> transitions) {
        return transitions.stream().collect(
                Collectors.groupingBy(t -> extractEventName(t.event())));
    }

    private String extractEventName(String label) {
        return label.substring(0, label.length() - MyBooleanExpression.getVariablesNumber());
    }

    private String extractVarAssignmentName(String label) {
        return label.substring(label.length() - MyBooleanExpression.getVariablesNumber(), label.length());
    }

    private MyBooleanExpression canBeMerged(Collection<MealyTransition> transitions) {
        boolean sameDst = transitions.stream().map(MealyTransition::dst).distinct().count() == 1;
        if (!sameDst) {
            return null;
        }
        boolean sameActions = transitions.stream().map(MealyTransition::actions).distinct().count() == 1;
        if (!sameActions) {
            return null;
        }
        if (transitions.size() == Math.pow(2, MyBooleanExpression.getVariablesNumber())) {
            return MyBooleanExpression.getTautology();
        }
        // FIXME luckily we have less than 32 transitions
        int acc = ~0;
        for (MealyTransition t : transitions) {
            acc &= Integer.valueOf(extractVarAssignmentName(t.event()), 2);
        }
        if (Integer.bitCount(acc) == 1) {
            int pos = MyBooleanExpression.getVariablesNumber() - Integer.highestOneBit(acc);
            String varName = MyBooleanExpression.numberToVar(pos);
            try {
                return MyBooleanExpression.get(varName);
            } catch (ParseException e) {
                throw new AssertionError();
            }
        }
        return null;
    }
}
