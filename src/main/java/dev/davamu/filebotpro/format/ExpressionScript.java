package dev.davamu.filebotpro.format;

import java.util.concurrent.Callable;

import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * Compiled format expression snippet. Closures and other {@link Callable} results are evaluated as well, so that {@code {{n}}} yields the same value as {@code {n}}.
 */
public class ExpressionScript extends CompiledScript {

	private final CompiledScript compiledScript;

	public ExpressionScript(CompiledScript compiledScript) {
		this.compiledScript = compiledScript;
	}

	@Override
	public Object eval(ScriptContext context) throws ScriptException {
		Object value = compiledScript.eval(context);

		if (value instanceof Callable<?>) {
			try {
				return ((Callable<?>) value).call();
			} catch (ScriptException e) {
				throw e;
			} catch (Exception e) {
				throw new ScriptException(e);
			}
		}

		return value;
	}

	@Override
	public ScriptEngine getEngine() {
		return compiledScript.getEngine();
	}

}
