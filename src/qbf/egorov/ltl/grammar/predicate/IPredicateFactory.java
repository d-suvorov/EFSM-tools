/**
 * IPredicateFactory.java, 10.05.2008
 */
package qbf.egorov.ltl.grammar.predicate;

import qbf.egorov.ltl.grammar.predicate.annotation.Predicate;
import qbf.egorov.statemachine.*;
import qbf.egorov.statemachine.impl.Event;

/**
 * TODO: add comment
 *
 * @author Kirill Egorov
 */
public interface IPredicateFactory<S extends IState> {
    void setAutomataState(S state, IStateTransition transition);

    @Predicate
    Boolean wasEvent(Event e);

    @Predicate
    Boolean isInState(IStateMachine<? extends IState> a, IState s);

    @Predicate
    Boolean wasInState(IStateMachine<? extends IState> a, IState s);

    @Predicate
    boolean cameToFinalState();

    @Predicate
    Boolean wasAction(IAction z);

    @Predicate
    Boolean wasFirstAction(IAction z);

    @Predicate
    boolean wasTrue(ICondition cond);

    @Predicate
    boolean wasFalse(ICondition cond);
}
