package dev.davamu.filebotpro.format;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.format.ExpressionFormat.*;

import java.util.Collection;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

public class ExpressionMapper {

	private final String expression;
	private final CompiledScript compiledExpression;

	public ExpressionMapper(String expression) throws ScriptException {
		this.expression = expression;
		this.compiledExpression = new ExpressionScript(compileScriptlet(asExpression(expression)));
	}

	public String getExpression() {
		return expression;
	}

	public <T> T map(Object value, Class<T> type) throws ScriptException {
		return map(new ExpressionBindings(value), type);
	}

	public <T> T map(Bindings bindings, Class<T> type) throws ScriptException {
		// initialize script context with the given bindings
		ScriptContext context = new SimpleScriptContext();
		context.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);

		// evaluate user script
		Object value = unwrap(compiledExpression.eval(context));

		// value as target type
		return (T) DefaultTypeTransformation.castToType(value, type);
	}

	/**
	 * Binding objects (e.g. {db.AniDB} or {db.TheTVDB}) are mapped to the episode they represent
	 */
	private Object unwrap(Object value) {
		if (value instanceof AssociativeScriptObject) {
			return ((AssociativeScriptObject) value).getProperty("episode");
		}
		if (value instanceof Collection) {
			return ((Collection<?>) value).stream().map(this::unwrap).collect(toList());
		}
		if (value instanceof Object[]) {
			return stream((Object[]) value).map(this::unwrap).collect(toList());
		}
		return value;
	}

}
