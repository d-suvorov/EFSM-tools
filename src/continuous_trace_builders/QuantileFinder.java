package continuous_trace_builders;

/**
 * (c) Igor Buzhinsky
 */

import continuous_trace_builders.parameters.Parameter;
import continuous_trace_builders.parameters.RealParameter;
import continuous_trace_builders.parameters.SegmentsParameter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class QuantileFinder {
    public static void run(Configuration conf, String directory, String datasetFilename) throws IOException {
        final Dataset ds = Dataset.load(Utils.combinePaths(directory, datasetFilename));
        for (Parameter p : conf.parameters()) {
            if (p instanceof RealParameter || p instanceof SegmentsParameter) {
                final List<Double> values = new ArrayList<>();
                for (int i = 0; i < ds.values.size(); i++) {
                    final List<double[]> trace = ds.values.get(i);
                    values.addAll(trace.stream().map(aTrace -> ds.get(aTrace, p)).collect(Collectors.toList()));
                }
                Collections.sort(values);
                System.out.println(p);
                for (int i = 2; i <= 10; i++) {
                    System.out.print(i + "-quantiles:");
                    for (int j = 1; j < i; j++) {
                        final int index = values.size() / i * j;
                        System.out.print(" " + Math.round(values.get(index)));
                    }
                    System.out.println();
                }
                System.out.println();
            }
        }
    }
}
