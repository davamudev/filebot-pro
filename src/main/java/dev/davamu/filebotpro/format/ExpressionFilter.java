package dev.davamu.filebotpro.format;

import static dev.davamu.filebotpro.format.ExpressionFormat.*;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

public class ExpressionFilter {

	private final String expression;
	private final CompiledScript compiledExpression;

	public ExpressionFilter(String expression) throws ScriptException {
		this.expression = expression;
		this.compiledExpression = new ExpressionScript(compileScriptlet(asExpression(expression)));
	}

	public String getExpression() {
		return expression;
	}

	public boolean matches(Object value) {
		return matches(new ExpressionBindings(value));
	}

	public boolean matches(Bindings bindings) {
		// initialize script context with the given bindings
		ScriptContext context = new SimpleScriptContext();
		context.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);

		try {
			// evaluate user script
			Object value = compiledExpression.eval(context);

			// value as boolean
			return DefaultTypeTransformation.castToBoolean(value);
		} catch (Throwable e) {
			// ignore any and all scripting exceptions
		}

		return false;
	}

}
