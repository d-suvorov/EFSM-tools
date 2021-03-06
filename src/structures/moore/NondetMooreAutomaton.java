package structures.moore;

import continuous_trace_builders.*;
import continuous_trace_builders.parameters.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import scenario.StringActions;
import scenario.StringScenario;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NondetMooreAutomaton {
    private final List<Boolean> isInitial = new ArrayList<>();
    private final List<MooreNode> states = new ArrayList<>();
    
    private Set<MooreTransition> unsupportedTransitions = new HashSet<>();

    private List<Integer> loopConstraints = null;

    public void setLoopConstraints(Map<MooreNode, Integer> loopConstraints) {
        this.loopConstraints = new ArrayList<>();
        for (int i = 0; i < stateCount(); i++) {
            this.loopConstraints.add(loopConstraints.get(states.get(i)));
        }
    }

    public Set<MooreTransition> unsupportedTransitions() {
        return unsupportedTransitions;
    }

    public double transitionFraction(Predicate<MooreTransition> p) {
        int matched = 0;
        int all = 0;
        for (MooreNode state : states) {
            for (MooreTransition t : state.transitions()) {
                all++;
                matched += p.test(t) ? 1 : 0;
            }
        }
        return (double) matched / all;
    }

    public double unsupportedTransitionFraction() {
        return transitionFraction(unsupportedTransitions::contains);
    }

    public int transitionNumber() {
        return states.stream().mapToInt(s -> s.transitions().size()).sum();
    }

    public double loopFraction() {
        return transitionFraction(t -> t.dst() == t.src());
    }

    public static NondetMooreAutomaton readGV(String filename) throws FileNotFoundException {
        final Map<String, List<String>> actionRelation = new LinkedHashMap<>();
        final Map<String, List<Pair<Integer, String>>> transitionRelation = new LinkedHashMap<>();
        actionRelation.put("init", new ArrayList<>());
        transitionRelation.put("init", new ArrayList<>());
        final Set<String> events = new LinkedHashSet<>();
        final Set<String> actions = new LinkedHashSet<>();
        final Set<Integer> initial = new LinkedHashSet<>();
        
        try (Scanner sc = new Scanner(new File(filename))) {
            while (sc.hasNextLine()) {
                final String line = sc.nextLine();
                if (!line.contains(";") || line.startsWith("#")) {
                    continue;
                }
                final String tokens[] = line.split(" +");
                if (line.contains("->")) {
                    final String from = tokens[1];
                    // the replacement is needed for initial state declarations as transitions from init_n:
                    final Integer to = Integer.parseInt(tokens[3].replaceAll(";", ""));
                    if (from.equals("init" + to)) {
                        initial.add(to);
                    } else {
                        final String event = tokens[5];
                        transitionRelation.get(from).add(Pair.of(to, event));
                        events.add(event);
                    }
                } else {
                    final String from = tokens[1];
                    if (from.startsWith("init") || from.equals("node")) {
                        continue;
                    }
                    transitionRelation.put(from, new ArrayList<>());
                    final String[] labels = line.split("\"")[1].split("\\\\n");
                    final List<String> theseActions = Arrays.asList(labels).subList(1, labels.length);
                    actionRelation.put(from, theseActions);
                    actions.addAll(theseActions);
                }
            }
        }

        int maxState = 0;
        for (List<Pair<Integer, String>> list : transitionRelation.values()) {
            for (Pair<Integer, String> p : list) {
                maxState = Math.max(maxState, p.getLeft());
            }
        }
        final List<Boolean> initialVector = new ArrayList<>();
        final List<StringActions> actionVector = new ArrayList<>();
        for (int i = 0; i <= maxState; i++) {
            initialVector.add(initial.contains(i));
            actionVector.add(new StringActions(String.join(", ", actionRelation.get(i + ""))));
        }
        
        final NondetMooreAutomaton a = new NondetMooreAutomaton(maxState + 1, actionVector, initialVector);
        for (int i = 0; i <= maxState; i++) {
            for (Pair<Integer, String> p : transitionRelation.get(String.valueOf(i))) {
                a.state(i).addTransition(p.getRight(), a.state(p.getLeft()));
            }
        }
        return a;
    }

    public NondetMooreAutomaton(List<MooreNode> states, List<Boolean> isInitial) {
        this.states.addAll(states);
        this.isInitial.addAll(isInitial);
    }
    
    public NondetMooreAutomaton(int statesCount, List<StringActions> actions, List<Boolean> isInitial) {
        for (int i = 0; i < statesCount; i++) {
            states.add(new MooreNode(i, actions.get(i)));
        }
        this.isInitial.addAll(isInitial);
    }

    public boolean isInitialState(int index) {
        return isInitial.get(index);
    }
    
    public List<Integer> initialStates() {
        final List<Integer> result = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
            if (isInitialState(i)) {
                result.add(i);
            }
        }
        return result;
    }

    public MooreNode state(int i) {
        return states.get(i);
    }

    public List<MooreNode> states() {
        return states;
    }

    public int stateCount() {
        return states.size();
    }

    public void addTransition(MooreNode state, MooreTransition transition) {
        state.addTransition(transition);
    }
    
    public void removeTransition(MooreNode state, MooreTransition transition) {
        state.removeTransition(transition);
    }

    public String toString(Map<String, String> colorRules, Optional<Configuration> conf) {
        final StringBuilder sb = new StringBuilder();
        sb.append("# generated file; view: dot -Tpng <filename> > filename.png\n"
            + "digraph Automaton {\n");

        final Map<String, String> actionDescriptions = conf.isPresent()
                ? conf.get().extendedActionDescriptions() : new HashMap<>();

        final String initNodes = String.join(", ",
                initialStates().stream().map(s -> "init" + s).collect(Collectors.toList()));
        
        sb.append("    " + initNodes + " [shape=point, width=0.01, height=0.01, label=\"\", color=white];\n");
        sb.append("    node [shape=box, style=rounded];\n");
        for (int i = 0; i < states.size(); i++) {
            final MooreNode state = states.get(i);
            String color = "";
            for (String action : state.actions().getActions()) {
                final String col = colorRules.get(action);
                if (col != null) {
                    color = " style=filled fillcolor=\"" + col + "\"";
                }
            }
            
            sb.append("    " + state.number() + " [label=\""
                    + state.toString(actionDescriptions) + "\"" + color + "]" + ";\n");
            if (isInitial.get(i)) {
                sb.append("    init" + state.number() + " -> " + state.number() + ";\n");
            }
        }
        
        for (MooreNode state : states) {
            for (MooreTransition t : state.transitions()) {
                sb.append("    " + t.src().number() + " -> " + t.dst().number()
                        + " [label=\" " + t.event() + " \"];\n");
            }
        }

        sb.append("}");
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return toString(Collections.emptyMap(), Optional.empty());
    }

    private static void nusmvEventDescriptions(int[] arr, int index, StringBuilder result,
                                               List<Pair<String, Parameter>> thresholds, List<String> events) {
        if (index == arr.length) {
            final String event = "input_A" + Arrays.toString(arr).replaceAll("[,\\[\\] ]", "");
            if (!events.contains(event)) {
                return;
            }
            final List<String> conditions = new ArrayList<>();
            for (int i = 0; i < arr.length; i++) {
                final String paramName = thresholds.get(i).getLeft();
                final Parameter param = thresholds.get(i).getRight();
                conditions.add(param.nusmvCondition("CONT_INPUT_" + paramName, arr[i]));
            }
            if (conditions.isEmpty()) {
                conditions.add("TRUE");
            }
            result.append("    " + event + " := " + String.join(" & ", conditions) + ";\n");
        } else {
            final int intervalNum = thresholds.get(index).getRight().valueCount();
            for (int i = 0; i < intervalNum; i++) {
                arr[index] = i;
                nusmvEventDescriptions(arr, index + 1, result, thresholds, events);
            }
        }
    }

    private static void spinEventDescriptions(int[] arr, int index, StringBuilder result,
                                              List<Pair<String, Parameter>> thresholds, List<String> events) {
        if (index == arr.length) {
            final String event = "input_A" + Arrays.toString(arr).replaceAll("[,\\[\\] ]", "");
            if (!events.contains(event)) {
                return;
            }
            final List<String> conditions = new ArrayList<>();
            for (int i = 0; i < arr.length; i++) {
                final String paramName = thresholds.get(i).getLeft();
                final Parameter param = thresholds.get(i).getRight();
                conditions.add(param.spinCondition("PLANT_INPUT_" + paramName, arr[i]));
            }
            if (conditions.isEmpty()) {
                conditions.add("true");
            }
            result.append("        " + event + " = " + String.join(" && ", conditions) + ";\n");
        } else {
            final int intervalNum = thresholds.get(index).getRight().valueCount();
            for (int i = 0; i < intervalNum; i++) {
                arr[index] = i;
                spinEventDescriptions(arr, index + 1, result, thresholds, events);
            }
        }
    }

    private static void spinEventDeclarations(int[] arr, int index, StringBuilder result,
                                              List<Pair<String, Parameter>> thresholds, List<String> events) {
        if (index == arr.length) {
            final String event = "input_A" + Arrays.toString(arr).replaceAll("[,\\[\\] ]", "");
            if (!events.contains(event)) {
                return;
            }
            result.append("bool " + event + ";\n");
        } else {
            final int intervalNum = thresholds.get(index).getRight().valueCount();
            for (int i = 0; i < intervalNum; i++) {
                arr[index] = i;
                spinEventDeclarations(arr, index + 1, result, thresholds, events);
            }
        }
    }

    private int maxLoopExecutions() {
        return loopConstraints.stream().mapToInt(x -> x).max().getAsInt();
    }

    public String toNuSMVString(List<String> events, List<String> actions, Optional<Configuration> conf) {
        final List<String> unmodifiedEvents = events;
        events = events.stream().map(s -> "input_" + s).collect(Collectors.toList());
        final List<Pair<String, Parameter>> eventThresholds = conf.isPresent()
                ? conf.get().eventThresholds() : new ArrayList<>();
        final List<Pair<String, Parameter>> actionThresholds = conf.isPresent()
                ? conf.get().actionThresholds() : new ArrayList<>();
        final Map<String, String> actionDescriptions = conf.isPresent()
                ? conf.get().extendedActionDescriptions() : new HashMap<>();
        final String inputLine = String.join(", ", eventThresholds.stream()
                .map(t -> "CONT_INPUT_" + t.getKey()).collect(Collectors.toList()));
        final StringBuilder sb = new StringBuilder();

        if (false) {
            sb.append("MODULE main()\n");
            sb.append("VAR\n");
            sb.append("    plant: PLANT(" + inputLine + ");\n");
            for (Pair<String, Parameter> entry : eventThresholds) {
                final String paramName = entry.getLeft();
                final Parameter param = entry.getRight();
                sb.append("    CONT_INPUT_" + paramName + ": " + param.nusmvType()
                        + ";\n");
            }
            sb.append("\n");
        }

        sb.append("MODULE PLANT(" + inputLine + ")\n");
        sb.append("VAR\n");
        sb.append("    unsupported: boolean;\n");
        sb.append("    loop_executed: boolean;\n");
        sb.append("    state: 0.." + (stateCount() - 1) + ";\n");
        if (loopConstraints != null) {
            sb.append("    loop_executions: 0.." + maxLoopExecutions() + ";\n");
            sb.append("    loop_executions_violated: boolean;\n");
        }
        sb.append("INIT\n");
        sb.append("    state in " + TraceModelGenerator.expressWithIntervalsNuSMV(initialStates()) + "\n");
        sb.append("TRANS\n");
        
        final List<String> stateConstraints = new ArrayList<>();
        for (int i = 0; i < stateCount(); i++) {
            final List<String> options = new ArrayList<>();
            final Map<List<Integer>, Set<String>> map = new LinkedHashMap<>();
            for (String event : events) {
                final List<Integer> destinations = states.get(i).transitions().stream()
                        .filter(t -> ("input_" + t.event()).equals(event))
                        .map(t -> t.dst().number()).collect(Collectors.toList());
                Collections.sort(destinations);
                Set<String> correspondingEvents = map.get(destinations);
                if (correspondingEvents == null) {
                    correspondingEvents = new TreeSet<>();
                    map.put(destinations, correspondingEvents);
                }
                correspondingEvents.add(event);
            }
            final Set<Integer> allSuccStates = states.get(i).transitions().stream()
                    .map(t -> t.dst().number()).collect(Collectors.toCollection(TreeSet::new));
            {
                // if the input is unknown, then the choice for the next state is wide
                final List<Integer> allSuccStatesList = new ArrayList<>(allSuccStates);
                Set<String> correspondingEvents = map.get(allSuccStatesList);
                if (correspondingEvents == null) {
                    correspondingEvents = new TreeSet<>();
                    map.put(allSuccStatesList, correspondingEvents);
                }
                correspondingEvents.add("!known_input");
            }
            for (Map.Entry<List<Integer>, Set<String>> entry : map.entrySet()) {
                final List<Integer> destinations = entry.getKey();
                final Set<String> correspondingEvents = entry.getValue();
                
                options.add("(" + String.join(" | ", correspondingEvents) + ") & next(state) in "
                        + TraceModelGenerator.expressWithIntervalsNuSMV(destinations));
            }

            stateConstraints.add("state = " + i + " -> (\n      " + String.join("\n    | ", options));
        }
        sb.append("    (" + String.join("\n    )) & (", stateConstraints) + "))\n");
        
        // marking that there was a transition unsupported by traces
        sb.append("ASSIGN\n");
        sb.append("    init(unsupported) := FALSE;\n");
        sb.append("    next(unsupported) := unsupported | next(current_unsupported);\n");
        sb.append("    init(loop_executed) := FALSE;\n");
        sb.append("    next(loop_executed) := state = next(state);\n");
        if (loopConstraints != null) {
            sb.append("    init(loop_executions) := 0;\n");
            sb.append("    next(loop_executions) := !next(loop_executed) ? 0 : loop_executions >= " + maxLoopExecutions() + " ? "
                    + maxLoopExecutions() + " : (loop_executions + 1);\n");
            sb.append("    init(loop_executions_violated) := FALSE;\n");
            final List<String> options = new ArrayList<>();
            options.add("loop_executions_violated");
            for (int i = 0; i < stateCount(); i++) {
                options.add("next(state) = " + i + " & next(loop_executions) = " + loopConstraints.get(i));
            }
            sb.append("    next(loop_executions_violated) := " + String.join("\n        | ", options) + ";\n");
        }
        sb.append("DEFINE\n");
        sb.append("    known_input := " + String.join(" | ", events) + ";\n");
        final List<String> unsupported = new ArrayList<>();
        unsupported.add("!known_input");
        for (String e : unmodifiedEvents) {
            final Set<Integer> sourceStates = new TreeSet<>();
            for (int i = 0; i < stateCount(); i++) {
                for (MooreTransition t : states.get(i).transitions()) {
                    if (t.event().equals(e) && unsupportedTransitions.contains(t)) {
                        sourceStates.add(i);
                        break;
                    }
                }
            }
            if (!sourceStates.isEmpty()) {
                unsupported.add("input_" + e + " & state in "
                        + TraceModelGenerator.expressWithIntervalsNuSMV(sourceStates));
            }
        }

        sb.append("    current_unsupported := " + String.join("\n        | ", unsupported) + ";\n");
        for (String action : actions) {
            final List<Integer> properStates = new ArrayList<>();
            for (int i = 0; i < stateCount(); i++) {
                if (ArrayUtils.contains(states.get(i).actions().getActions(), action)) {
                    properStates.add(i);
                }
            }
            final String condition = properStates.isEmpty()
                    ? "FALSE"
                    : ("state in " + TraceModelGenerator.expressWithIntervalsNuSMV(properStates));
            final String comment = actionDescriptions.containsKey(action)
                    ? (" -- " + actionDescriptions.get(action)) : "";
            sb.append("    output_" + action + " := " + condition + ";" + comment + "\n");
        }

        // output conversion to continuous values
        for (Pair<String, Parameter> entry : actionThresholds) {
            final String paramName = entry.getKey();
            final Parameter param = entry.getValue();
            sb.append("    CONT_" + paramName + " := case\n");
            for (int i = 0; i < param.valueCount(); i++) {
                sb.append("        output_" + paramName + i + ": "
                        + param.nusmvInterval(i) + ";\n");
            }
            sb.append("    esac;\n");
        }
        // input conversion to discrete values
        nusmvEventDescriptions(new int[eventThresholds.size()], 0, sb, eventThresholds, events);

        return sb.toString();
    }

    private String indent(int spaces, String s) {
        final String indent = StringUtils.repeat(' ', spaces);
        return String.join("\n", Arrays.stream(s.split("\n")).map(x -> indent + x).collect(Collectors.toList()));
    }

    public String toSPINString(List<String> events, List<String> actions, Optional<Configuration> conf) {
        if (!conf.isPresent()) {
            throw new AssertionError();
        }
        final List<String> unmodifiedEvents = events;
        final StringBuilder sb = new StringBuilder();
        events = events.stream().map(s -> "input_" + s).collect(Collectors.toList());
        final List<Pair<String, Parameter>> eventThresholds = conf.isPresent()
                ? conf.get().eventThresholds() : new ArrayList<>();
        final List<Pair<String, Parameter>> actionThresholds = conf.isPresent()
                ? conf.get().actionThresholds() : new ArrayList<>();

        for (Pair<String, Parameter> p : eventThresholds) {
            sb.append(p.getRight().spinType() + " PLANT_INPUT_" + p.getLeft() + ";\n");
        }
        for (Parameter p : conf.get().outputParameters) {
            sb.append(p.spinType() + " PLANT_OUTPUT_" + p.traceName() + ";\n");
            sb.append(p.spinType() + " CONT_PLANT_OUTPUT_" + p.traceName() + ";\n");
        }

        sb.append("\n");

        sb.append("#define INCLUDE_FAIRNESS\n");
        sb.append("#define INCLUDE_UNSUPPORTED\n");
        sb.append("\n");
        sb.append("#define LTL(x, y) ltl x { X(y) }\n");
        sb.append("\n");
        sb.append("#ifdef INCLUDE_FAIRNESS\n");
        sb.append("#define LTL_FAIRNESS(x, y) ltl x { X((<> [] loop_executed) || (y)) }\n");
        sb.append("#else\n");
        sb.append("#define LTL_FAIRNESS(x, y) ltl x { 0 }\n");
        sb.append("#endif\n");
        sb.append("\n");
        sb.append("#ifdef INCLUDE_UNSUPPORTED\n");
        sb.append("#define LTL_UNSUPPORTED(x, y) ltl x { X((<> current_unsupported) || (y)) }\n");
        sb.append("#else\n");
        sb.append("#define LTL_UNSUPPORTED(x, y) ltl x { 0 }\n");
        sb.append("#endif\n");
        sb.append("\n");
        sb.append("#if defined(INCLUDE_FAIRNESS) && defined(INCLUDE_UNSUPPORTED)\n");
        sb.append("#define LTL_FAIRNESS_UNSUPPORTED(x, y) ltl x { X((<> [] loop_executed) || (<> current_unsupported) || (y)) }\n");
        sb.append("#else\n");
        sb.append("#define LTL_FAIRNESS_UNSUPPORTED(x, y) ltl x { 0 }\n");
        sb.append("#endif\n");
        sb.append("\n");
        sb.append("bool loop_executed;\n");
        sb.append("bool current_unsupported;\n");
        sb.append("\n");
        sb.append("int state = -1;\n");
        spinEventDeclarations(new int[eventThresholds.size()], 0, sb, eventThresholds, events);
        sb.append("bool known_input;\n");
        sb.append("\n");
        sb.append("init { do :: atomic {\n");
        sb.append("\n");
        sb.append("    d_step {\n");
        spinEventDescriptions(new int[eventThresholds.size()], 0, sb, eventThresholds, events);
        sb.append("        known_input = " + String.join(" || ", events) + ";\n");
        sb.append("\n");
        sb.append("        #ifdef INCLUDE_UNSUPPORTED\n");
        final List<String> unsupported = new ArrayList<>();
        unsupported.add("!known_input");
        for (String e : unmodifiedEvents) {
            final Set<Integer> sourceStates = new TreeSet<>();
            for (int i = 0; i < stateCount(); i++) {
                for (MooreTransition t : states.get(i).transitions()) {
                    if (t.event().equals(e) && unsupportedTransitions.contains(t)) {
                        sourceStates.add(i);
                        break;
                    }
                }
            }
            if (!sourceStates.isEmpty()) {
                unsupported.add("input_" + e + " && ("
                        + TraceModelGenerator.expressWithIntervalsSPIN(sourceStates, "state") + ")");
            }
        }
        sb.append("        current_unsupported = " +  String.join(" ||\n            ", unsupported) + ";\n");
        sb.append("        #endif\n");
        sb.append("    }\n");
        sb.append("\n");
        sb.append("    #ifdef INCLUDE_FAIRNESS\n");
        sb.append("    int last_state = state;\n");
        sb.append("    #endif\n");
        sb.append("\n");
        sb.append("    if\n");

        // creation of the structure:
        // destination -> source -> inputs which lead to this destination from this source
        final List<Map<Integer, Set<String>>> mapList = new ArrayList<>();
        for (int i = 0; i < stateCount(); i++) {
            final Map<Integer, Set<String>> map = new TreeMap<>();
            for (int j = -1; j < stateCount(); j++) {
                map.put(j, new TreeSet<>());
            }
            mapList.add(map);
        }
        for (int source = 0; source < stateCount(); source++) {
            for (String event : events) {
                final List<Integer> destinations = states.get(source).transitions().stream()
                        .filter(t -> ("input_" + t.event()).equals(event))
                        .map(t -> t.dst().number()).collect(Collectors.toList());
                for (int dest : destinations) {
                    mapList.get(dest).get(source).add(event);
                }
                final Set<Integer> allSuccStates = states.get(source).transitions().stream()
                        .map(t -> t.dst().number()).collect(Collectors.toCollection(TreeSet::new));
                // if the input is unknown, then the choice for the next state is wide
                for (int dest : allSuccStates) {
                    mapList.get(dest).get(source).add("!known_input");
                }
            }
        }
        // transitions to initial states from the pseudo-initial state
        for (int dest : initialStates()) {
            mapList.get(dest).get(-1).add("");
        }

        for (int i = 0; i < stateCount(); i++) {
            final Map<Integer, Set<String>> sources = mapList.get(i);
            final List<String> sourceOptions = new ArrayList<>();
            for (int j = -1; j < stateCount(); j++) {
                final Set<String> inputs = sources.get(j);
                if (inputs.isEmpty()) {
                    continue;
                }
                sourceOptions.add("(state == " + j + (j == -1 ? ""
                        : (" && (" + String.join(" || ", inputs) + ")")) + ")");
            }
            if (sourceOptions.isEmpty()) {
                continue;
            }

            final List<String> properActions = new ArrayList<>();
            for (String action : actions) {
                if (ArrayUtils.contains(states.get(i).actions().getActions(), action)) {
                    properActions.add("PLANT_OUTPUT_" + action.substring(0, action.length() - 1) + " = "
                            + action.charAt(action.length() - 1));
                }
            }

            sb.append("    :: " + String.join(" || ", sourceOptions) + " -> d_step { state = " + i + "; "
                    + String.join("; ", properActions) + "; }\n");
        }
        sb.append("    fi\n");
        sb.append("\n");

        final StringBuilder dstepSb = new StringBuilder();
        final StringBuilder usualSb = new StringBuilder();

        // output conversion to continuous values
        for (Pair<String, Parameter> entry : actionThresholds) {
            final String paramName = entry.getKey();
            final Parameter param = entry.getValue();
            final StringBuilder effectiveSb = param instanceof IgnoredBoolParameter ? usualSb : dstepSb;
            effectiveSb.append("    if\n");
            for (int i = 0; i < param.valueCount(); i++) {
                final String condition = "    :: PLANT_OUTPUT_" + paramName + " == " + i + " -> ";
                if (param instanceof BoolParameter) {
                    effectiveSb.append(condition + "CONT_PLANT_OUTPUT_" + paramName + " = " + i + ";\n");
                } else if (param instanceof IgnoredBoolParameter) {
                    effectiveSb.append("    :: CONT_PLANT_OUTPUT_" + paramName + " = " + 0 + ";\n");
                    effectiveSb.append("    :: CONT_PLANT_OUTPUT_" + paramName + " = " + 1 + ";\n");
                } else if (param instanceof SetParameter) {
                    effectiveSb.append(condition + "CONT_PLANT_OUTPUT_" + paramName + " = "
                            + ((SetParameter) param).roundedValue(i) + ";\n");
                } else if (param instanceof RealParameter) {
                    final String[] tokens = param.spinInterval(i).split("\\.\\.");
                    final int min = Integer.parseInt(tokens[0]);
                    final int max = Integer.parseInt(tokens[1]);
                    final int mid = (max + min) / 2;
                    // by default, only one value is allowed
                    //effectiveSb.append(condition.replace("::", "// ::") + "CONT_PLANT_OUTPUT_" + paramName + " = "
                    //        + min + ";\n");
                    effectiveSb.append(condition + "CONT_PLANT_OUTPUT_" + paramName + " = " + mid + ";\n");
                    //effectiveSb.append(condition.replace("::", "// ::") + "CONT_PLANT_OUTPUT_" + paramName + " = "
                    //        + max + ";\n");
                }
            }
            effectiveSb.append("    fi\n");
            effectiveSb.append("\n");
        }

        dstepSb.append("    #ifdef INCLUDE_FAIRNESS\n");
        dstepSb.append("    loop_executed = state == last_state;\n");
        dstepSb.append("    #endif\n");
        dstepSb.append("\n");

        sb.append("    d_step {\n");
        sb.append(indent(4, dstepSb.toString()));
        sb.append("\n    }\n\n");
        sb.append(usualSb);
        sb.append("} od }\n");

        return sb.toString();
    }

    public boolean compliesWith(List<StringScenario> scenarios, boolean positive, boolean markUnsupportedTransitions) {
        final Set<MooreTransition> supported = new HashSet<>();

        for (StringScenario sc : scenarios) {
            boolean[] curStates = new boolean[states.size()];
            final StringActions firstActions = sc.getActions(0);
            for (int i = 0; i < states.size(); i++) {
                if (isInitialState(i) && states.get(i).actions().setEquals(firstActions)) {
                    curStates[i] = true;
                }
            }
            for (int i = 1; i < sc.size(); i++) {
                final String event = sc.getEvents(i).get(0);
                final StringActions actions = sc.getActions(i);
                final boolean[] newStates = new boolean[states.size()];
                for (int j = 0; j < states.size(); j++) {
                    if (curStates[j]) {
                        for (MooreTransition t : states.get(j).transitions()) {
                            if (t.event().equals(event) && t.dst().actions().setEquals(actions)) {
                                newStates[t.dst().number()] = true;
                                supported.add(t);
                            }
                        }
                    }
                }
                curStates = newStates;
            }
            final boolean passed = ArrayUtils.contains(curStates, true);
            if (passed != positive) {
                return false;
            }
        }

        if (markUnsupportedTransitions) {
            for (MooreNode state : states) {
                for (MooreTransition t : state.transitions()) {
                    if (!supported.contains(t)) {
                        unsupportedTransitions.add(t);
                    }
                }
            }
        }

        return true;
    }

    public boolean isDeterministic() {
        if (initialStates().size() > 1) {
            return false;
        }
        final Set<String> allEvents = new TreeSet<>();
        states.forEach(s -> s.transitions().stream().map(MooreTransition::event).forEach(allEvents::add));
        for (MooreNode s : states) {
            for (String event : allEvents) {
                if (s.allDst(event).size() > 1) {
                    return false;
                }
            }
        }
        return true;
    }

    /*
     * Requires determinism.
     */
    public double strongCompliance(StringScenario scenario) {
        MooreNode node = states.get(initialStates().get(0));
        for (int pos = 0; pos < scenario.size(); pos++) {
            final StringActions actions = scenario.getActions(pos);
            if (pos == 0) {
                if (!node.actions().setEquals(actions)) {
                    return 0;
                }
            } else {
                final String e = scenario.getEvents(pos).get(0);
                boolean ok = false;
                for (MooreTransition t : node.transitions()) {
                    if (t.event().equals(e) && t.dst().actions().setEquals(actions)) {
                        node = t.dst();
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    return 0;
                }
            }
        }
        return 1;
    }

    /*
     * Requires determinism.
     */
    public double mediumCompliance(StringScenario scenario) {
        MooreNode node = states.get(initialStates().get(0));
        for (int pos = 0; pos < scenario.size(); pos++) {
            final StringActions actions = scenario.getActions(pos);
            if (pos == 0) {
                if (!node.actions().setEquals(actions)) {
                    return 0;
                }
            } else {
                final String e = scenario.getEvents(pos).get(0);
                boolean ok = false;
                for (MooreTransition t : node.transitions()) {
                    if (t.event().equals(e) && t.dst().actions().setEquals(actions)) {
                        node = t.dst();
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    return (double) pos / scenario.size();
                }
            }
        }
        return 1;
    }

    /*
     * Requires determinism.
     */
    public double weakCompliance(StringScenario scenario) {
        MooreNode node = states.get(initialStates().get(0));
        int matched = 0;
        for (int pos = 0; pos < scenario.size(); pos++) {
            final StringActions actions = scenario.getActions(pos);
            if (pos > 0) {
                final String e = scenario.getEvents(pos).get(0);
                for (MooreTransition t : node.transitions()) {
                    if (t.event().equals(e)) {
                        node = t.dst();
                        break;
                    }
                }
            }
            matched += node.actions().setEquals(actions) ? 1 : 0;
        }
        return (double) matched / scenario.size();
    }

    public double strongCompliance(List<StringScenario> scenarios) {
        if (!isDeterministic()) {
            throw new RuntimeException("The automaton must be deterministic.");
        }
        return scenarios.stream().mapToDouble(s -> strongCompliance(s)).average().getAsDouble();
    }

    public double mediumCompliance(List<StringScenario> scenarios) {
        if (!isDeterministic()) {
            throw new RuntimeException("The automaton must be deterministic.");
        }
        return scenarios.stream().mapToDouble(s -> mediumCompliance(s)).average().getAsDouble();
    }

    public double weakCompliance(List<StringScenario> scenarios) {
        if (!isDeterministic()) {
            throw new RuntimeException("The automaton must be deterministic.");
        }
        return scenarios.stream().mapToDouble(s -> weakCompliance(s)).average().getAsDouble();
    }
    
    public NondetMooreAutomaton copy() {
        final List<StringActions> actions = new ArrayList<>();
        for (MooreNode state : states) {
            actions.add(state.actions());
        }

        final NondetMooreAutomaton copy = new NondetMooreAutomaton(states.size(), actions,
                new ArrayList<>(this.isInitial));

        for (MooreNode state : states) {
            final MooreNode src = copy.state(state.number());
            for (MooreTransition t : state.transitions()) {
                final MooreTransition tNew
                        = new MooreTransition(src, copy.state(t.dst().number()), t.event());
                src.addTransition(tNew);
                if (unsupportedTransitions.contains(t)) {
                    copy.unsupportedTransitions.add(tNew);
                }
            }
        }

        copy.loopConstraints = loopConstraints;
        return copy;
    }
    
    public NondetMooreAutomaton swapStates(int[] permutation) {
        final NondetMooreAutomaton copy = copy();
        
        for (int i = 0; i < isInitial.size(); i++) {
            copy.isInitial.set(i, isInitial.get(permutation[i]));
        }
        
        copy.states.clear();
        for (int i = 0; i < states.size(); i++) {
            copy.states.add(new MooreNode(i, states.get(permutation[i]).actions()));
        }
    
        for (MooreNode state : states) {
            final MooreNode src = copy.state(permutation[state.number()]);
            for (MooreTransition t : state.transitions()) {
                final MooreNode dst = copy.state(permutation[t.dst().number()]);                
                final MooreTransition tNew = new MooreTransition(src, dst, t.event());
                src.addTransition(tNew);
                if (unsupportedTransitions.contains(t)) {
                    copy.unsupportedTransitions.add(tNew);
                }
            }
        }
        
        return copy;
    }

    public NondetMooreAutomaton simplify() {
        final NondetMooreAutomaton copy = copy();

        for (MooreNode state : states) {
            final Set<Integer> destinations = state.transitions().stream()
                    .map(MooreTransition::dst).map(MooreNode::number)
                    .collect(Collectors.toCollection(TreeSet::new));
            final MooreNode copyState = copy.state(state.number());
            new HashSet<>(copyState.transitions()).forEach(copyState::removeTransition);
            for (Integer dst : destinations) {
                copyState.addTransition(" ", copy.state(dst));
            }
        }

        return copy;
    }

    public Set<Integer> reachableStates() {
        final Set<Integer> visited = new LinkedHashSet<>();
        final Deque<Integer> queue = new ArrayDeque<>(initialStates());
        while (!queue.isEmpty()) {
            final int state = queue.removeFirst();
            if (visited.contains(state)) {
                continue;
            }
            visited.add(state);

            states.get(state).transitions().stream().map(MooreTransition::dst).map(MooreNode::number)
                    .forEach(queue::addLast);
        }
        return visited;
    }
}
