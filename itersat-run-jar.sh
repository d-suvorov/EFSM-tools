#!/bin/bash
states=10
events=3
actions=2
ant qbf-automaton-generator-jar && java -jar jars/qbf-automaton-generator.jar qbf/testing/fsm_${states}s${events}e${actions}a_5_80.sc --ltl qbf/testing/fsm_${states}s${events}e${actions}a-3-true.ltl --size $states --eventNumber $events --actionNumber $actions --timeout 600 --complete --result qbf/generated-fsm.gv --strategy ITERATIVE_SAT --bfsConstraints
