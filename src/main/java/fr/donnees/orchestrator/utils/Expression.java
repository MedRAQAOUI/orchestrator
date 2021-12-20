package fr.donnees.orchestrator.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class Expression {

    private String expression;
    private Replacement replacement;
    private Map<String, String> replacements;

    public String getTargetExpression() {
        replace();
        return expression;
    }

    public Expression(String expression) {
        replacements = new HashMap<>();
        this.expression = expression;
    }

    public Replacement replacement() {
        replacement = new Replacement();
        replacement.setExpression(this);
        return replacement;
    }

    private void replace() {
        replacements.entrySet().forEach(entrySet -> {
            expression = expression.replace(entrySet.getKey(), entrySet.getValue());
        });
    }

    public class Replacement {
        private String origin;
        private String target;
        private Expression expression;

        private Replacement() {

        }

        public Replacement from(String origin) {
            this.origin = origin;
            return this;
        }

        public <T> Replacement to(T t, Function<T, String> function) {
            this.target = function.apply(t);
            return this;
        }

        public Expression addReplacement() {
            this.expression.replacements.put(origin, target);
            return this.expression;
        }

        public void setExpression(Expression expression) {
            this.expression = expression;
        }
    }
}
