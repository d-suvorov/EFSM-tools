package continuous_trace_builders;

/**
 * (c) Igor Buzhinsky
 */

import main.plant.PlantBuilderMain;
import meta.Author;
import structures.moore.NondetMooreAutomaton;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

public class TraceEvaluationBuilder {
    final static boolean ALL_EVENT_COMBINATIONS = false;

    public static void run(Configuration conf, String directory, String datasetFilename, boolean satBased,
                           int traceIncludeEach, double traceFraction) throws IOException {
        Dataset ds = Dataset.load(Utils.combinePaths(directory, datasetFilename));
        System.out.println(conf);
        System.out.println();
        final List<String> params = TraceTranslator.generateScenarios(conf, directory, ds, new HashSet<>(),
                "", "", false, satBased, ALL_EVENT_COMBINATIONS, traceIncludeEach, traceFraction);
        ds = null;
        System.out.println();
        final PlantBuilderMain builder = new PlantBuilderMain();
        builder.run(params.toArray(new String[params.size()]), Author.IB, "");
        if (!builder.resultAutomaton().isPresent()) {
            System.err.println("No automaton found.");
            return;
        }
        dumpProperties(builder.resultAutomaton().get());
    }

    static void dumpProperties(NondetMooreAutomaton a) {
        final int nStates = a.states().size();
        final int nTrans = a.transitionNumber();
        final int nTransUnsup = a.unsupportedTransitions().size();
        final int nTransSup = nTrans - nTransUnsup;
        System.out.println("Number of states: " +  nStates);
        System.out.println("Number of supported transitions: " + nTransSup);
        System.out.println("Fraction of supported transitions: " + (float) ((double) nTransSup / nTrans));
    }
}
