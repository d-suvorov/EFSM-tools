#!/bin/bash
states=3
events=4
actions=4
inst=0
compl=complete
cd .. && ant qbf-automaton-generator-jar && cd qbf && java -ea -jar ../jars/qbf-automaton-generator.jar testing/$compl/fsm-$states-$inst.sc --ltl testing/$compl/fsm-$states-$inst-true.ltl --size $states --eventNumber $events --actionNumber $actions --timeout 3000 -qs SKIZZO  --solverParams "" --result generated-fsm.gv --complete