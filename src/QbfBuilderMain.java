import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.BooleanOptionHandler;

import qbf.egorov.ltl.LtlParseException;
import qbf.egorov.ltl.LtlParser;
import qbf.egorov.ltl.grammar.LtlNode;
import qbf.reduction.QbfSolver;
import qbf.reduction.SatSolver;
import qbf.reduction.SolvingStrategy;
import qbf.reduction.Verifier;
import scenario.StringScenario;
import structures.Automaton;
import structures.ScenariosTree;
import structures.Transition;
import tools.AutomatonCompletenessChecker;
import algorithms.BacktrackingAutomatonBuilder;
import algorithms.HybridAutomatonBuilder;
import algorithms.IterativeAutomatonBuilder;
import algorithms.QbfAutomatonBuilder;
import bool.MyBooleanExpression;

public class QbfBuilderMain {
	@Argument(usage = "paths to files with scenarios", metaVar = "files", required = true)
	private List<String> arguments = new ArrayList<>();

	@Option(name = "--size", aliases = { "-s" }, usage = "automaton size", metaVar = "<size>", required = true)
	private int size;
	
	@Option(name = "--eventNumber", aliases = { "-en" }, usage = "number of events (A, B, ...)", metaVar = "<eventNumber>", required = true)
	private int eventNumber;
	
	@Option(name = "--actionNumber", aliases = { "-an" }, usage = "number of actions (z0, z1, ...)", metaVar = "<actionNumber>", required = true)
	private int actionNumber;
	
	@Option(name = "--varNumber", aliases = { "-vn" }, usage = "number of variables (x0, x1, ...)", metaVar = "<varNumber>")
	private int varNumber = 0;
	
	@Option(name = "--log", aliases = { "-l" }, usage = "write log to this file", metaVar = "<file>")
	private String logFilePath;

	@Option(name = "--result", aliases = { "-r" }, usage = "write result automaton in GV format to this file",
			metaVar = "<GV file>")
	private String resultFilePath = "automaton.gv";

	@Option(name = "--tree", aliases = { "-t" }, usage = "write scenarios tree in GV format to this file",
			metaVar = "<GV file>")
	private String treeFilePath;

	@Option(name = "--ltl", aliases = { "-lt" }, usage = "file with LTL properties", metaVar = "<file>")
	private String ltlFilePath;
	
	@Option(name = "--extractSubterms", aliases = { "-es" }, handler = BooleanOptionHandler.class,
			usage = "whether subterms should be extracted to separate variables (only for QSAT strategy)",
			metaVar = "<extractSubterms>")
	private boolean extractSubterms;
	
	@Option(name = "--qbfSolver", aliases = { "-qs" }, usage = "QBF solver: SKIZZO or DEPQBF (only for the QSAT strategy)",
			metaVar = "<qbfSolver>")
	private String qbfSolver = QbfSolver.SKIZZO.toString();
	
	@Option(name = "--satSolver", aliases = { "-qss" }, usage = "SAT solver: CRYPTOMINISAT or LINGELING (for ITERATIVE_SAT, EXP_SAT and HYBRID strategies)",
			metaVar = "<satSolver>")
	private String satSolver = SatSolver.LINGELING.toString();
	
	@Option(name = "--solverParams", aliases = { "-sp" }, usage = "additional solver parameters", metaVar = "<solverParams>")
	private String solverParams = "";
	
	@Option(name = "--timeout", aliases = { "-to" }, usage = "solver timeout (sec)", metaVar = "<timeout>")
	private int timeout = 60 * 60 * 24;
	
	@Option(name = "--strategy", aliases = { "-str" }, usage = "solving mode: QSAT, EXP_SAT, ITERATIVE_SAT, BACKTRACKING, HYBRID",
			metaVar = "<strategy>")
	private String strategy = SolvingStrategy.QSAT.toString();
	
	@Option(name = "--complete", aliases = { "-c" }, handler = BooleanOptionHandler.class,
            usage = "generate automaton which has a transition for all (event, expression) pairs")
	private boolean complete;
	
	@Option(name = "--hybridSecToGenerateFormula", aliases = { "-hgf" }, usage = "time limit in seconds for formula generation in the HYBRID mode", metaVar = "<sec>")
	private int hybridSecToGenerateFormula = 15;
	
	@Option(name = "--hybridSecToSolve", aliases = { "-hs" }, usage = "time limit in seconds for the solver in the HYBRID mode", metaVar = "<sec>")
	private int hybridSecToSolve = 30;
	
	private void launcher(String[] args) throws IOException {
		Locale.setDefault(Locale.US);

		CmdLineParser parser = new CmdLineParser(this);
		try {
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			System.out.println("QBF (QSAT) automaton builder from scenarios and LTL formulae");
			System.out.println("Authors: Vladimir Ulyantsev (ulyantsev@rain.ifmo.ru), Igor Buzhinsky (igor.buzhinsky@gmail.com)\n");
			System.out.print("Usage: ");
			parser.printSingleLineUsage(System.out);
			System.out.println();
			parser.printUsage(System.out);
			return;
		}
		
		try {
			Runtime.getRuntime().exec(QbfSolver.valueOf(qbfSolver).command);
		} catch (IOException e) {
			System.err.println("ERROR: Problems with solver execution (" + qbfSolver + ")");
			e.printStackTrace();
			return;
		}

		Logger logger = Logger.getLogger("Logger");
		if (logFilePath != null) {
			try {
				FileHandler fh = new FileHandler(logFilePath, false);
				logger.addHandler(fh);
				SimpleFormatter formatter = new SimpleFormatter();
				fh.setFormatter(formatter);

				logger.setUseParentHandlers(false);
				System.out.println("Log redirected to " + logFilePath);
			} catch (Exception e) {
				System.err.println("Can't work with file " + logFilePath + ": " + e.getMessage());
				return;
			}
		}

		ScenariosTree tree = new ScenariosTree();
		for (String filePath : arguments) {
			try {
				tree.load(filePath, varNumber);
				logger.info("Loaded scenarios from " + filePath);
				logger.info("  Total scenarios tree size: " + tree.nodesCount());
			} catch (IOException | ParseException e) {
				logger.warning("Can't load scenarios from file " + filePath);
				e.printStackTrace();
				return;
			}
		}

		if (treeFilePath != null) {
			try (PrintWriter treePrintWriter = new PrintWriter(new File(treeFilePath))) {
				treePrintWriter.println(tree);
				logger.info("Scenarios tree saved to " + treeFilePath);
			} catch (Exception e) {
				logger.warning("Can't save scenarios tree to " + treeFilePath);
			}
		}
		
		try {
			List<LtlNode> formulae = LtlParser.loadProperties(ltlFilePath, varNumber);
			logger.info("LTL formula from " + ltlFilePath);
			
			long startTime = System.currentTimeMillis();
			logger.info("Start building automaton");
			
			SolvingStrategy ss;
			try {
				ss = SolvingStrategy.valueOf(strategy);
			} catch (IllegalArgumentException e) {
				logger.warning(strategy + " is not a valid solving strategy.");
				return;
			}
			
			QbfSolver qbfsolver;
			try {
				qbfsolver = QbfSolver.valueOf(qbfSolver);
			} catch (IllegalArgumentException e) {
				logger.warning(qbfSolver + " is not a valid QBF solver.");
				return;
			}
			
			SatSolver satsolver;
			try {
				satsolver = SatSolver.valueOf(satSolver);
			} catch (IllegalArgumentException e) {
				logger.warning(satSolver + " is not a valid SAT solver.");
				return;
			}
			
			List<String> events = new ArrayList<>();
			for (int i = 0; i < eventNumber; i++) {
				final String event = String.valueOf((char) ('A' +  i));
				for (int j = 0; j < 1 << varNumber; j++) {
					StringBuilder sb = new StringBuilder(event);
					for (int pos = 0; pos < varNumber; pos++) {
						sb.append(((j >> pos) & 1) == 1 ? 1 : 0);
					}
					events.add(sb.toString());
				}
			}
			
			List<String> actions = new ArrayList<>();
			for (int i = 0; i < actionNumber; i++) {
				actions.add("z" + i);
			}
			
			Optional<Automaton> resultAutomaton = null;
			final Verifier verifier = new Verifier(size, logger, ltlFilePath, events, actions, varNumber);
			final long finishTime = System.currentTimeMillis() + timeout * 1000;
			switch (ss) {
			case QSAT: case EXP_SAT:
				resultAutomaton = QbfAutomatonBuilder.build(logger, tree, formulae, size, ltlFilePath,
						qbfsolver, solverParams, extractSubterms, ss == SolvingStrategy.EXP_SAT,
						events, actions, satsolver, verifier, finishTime, complete);
				break;
			case HYBRID:
				resultAutomaton = HybridAutomatonBuilder.build(logger, tree, formulae, size, ltlFilePath,
						qbfsolver, solverParams, extractSubterms,
						events, actions, satsolver, verifier, finishTime, complete,
						hybridSecToGenerateFormula, hybridSecToSolve);
				break;
			case ITERATIVE_SAT:
				resultAutomaton = IterativeAutomatonBuilder.build(logger, tree, size, solverParams,
						resultFilePath, ltlFilePath, formulae, events, actions, satsolver, verifier, finishTime, complete);
				break;
			case BACKTRACKING:
				resultAutomaton = BacktrackingAutomatonBuilder.build(logger, tree, size,
						resultFilePath, ltlFilePath, formulae, events, actions, verifier, finishTime, complete);
				break;
			}
			final double executionTime = (System.currentTimeMillis() - startTime) / 1000.;
			
			if (!resultAutomaton.isPresent()) {
				logger.info("Automaton with " + size + " states NOT FOUND!");
				logger.info("Automaton builder execution time: " + executionTime);
			} else {
				logger.info("Automaton with " + size + " states WAS FOUND!");
				logger.info("Automaton builder execution time: " + executionTime);
				
				// test compliance
				final List<StringScenario> scenarios = new ArrayList<>();
				for (String scenarioPath : arguments) {
					scenarios.addAll(StringScenario.loadScenarios(scenarioPath, varNumber));
				}
				
				if (scenarios.stream().allMatch(resultAutomaton.get()::isCompliantWithScenario)) {
					logger.info("COMPLIES WITH SCENARIOS");
				} else {
					logger.severe("NOT COMPLIES WITH SCENARIOS");
				}

				// writing to a file
				try (PrintWriter resultPrintWriter = new PrintWriter(new File(resultFilePath))) {
					resultPrintWriter.println(resultAutomaton.get());
				} catch (FileNotFoundException e) {
					logger.warning("File " + resultFilePath + " not found: " + e.getMessage());
				}
				
				// verification
				boolean verified = verifier.verify(resultAutomaton.get());
				if (verified) {
					logger.info("VERIFIED");
				} else {
					logger.severe("NOT VERIFIED");
				}
				
				// bfs check
				if (ss != SolvingStrategy.BACKTRACKING) {
					if (checkBfs(resultAutomaton.get(), events, logger)) {
						logger.info("BFS");
					} else {
						if (ss == SolvingStrategy.ITERATIVE_SAT || ss == SolvingStrategy.HYBRID) {
							logger.info("NOT BFS (possibly due to transition redirections)");
						} else {
							logger.severe("NOT BFS");
						}
					}
				}
				
				// completeness check
				if (complete) {
					String s = AutomatonCompletenessChecker.checkCompleteness(resultAutomaton.get());
					if (s.equals("COMPLETE")) {
						logger.info(s);
					} else {
						logger.severe(s);
					}
				}
			}
		} catch (ParseException | LtlParseException e) {
			logger.warning("Can't get LTL formula from " + treeFilePath);
			throw new RuntimeException(e);
		}
	}

	private boolean checkBfs(Automaton a, List<String> events, Logger logger) {
		final Deque<Integer> queue = new ArrayDeque<>();
		final boolean[] visited = new boolean[a.statesCount()];
		visited[a.getStartState().getNumber()] = true;
		queue.add(a.getStartState().getNumber());
		final List<Integer> dequedStates = new ArrayList<>();
		while (!queue.isEmpty()) {
			final int stateNum = queue.pollFirst();
			dequedStates.add(stateNum);
			for (String e : events) {
				Transition t = a.getState(stateNum).getTransition(e, MyBooleanExpression.getTautology());
				if (t != null) {
					final int dst = t.getDst().getNumber();
					if (!visited[dst]) {
						queue.add(dst);
					}
					visited[dst] = true;
				}
			}
		}
		final List<Integer> sortedList = dequedStates.stream().sorted().collect(Collectors.toList());
		if (sortedList.equals(dequedStates)) {
			return true;
		} else {
			logger.warning(dequedStates + " instead of " + sortedList);
			return false;
		}
	}
	
	public void run(String[] args) {
		try {
			launcher(args);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public static void main(String[] args) {
		new QbfBuilderMain().run(args);
	}
}
