#!/bin/bash
states=17
java -Xmx4G -jar ../jars/qbf-automaton-generator.jar cvs.sc --ltl cvs.ltl --size $states --eventNames setfiletype,initialise,connect,login,changedir,listfiles,logout,disconnect,makedir,delete,appendfile,retrievefile,listnames,rename,storefile,rmdir --result generated-fsm.gv --strategy COUNTEREXAMPLE --completenessType NO_DEAD_ENDS --satSolver LINGELING
