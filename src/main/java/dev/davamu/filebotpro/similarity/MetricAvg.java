package dev.davamu.filebotpro.similarity;

import static java.util.Arrays.*;

public class MetricAvg implements SimilarityMetric {

	private final SimilarityMetric[] metrics;

	public MetricAvg(SimilarityMetric... metrics) {
		this.metrics = metrics;
	}

	@Override
	public float getSimilarity(Object o1, Object o2) {
		float f = 0;
		for (SimilarityMetric metric : metrics) {
			f += metric.getSimilarity(o1, o2);
		}
		return f / metrics.length;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + ' ' + asList(metrics);
	}

}
