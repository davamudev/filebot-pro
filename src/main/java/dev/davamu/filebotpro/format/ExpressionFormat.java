package dev.davamu.filebotpro.format;

import static dev.davamu.filebotpro.util.ExceptionUtilities.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.lang.model.SourceVersion;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;

import dev.davamu.filebotpro.util.LegacyNamespace;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.MissingPropertyException;

public class ExpressionFormat extends Format {

	private final String expression;

	private final Object[] compilation;

	private SuppressedThrowables suppressed;

	public ExpressionFormat(String expression) throws ScriptException {
		this.expression = expression;
		this.compilation = wrap(compile(asExpression(expression)));
	}

	public String getExpression() {
		return expression;
	}

	protected Object[] compile(String expression) throws ScriptException {
		List<Object> compilation = new ArrayList<Object>();

		char open = '{';
		char close = '}';

		StringBuilder token = new StringBuilder();
		int level = 0;

		// parse expressions and literals
		for (int i = 0; i < expression.length(); i++) {
			char c = expression.charAt(i);

			if (c == open) {
				if (level == 0) {
					if (token.length() > 0) {
						compilation.add(token.toString());
						token.setLength(0);
					}
				} else {
					token.append(c);
				}

				level++;
			} else if (c == close) {
				if (level == 1) {
					if (token.length() > 0) {
						try {
							compilation.add(compileScriptlet(token.toString()));
						} catch (ScriptException e) {
							// try to extract syntax exception
							ScriptException illegalSyntax = e;

							try {
								String message = findCause(e, MultipleCompilationErrorsException.class).getErrorCollector().getSyntaxError(0).getOriginalMessage();
								illegalSyntax = new ScriptException("SyntaxError: " + message);
							} catch (Exception ignore) {
								// ignore, just use original exception
							}

							throw illegalSyntax;
						} finally {
							token.setLength(0);
						}
					}
				} else {
					token.append(c);
				}

				level--;
			} else {
				token.append(c);
			}

			// sanity check
			if (level < 0) {
				throw new ScriptException("SyntaxError: unexpected token: " + close);
			}
		}

		// sanity check
		if (level != 0) {
			throw new ScriptException("SyntaxError: missing token: " + close);
		}

		// append tail
		if (token.length() > 0) {
			compilation.add(token.toString());
		}

		return compilation.toArray();
	}

	public Bindings getBindings(Object value) {
		return new ExpressionBindings(value);
	}

	@Override
	public StringBuffer format(Object object, StringBuffer sb, FieldPosition pos) {
		return sb.append(format(getBindings(object)));
	}

	public String format(Bindings bindings) {
		// initialize script context with the given bindings
		ScriptContext context = new SimpleScriptContext();
		context.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);

		// reset exception state
		List<Throwable> suppressed = new ArrayList<Throwable>();

		StringBuilder sb = new StringBuilder();
		for (Object snippet : compilation) {
			if (snippet instanceof CompiledScript) {
				try {
					CharSequence value = normalizeExpressionValue(((CompiledScript) snippet).eval(context));
					if (value != null) {
						sb.append(value);
					}
				} catch (ScriptException e) {
					suppressed.add(normalizeExpressionException(e));
				}
			} else {
				sb.append(snippet);
			}
		}

		// require non-empty String value
		String value = normalizeResult(sb);

		if (value.isEmpty()) {
			throw new SuppressedThrowables("Expression yields empty value", suppressed);
		}

		// store for later (not thread-safe)
		this.suppressed = suppressed.isEmpty() ? null : new SuppressedThrowables("Suppressed", suppressed);

		return value;
	}

	public SuppressedThrowables suppressed() {
		return suppressed;
	}

	protected Object normalizeBindingValue(Object value) {
		return value;
	}

	protected CharSequence normalizeExpressionValue(Object value) {
		return value == null ? null : value.toString();
	}

	protected String normalizeResult(CharSequence value) {
		return value.toString();
	}

	protected Throwable normalizeExpressionException(ScriptException exception) {
		if (findCause(exception, MissingPropertyException.class) != null) {
			return new BindingException(findCause(exception, MissingPropertyException.class).getProperty(), "undefined", exception);
		}

		if (findCause(exception, GroovyRuntimeException.class) != null) {
			return new ExpressionException(findCause(exception, GroovyRuntimeException.class).getMessage(), exception);
		}

		// unwrap ScriptException if possible
		if (exception.getCause() instanceof Exception) {
			return exception.getCause();
		}

		return exception;
	}

	@Override
	public Object parseObject(String source, ParsePosition pos) {
		throw new UnsupportedOperationException();
	}

	private Object[] wrap(Object[] compilation) {
		for (int i = 0; i < compilation.length; i++) {
			Object snippet = compilation[i];

			// simple expressions like {n} are evaluated directly
			if (snippet instanceof Variable) {
				continue;
			}

			if (snippet instanceof CompiledScript) {
				compilation[i] = new ExpressionScript((CompiledScript) snippet);
			}
		}

		return compilation;
	}

	protected static Compilable createScriptEngine() {
		GroovyClassLoader classLoader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), new CompilerConfiguration());
		return new GroovyScriptEngineImpl(classLoader);
	}

	private static Compilable engine;
	private static Map<String, CompiledScript> scriptletCache = new HashMap<String, CompiledScript>();

	protected static CompiledScript compileScriptlet(String expression) throws ScriptException {
		// simple expressions like {n} don't need to be interpreted by the script engine
		if (SourceVersion.isIdentifier(expression) && !SourceVersion.isKeyword(expression)) {
			return new Variable(expression);
		}

		synchronized (scriptletCache) {
			CompiledScript scriptlet = scriptletCache.get(expression);
			if (scriptlet == null) {
				// lazy initialize script engine
				if (engine == null) {
					engine = createScriptEngine();
				}

				// compile and cache script
				scriptlet = engine.compile(LegacyNamespace.migrate(expression));
				scriptletCache.put(expression, scriptlet);
			}
			return scriptlet;
		}
	}

	protected static String asExpression(String s) {
		// try as file path
		if (s.startsWith("@") && s.endsWith(".groovy")) {
			File f = new File(s.substring(1));
			if (f.isFile()) {
				try {
					return readTextFile(f);
				} catch (Exception e) {
					throw new IllegalArgumentException("Failed to read text file: " + f, e);
				}
			}
		}

		// or default to literal value
		return s;
	}

	private static class Variable extends CompiledScript {

		private String name;

		public Variable(String name) {
			this.name = name;
		}

		@Override
		public Object eval(ScriptContext context) throws ScriptException {
			try {
				Object value = context.getAttribute(name);
				if (value == null) {
					throw new MissingPropertyException(name, Variable.class);
				}
				return value;
			} catch (Exception e) {
				throw new ScriptException(e);
			} catch (Throwable t) {
				throw new ScriptException(new ExecutionException(t));
			}
		}

		@Override
		public ScriptEngine getEngine() {
			return null;
		}

	}

}
