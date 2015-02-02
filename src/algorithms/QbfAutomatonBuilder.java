package algorithms;

/**
 * (c) Igor Buzhinsky
 */

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import algorithms.FormulaBuilder.EventExpressionPair;
import qbf.egorov.ltl.grammar.LtlNode;
import qbf.reduction.QuantifiedBooleanFormula;
import qbf.reduction.SolverResult;
import qbf.reduction.SolverResult.SolverResults;
import qbf.reduction.Solvers;
import structures.Automaton;
import structures.ScenariosTree;

public class QbfAutomatonBuilder extends ScenarioAndLtlAutomatonBuilder {
	public static Optional<Automaton> build(Logger logger, ScenariosTree tree,
			List<LtlNode> formulae, int colorSize, int depth,
			int timeoutSeconds, Solvers solver, String solverParams, boolean extractSubterms,
			boolean complete, boolean useSat, boolean bfsConstraints, boolean useCoprocessor,
			List<EventExpressionPair> efPairs, List<String> actions) throws IOException {
		deleteTrash();
		
		QuantifiedBooleanFormula qbf = new QbfFormulaBuilder(logger, tree,
			formulae, colorSize, depth, extractSubterms, complete, bfsConstraints, efPairs, actions).getFormula(useSat);
		
		SolverResult ass = useSat
				? qbf.solveAsSat(tree, colorSize, depth, logger, solverParams, timeoutSeconds, efPairs)
				: qbf.solve(logger, solver, solverParams, timeoutSeconds, useCoprocessor);

		logger.info(ass.toString().split("\n")[0]);

		return ass.type() != SolverResults.SAT
				? Optional.empty() : constructAutomatonFromAssignment(logger, ass, tree, colorSize);
	}
}
